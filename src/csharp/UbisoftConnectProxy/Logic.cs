#nullable enable

using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Security.Cryptography.X509Certificates;
using System.Threading;
using System.Threading.Tasks;
using UbisoftConnectProxy.JavaInterop;
using UbisoftConnectProxy.JavaInterop.Dtos;

namespace UbisoftConnectProxy
{
    public class Logic
    {
        private readonly Dictionary<int, PendingRequest> _pendingRequests = new();
        private readonly X509Certificate2 _certificate;

        private int _nextRequestId;
        private JavaConnection? _javaConnection;
        private Webserver? _webserver;

        private Task? _disposeTask;

        public Logic()
        {
            Thread.CurrentThread.Name = "Main Logic Thread";

            // Some boilerplate to react to close window event, CTRL-C, kill, etc
            var syncContext = Misc.GetCurrentSynchronizationContext();
            _handler = sig =>
            {
                syncContext.DispatchAsync(async () => await DisposeAsync()).Wait();
                return true;
            };
            SetConsoleCtrlHandler(_handler, true);

            // get the first certificate file
            var folderRootCertificate = new DirectoryInfo("RootCertificate");
            var certFile = folderRootCertificate.EnumerateFiles()
                .OrderBy(x => x.FullName.ToLowerInvariant().EndsWith("p12") ? 0 : 1)
                .FirstOrDefault();
            if (certFile == null)
            {
                WriteFailure("Certificate file got deleted?!");
                Quit("Certificate file got deleted?!");
                _certificate = null!;
                return;
            }
            _certificate = new X509Certificate2(File.ReadAllBytes(certFile.FullName), "password");
        }

        public async Task RunAsync(ushort javaTcpPort)
        {
            // 1. Connect to Java
            WriteCheck("Step 1: Connecting to Java on " + javaTcpPort);
            TaskCompletionSource tcsJavaDnsReady = new();
            TcpClient tcpClient = new();
            tcpClient.NoDelay = true;
            await tcpClient.ConnectAsync(IPAddress.Loopback, javaTcpPort);
            // connection established! (crashes when a second DnsReady is received, as it should)
            _javaConnection = new(tcpClient.GetStream(), OnJavaDisconnected, tcsJavaDnsReady.SetResult, OnResponse);
            WriteSuccess("Connected");

            // 2. Remove from hosts
            WriteCheck("Step 2: Removing redirect from hosts");
            try
            {
                if (SetHostsFile(false))
                {
                    WriteSuccess("Not present");
                }
                else
                {
                    WriteSuccess("Removed");
                }
            }
            catch (Exception e)
            {
                WriteFailure(e.Message);
                Quit("Could not read/write the hosts file. Did you forget to run the program as admin?");
                throw;
            }

            // 3. Wait for Java to have made the first HTTP request so it has the IP cached
            WriteCheck("Step 3: Waiting for Java to be ready");
            _javaConnection.SendReady(Ready.Hosts);
            await tcsJavaDnsReady.Task;
            WriteSuccess("Ready");

            // 4. Once Java says it's ready, change the hosts file
            WriteCheck("Step 4: Redirecting channel-service.upc.ubi.com to 127.0.0.1");
            try
            {
                if (SetHostsFile(true))
                {
                    WriteSuccess("Already present?");
                }
                else
                {
                    WriteSuccess("Done");
                }
            }
            catch (Exception e)
            {
                WriteFailure(e.Message);
                Console.WriteLine("Could not read/write the hosts file. Did you forget to run the program as admin?\n");
                Quit("Could not read/write the hosts file. Did you forget to run the program as admin?");
                throw;
            }

            // 5. Install the root certificate if not present yet
            WriteCheck("Step 5: Installing the Root certificate");
            try
            {
                if (SetCertificate(true))
                {
                    WriteSuccess("Already present");
                }
                else
                {
                    WriteSuccess("Done");
                }
                _javaConnection?.SendReady(Ready.Starting);
            }
            catch (Exception e)
            {
                WriteFailure(e.Message);
                Console.WriteLine("Could not read/write the hosts file. Did you forget to run the program as admin?\n");
                Quit("Could not read/write the hosts file. Did you forget to run the program as admin?");
                throw;
            }

            // 6. Start the webserver
            WriteCheck("Step 6: Starting the webserver");
            try
            {
                _webserver = new(_certificate, this);
                await _webserver.StartAsync();
                WriteSuccess("Started");
            }
            catch (Exception e)
            {
                WriteFailure(e.Message);
            }

            // 7. Ready!
            Console.WriteLine("Ready!");
            _javaConnection?.SendReady(Ready.Running);
        }

        private void OnJavaDisconnected(Exception? e)
        {
            _javaConnection = null;
            Quit("Java disconnected: " + e);
        }

        public void OnError(string text)
        {
            Console.WriteLine(text);
            if (_javaConnection != null)
            {
                SendError(text, false);
            }
        }

        public void OnProxyError(Exception e)
        {
            OnError("Proxy error: " + e);
        }

        private void SendError(string error, bool fatal)
        {
            _javaConnection!.Send(new WebserverErrorDto
                {
                    Fatal = fatal,
                    Text = error
                });
        }

        public static void WriteCheck(string check)
        {
            Console.ForegroundColor = ConsoleColor.White;
            Console.Write(check + "... ");
        }

        public static void WriteSuccess(string text)
        {
            Console.ForegroundColor = ConsoleColor.Green;
            Console.WriteLine(text);
            Console.ForegroundColor = ConsoleColor.White;
        }

        public static void WriteFailure(string text)
        {
            Console.ForegroundColor = ConsoleColor.Red;
            Console.WriteLine(text + "\n");
            Console.ForegroundColor = ConsoleColor.White;
        }

        public Task DisposeAsync()
        {
            if (_disposeTask == null)
            {
                _disposeTask = DoDisposeAsync();
            }
            return _disposeTask;
        }

        private async Task DoDisposeAsync()
        {
            // 1. Start closing the webserver
            ValueTask? closeWebserver = _webserver?.DisposeAsync();

            // 2. Remove from hosts
            try
            {
                Console.WriteLine("Removing entry from hosts...");
                SetHostsFile(false);
            }
            catch (Exception e)
            {
                Console.WriteLine("Failed to remove hosts file entry: " + e);
            }

            // 3. Uninstall the root certificate (needs user UI interaction!)
            Console.WriteLine("\nShutting down...\n");
            try
            {
                Console.WriteLine("Removing root certificate...");
                SetCertificate(false);
            }
            catch (Exception e)
            {
                Console.WriteLine("Failed to uninstall the root certificate: " + e);
            }

            // 4. Wait for the closing to finish
            if (closeWebserver != null)
            {
                await closeWebserver.Value;
            }
        }

        public void Quit(string error)
        {
            Console.WriteLine($"Quitting: {error}");
            if (_javaConnection != null)
            {
                SendError(error, true);
            }
            else
            {
                QuitSoon();
            }
        }

        private async void QuitSoon()
        {
            await DisposeAsync();
            // Environment.Exit blocks while performing cleanup
            await Task.Run(() => Environment.Exit(0));
        }

        private static readonly string _hostsFilePath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System), "drivers/etc/hosts");

        /// <summary>
        /// Returns whether it already was the way you want it.
        /// </summary>
        static bool SetHostsFile(bool redirectActive)
        {
            // find the entry
            const string ip = "127.0.0.1";
            const string hostname = "channel-service.upc.ubi.com";
            const string fullLine = ip + "   " + hostname + "   # ubisoftconnect-win7fix";
            List<string> lines = new(File.ReadAllLines(_hostsFilePath, Misc.UTF8NoBOM));

            if (redirectActive)
            {
                if (lines.Any(x => x.StartsWith(fullLine)))
                {
                    // already present
                    return true;
                }
                // add it
                lines.Add(fullLine);
            }
            else if (lines.RemoveAll(x => x.StartsWith(fullLine)) == 0)
            {
                // was not removed -> was not present
                return true;
            }

            // we need to modify it
            File.WriteAllLines(_hostsFilePath, lines, Misc.UTF8NoBOM);
            // done!
            return false;
        }
        /// <summary>
        /// Returns whether it already was the way you want it.
        /// </summary>
        bool SetCertificate(bool shouldBeInstalled)
        {
            if (_certificate == null)
            {
                // certificate not loaded
                throw new InvalidOperationException("Certificate not loaded yet");
            }

            using X509Store store = new(StoreName.Root, StoreLocation.CurrentUser);
            store.Open(OpenFlags.ReadWrite);
            if (shouldBeInstalled)
            {
                if (store.Certificates.Contains(_certificate))
                {
                    // already installed
                    return true;
                }
                // install
                store.Add(_certificate);
            }
            else
            {
                if (!store.Certificates.Contains(_certificate))
                {
                    // not installed
                    return true;
                }
                // remove
                store.Remove(_certificate);
                return true;

            }
            return false;
        }

        void OnResponse(ResponseDto response)
        {
            if (_pendingRequests.TryGetValue(response.RequestId, out var request))
            {
                request.TaskCompleter.TrySetResult(response);
            }
        }

        public async Task<ResponseDataDto> DoRequestAsync(RequestDataDto requestData)
        {
            RequestDto requestDto = new()
            {
                RequestId = _nextRequestId++,
                Data = requestData,
            };

            if (_javaConnection != null)
            {
                _javaConnection.Send(requestDto);
                PendingRequest pendingRequest = new();
                _pendingRequests.Add(requestDto.RequestId, pendingRequest);
                var response = await pendingRequest.TaskCompleter.Task;
                _pendingRequests.Remove(requestDto.RequestId);
                if (response != null)
                {
                    return response.Data;
                }
            }

            return new()
            {
                Content = Array.Empty<byte>(),
                Headers = Array.Empty<HeaderDto>(),
                StatusCode = 500,
            };
        }

        [DllImport("Kernel32")]
        private static extern bool SetConsoleCtrlHandler(EventHandler handler, bool add);

        private delegate bool EventHandler(CtrlType sig);
        static EventHandler? _handler;

        enum CtrlType
        {
            CTRL_C_EVENT = 0,
            CTRL_BREAK_EVENT = 1,
            CTRL_CLOSE_EVENT = 2,
            CTRL_LOGOFF_EVENT = 5,
            CTRL_SHUTDOWN_EVENT = 6
        }
    }

    class PendingRequest
    {
        public TaskCompletionSource<ResponseDto?> TaskCompleter { get; } = new();

        public PendingRequest()
        {
            CompleteLater();
        }

        async void CompleteLater()
        {
            await Task.Delay(TimeSpan.FromSeconds(30));
            TaskCompleter.TrySetResult(null);
        }
    }
}

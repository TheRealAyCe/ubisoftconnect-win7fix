#nullable enable

using Microsoft.AspNetCore.Hosting;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.AspNetCore.Server.Kestrel.Https;
using Microsoft.Extensions.Hosting;
using System;
using System.Net;
using System.Security.Cryptography.X509Certificates;
using System.Threading;
using System.Threading.Tasks;

namespace UbisoftConnectProxy
{
    public sealed class Webserver : IAsyncDisposable
    {
        private readonly CancellationTokenSource _cts = new();
        private readonly IHost _host;

        private bool _started;

        public Webserver(X509Certificate2 certificate, Logic logic)
        {
            // install cert, pw: password, put in "trusted roots"
            //var store = new X509Store(StoreName.Root, StoreLocation.CurrentUser);
            //store.Open(OpenFlags.ReadOnly);
            //var certificate = store.Certificates.OfType<X509Certificate2>()
            //    .First(c => c.ToString().Contains("channel-service.upc.ubi.com"));

            _host = CreateHostBuilder(certificate, new(logic)).Build();
        }

        public async Task StartAsync()
        {
            await _host.StartAsync(_cts.Token);
            _started = true;
        }

        public async ValueTask DisposeAsync()
        {
            _cts.Cancel();
            _cts.Dispose();
            if (_started)
            {
                try
                {
                    await _host.StopAsync();
                }
                finally
                {
                    _host.Dispose();
                }
            }
        }

        private static IHostBuilder CreateHostBuilder(X509Certificate2 certificate, Synchronizer synchronizer)
        {
            return Host.CreateDefaultBuilder()
                .ConfigureWebHostDefaults(webBuilder =>
                {
                    webBuilder
                        .UseKestrel(options =>
                        {
                            options.Listen(IPAddress.Any, 443, listenOptions =>
                            {
                                HttpsConnectionAdapterOptions connectionOptions = new()
                                {
                                    ServerCertificate = certificate
                                };

                                listenOptions.UseHttps(connectionOptions);
                            });
                        })
                        .ConfigureServices(servicesCollection =>
                        {
                            servicesCollection.AddSingleton(synchronizer);
                        })
                        .UseStartup<Startup>();
                });
        }
    }

    public class Synchronizer
    {
        public SynchronizationContext SynchronizationContext { get; } = Misc.GetCurrentSynchronizationContext();
        public Logic Logic { get; }

        public Synchronizer(Logic logic)
        {
            Logic = logic;
        }
    }
}

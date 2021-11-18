#nullable enable

using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading;

namespace UbisoftConnectProxy.JavaInterop
{
    /// <summary>
    /// Sends data into a stream by calling WriteAsync.
    /// </summary>
    public sealed class Sender : Disposable
    {
        readonly List<byte[]> sendBuffer = new();
        readonly Stream stream;
        readonly Action<Exception?> onError;
        readonly PulsableWait waiter = new();
        readonly CancellationTokenSource cancellationTokenSource = new();

        /// <summary>
        /// An exception is actually expected. It's your job to verify if that exception is even relevant (highly expected in case of network disconnect for example). It will also be called (with null) once the Sender exits "normally".
        /// </summary>
        public Sender(Stream stream, Action<Exception?> onError)
        {
            Misc.ThrowIfHasNoSynchronizationContext();

            this.stream = stream;
            this.onError = onError;
            DoWork();
        }

        /// <summary>
        /// Add some data to the queue. Order is guaranteed.
        /// </summary>
        public void Send(byte[] data)
        {
            ThrowIfDisposed();

            if (data.Length == 0)
            {
                return;
            }

            sendBuffer.Add(data);
            waiter.Pulse();
        }

        async void DoWork()
        {
            // ensure no sync evaluation
            await Misc.YieldLoop();

            // this is all synchronized via the starting synchronization context of course

            try
            {
                while (true)
                {
                    while (!Disposed && sendBuffer.Count == 0)
                    {
                        await waiter.WaitAsync();
                    }

                    if (Disposed)
                    {
                        break;
                    }

                    byte[] sendBlob;
                    if (sendBuffer.Count == 1)
                    {
                        sendBlob = sendBuffer[0];
                    }
                    else
                    {
                        // compact it into one byte array. but not horribly inefficient LINQ style (it probably really does not matter! :)
                        //sendBlob = sendBuffer.SelectMany(x => x).ToArray();
                        sendBlob = new byte[sendBuffer.Select(x => x.Length).Sum()];
                        int offset = 0;
                        foreach (var block in sendBuffer)
                        {
                            int length = block.Length;
                            Buffer.BlockCopy(block, 0, sendBlob, offset, length);
                            offset += length;
                        }
                    }
                    sendBuffer.Clear();

                    await stream.WriteAsync(sendBlob.AsMemory(0, sendBlob.Length), cancellationTokenSource.Token);
                    // Flush does not do anything for TCP streams, but PipeStreams may need it.
                    await stream.FlushAsync();
                }
            }
            catch (Exception e)
            {
                Dispose();
                onError(e);
                return;
            }

            onError(null);
        }

        /// <inheritdoc />
        protected override void DisposeOnce()
        {
            sendBuffer.Clear();
            waiter.Dispose();
            cancellationTokenSource.Cancel();
            cancellationTokenSource.Dispose();
        }
    }
}

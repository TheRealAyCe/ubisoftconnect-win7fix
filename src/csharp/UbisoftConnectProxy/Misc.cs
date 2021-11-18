#nullable enable

using System;
using System.IO;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace UbisoftConnectProxy
{
    /// <summary>
    /// Base class for disposable classes with some QOL improvements. Supports multiple calls to Dispose (only the first call does something).
    /// </summary>
    public abstract class Disposable : IDisposable
    {
        /// <summary>
        /// Will be false until someone calls Dispose, which will set it to true and call DisposeOnce on the first invocation only.
        /// </summary>
        protected bool Disposed { get; private set; }

        /// <summary>
        /// Wraps DisposeOnce which is supposed to clean up.
        /// </summary>
#if NET5_0_OR_GREATER
#pragma warning disable CA1816 // Dispose methods should call SuppressFinalize
#endif
        public virtual void Dispose()
#if NET5_0_OR_GREATER
#pragma warning restore CA1816 // Dispose methods should call SuppressFinalize
#endif
        {
            if (Disposed)
            {
                return;
            }

            Disposed = true;
            DisposeOnce();
        }

        /// <summary>
        /// Throws an ObjectDisposedException if the object was disposed already.
        /// </summary>
        protected virtual void ThrowIfDisposed()
        {
            if (Disposed)
            {
                throw new ObjectDisposedException(GetType().Name);
            }
        }

        /// <summary>
        /// Called only if disposed was not true.
        /// </summary>
        protected abstract void DisposeOnce();
    }

    public static class Misc
    {
        /// <summary>
        /// Gets the current SynchronizationContext, and throws an InvalidOperationException if there isn't one.
        /// </summary>
        /// <exception cref="System.InvalidOperationException" />
        public static SynchronizationContext GetCurrentSynchronizationContext()
        {
            var synchronizationContext = SynchronizationContext.Current;
            if (synchronizationContext == null)
            {
                throw new InvalidOperationException("No SynchronizationContext available");
            }

            return synchronizationContext;
        }

        /// <summary>
        /// Throws an InvalidOperationException if there isn't one.
        /// </summary>
        /// <exception cref="System.InvalidOperationException" />
        public static void ThrowIfHasNoSynchronizationContext()
        {
            GetCurrentSynchronizationContext();
        }
        /// <summary>
        /// Yields num times. Useful to fight edge cases for wiring up live objects before they fire their first event. Not reliable in EVERY situation, but most. :(
        /// "Fixes" the problem that async functions take a whole dispatch cycle to return in the WPF context, even when no more work needs to be done async-ly.
        /// Without YieldLoop, "live code" can start publishing events before the events have even been subscribed, if an async function returns something like a connection object. Yielding a few times makes "sure" (it depends on how deep the stacktrace is, but usually it's not deeper than the default value of 10) that any "sync" code has had a chance to execute.
        /// An interesting observation is that Nito.AsyncEx seems to execute "sync" code after awaiting an async function (that has just completed awaiting something inside it) immediately. Huh.
        /// </summary>
        public static async Task YieldLoop(int num = 10)
        {
            while (--num >= 0)
            {
                await Task.Yield();
            }
        }

        public static byte[] ReadFully(this Stream stream, int numBytes)
        {
            var buffer = new byte[numBytes];
            stream.ReadFully(buffer.AsSpan(0, numBytes));
            return buffer;
        }
        public static void ReadFully(this Stream stream, Span<byte> buffer)
        {
            int offset = 0;
            while (offset < buffer.Length)
            {
                int read = stream.Read(buffer[offset..]);
                if (read <= 0)
                {
                    throw new EndOfStreamException();
                }
                offset += read;
            }
        }

        /// <summary>
        /// Dispose the disposable, ignoring any exceptions thrown by it. Does nothing if this is null.
        /// </summary>
        public static void DisposeSilent(this IDisposable disposable)
        {
            if (disposable == null)
            {
                return;
            }

            try
            {
                disposable.Dispose();
            }
            catch
            {
            }
        }

        /// <summary>
        /// A UTF8 encoding that does not emit a byte order mark.
        /// </summary>
        public static UTF8Encoding UTF8NoBOM { get; } = new UTF8Encoding(false);

        /// <summary>
        /// Returns a Task for awaiting of the completion.
        /// </summary>
        public static Task<T> DispatchAsync<T>(this SynchronizationContext @this, Func<Task<T>> func)
        {
            TaskCompletionSource<T> taskSource = new();
            @this.Post(async nul =>
            {
                try
                {
                    taskSource.TrySetResult(await func());
                }
                catch (Exception e)
                {
                    taskSource.TrySetException(e);
                }
            }, null);
            return taskSource.Task;
        }

        public static Task DispatchAsync(this SynchronizationContext @this, Func<Task> func)
        {
            TaskCompletionSource taskSource = new();
            @this.Post(async nul =>
            {
                try
                {
                    await func();
                    taskSource.TrySetResult();
                }
                catch (Exception e)
                {
                    taskSource.TrySetException(e);
                }
            }, null);

            return taskSource.Task;
        }
    }

    /// <summary>
    /// Async variant for Monitor.Wait and PulseAll.
    /// </summary>
    public sealed class PulsableWait : Disposable
    {
        private int _waiting = 0;
        private readonly SemaphoreSlim _semaphoreSlim = new(0);

        /// <summary>
        /// Pulses for a last time and cleans up.
        /// </summary>
        protected override void DisposeOnce()
        {
            Pulse();
            _semaphoreSlim.Dispose();
        }

        /// <summary>
        /// Fulfills all pending waits once.
        /// </summary>
        public void Pulse()
        {
            while (_waiting > 0)
            {
                _waiting--;
                _semaphoreSlim.Release();
            }
        }

        void StartWait()
        {
            if (Disposed)
            {
                throw new ObjectDisposedException(nameof(PulsableWait));
            }

            _waiting++;
        }

        /// <summary>
        /// Wait until Pulse is called.
        /// </summary>
        /// <returns></returns>
        public async Task WaitAsync()
        {
            StartWait();
            await _semaphoreSlim.WaitAsync();
        }

        /// <summary>
        /// Returns if it was completed by pulsing (true) or waiting (false).
        /// </summary>
        public async Task<bool> WaitAsync(TimeSpan waitForMax)
        {
            StartWait();
            var success = await _semaphoreSlim.WaitAsync(waitForMax);
            if (!success)
            {
                // "deregister" waiter
                _waiting--;
            }

            return success;
        }
    }
}

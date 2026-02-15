#nullable enable

using System;
using System.Threading.Tasks;

namespace UbisoftConnectProxy;

/// <summary>
/// A simple class that wraps logic for enforcing a sequential order on async operations. Is threadsafe.
/// </summary>
public sealed class SequentialAsyncAccess
{
    private readonly
#if NET9_0_OR_GREATER
        System.Threading.Lock
#else
        object
#endif
        _lock = new();

    private Task? _waitForMe;

    /// <summary>
    /// Waits for its turn and returns a SequentialAsyncAccessToken that must be disposed in order to allow the next action to start, but also shows if this was the most recent invocation.
    /// </summary>
    public async Task<SequentialAsyncAccessToken> AwaitAsync()
    {
        TaskCompletionSource taskCompletionSource = new();
        var myTask = taskCompletionSource.Task;

        Task? previousTask;
        bool isMostRecent = true;
        lock (_lock)
        {
            previousTask = _waitForMe;
            _waitForMe = myTask;
        }

        if (previousTask != null)
        {
            await previousTask;
            lock (_lock)
            {
                isMostRecent = _waitForMe == myTask;
            }
        }

        return new SequentialAsyncAccessToken(() =>
        {
            lock (_lock)
            {
                if (_waitForMe == myTask)
                {
                    _waitForMe = null;
                }
            }

            taskCompletionSource.TrySetResult();
        }, isMostRecent);
    }
}

/// <summary>
/// Used to control access to a sequentially-limited block of code.
/// </summary>
/// <remarks>
/// To be used internally only.
/// </remarks>
public sealed class SequentialAsyncAccessToken(Action onDispose, bool isMostRecent) : IDisposable
{
    /// <summary>
    /// Is this the most recent invocation?
    /// </summary>
    public bool IsMostRecent { get; } = isMostRecent;

    private readonly Action _onDispose = onDispose;

    /// <summary>
    /// Relinquish control of the sequentially-limited block.
    /// </summary>
    public void Dispose()
    {
        _onDispose();
    }
}

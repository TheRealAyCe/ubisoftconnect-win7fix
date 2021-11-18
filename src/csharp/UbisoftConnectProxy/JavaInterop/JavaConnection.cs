#nullable enable

using System;
using System.IO;
using UbisoftConnectProxy.JavaInterop.Dtos;

namespace UbisoftConnectProxy.JavaInterop
{
    public class JavaConnection : Disposable
    {
        private readonly Stream _stream;
        private readonly Sender _sender;

        private Exception? _disconnectException;

        private readonly Action<Exception?> _onDisconnected;
        private readonly Action _onJavaDnsReady;
        private readonly Action<ResponseDto> _onResponse;

        /// <summary>
        /// Create a connection from a bidirectional Stream.
        /// </summary>
        public JavaConnection(Stream stream, Action<Exception?> onDisconnected, Action onJavaDnsReady, Action<ResponseDto> onResponse)
        {
            Misc.ThrowIfHasNoSynchronizationContext();

            _onDisconnected = onDisconnected;
            _onJavaDnsReady = onJavaDnsReady;
            _onResponse = onResponse;

            _stream = stream;
            _sender = new(_stream, Disconnect);

            ReceiveLoopAsync();
        }

        public void Send(RequestDto dto) => Send(dto, RequestDto.MsgId);
        public void SendReady(Ready what) => Send(new WebserverReady { Ready = what }, WebserverReady.MsgId);
        public void Send(WebserverErrorDto dto) => Send(dto, WebserverErrorDto.MsgId);

        private void Send(IDataObject? dto, byte msgId)
        {
            ThrowIfDisposed();

            using MemoryStream fullMessage = new();
            fullMessage.WriteByte(msgId);
            if (dto != null)
            {
                using MemoryStream serialized = new();
                dto.Write(serialized);
                fullMessage.WriteJInt((int)serialized.Length);
                fullMessage.Write(serialized.ToArray());
            }
            _sender.Send(fullMessage.ToArray());
        }

        private async void ReceiveLoopAsync()
        {
            await Misc.YieldLoop();

            try
            {
                while (!Disposed)
                {
                    var msgId = (await _stream.ReadFullyAsync(1))[0];
                    if (msgId == JavaDnsReady.MsgId)
                    {
                        if (!Disposed)
                        {
                            _onJavaDnsReady();
                        }
                    }
                    else
                    {
                        var messageLength = await _stream.ReadJIntAsync();
                        var message = await _stream.ReadFullyAsync(messageLength);

                        using MemoryStream ms = new(message);
                        if (msgId == ResponseDto.MsgId)
                        {
                            ResponseDto response = new();
                            await response.ReadAsync(ms);
                            if (!Disposed)
                            {
                                _onResponse(response);
                            }
                        }
                        else
                        {
                            throw new IOException($"Unknown message '{msgId}'");
                        }
                    }
                }
            }
            catch (Exception e)
            {
                Disconnect(e);
            }
        }

        private void Disconnect(Exception? e)
        {
            if (Disposed)
            {
                return;
            }

            _disconnectException = e;

            Dispose();
        }

        /// <inheritdoc />
        protected override void DisposeOnce()
        {
            try
            {
                _stream.Dispose();
            }
            catch { }
            _sender.Dispose();
            _onDisconnected.Invoke(_disconnectException);
        }
    }
}

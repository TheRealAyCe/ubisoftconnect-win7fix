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
        private readonly Action<HelloFromJava> _onJavaConnected;
        private readonly Action<J_ResponseInitialDto> _onInitialResponse;
        private readonly Action<J_ResponseDataDto> _onResponseData;

        /// <summary>
        /// Create a connection from a bidirectional Stream.
        /// </summary>
        public JavaConnection(
            Stream stream,
            Action<Exception?> onDisconnected,
            Action<HelloFromJava> onJavaConnected,
            Action onJavaDnsReady,
            Action<J_ResponseInitialDto> onInitialResponse,
            Action<J_ResponseDataDto> onResponseData
            )
        {
            Misc.ThrowIfHasNoSynchronizationContext();

            _onDisconnected = onDisconnected;
            _onJavaDnsReady = onJavaDnsReady;
            _onJavaConnected = onJavaConnected;
            _onInitialResponse = onInitialResponse;
            _onResponseData = onResponseData;

            _stream = stream;
            _sender = new(_stream, Disconnect);

            ReceiveLoopAsync();
        }

        public void Send(WS_WebserverErrorDto dto) => Send(dto, WS_WebserverErrorDto.MsgId);
        public void SendReady(Ready what) => Send(new WS_WebserverReady { Ready = what }, WS_WebserverReady.MsgId);
        public void Send(WS_RequestDto dto) => Send(dto, WS_RequestDto.MsgId);
        public void Send(WS_AbortRequestDto dto) => Send(dto, WS_AbortRequestDto.MsgId);

        public void Send(IDataObject dto, byte msgId)
        {
            ThrowIfDisposed();

            using MemoryStream fullMessage = new();
            fullMessage.WriteByte(msgId);
            //if (dto != null)
            {
                dto.Write(fullMessage);
                //using MemoryStream serialized = new();
                //dto.Write(serialized);
                //fullMessage.WriteJInt((int)serialized.Length);
                //fullMessage.Write(serialized.ToArray());
            }
            _sender.Send(fullMessage.ToArray());
        }

        private const byte MsgId_J_Code = 20;
        private const byte MsgId_J_ResponseInitial = 21;
        private const byte MsgId_J_ResponseData = 22;

        private async void ReceiveLoopAsync()
        {
            await Misc.YieldLoop();

            try
            {
                {
                    // initial data
                    HelloFromJava msg = new();
                    await msg.ReadAsync(_stream);
                    if (!Disposed)
                    {
                        _onJavaConnected(msg);
                    }
                }

                while (!Disposed)
                {
                    var msgId = (await _stream.ReadFullyAsync(1))[0];
                    //Console.WriteLine($"Received msg {msgId}");
                    switch (msgId)
                    {
                        case MsgId_J_Code:
                            {
                                JavaStatusCode msg = new();
                                await msg.ReadAsync(_stream);
                                if (!Disposed && msg.StatusCode == 1)
                                {
                                    _onJavaDnsReady();
                                }
                                break;
                            }
                        case MsgId_J_ResponseInitial:
                            {
                                J_ResponseInitialDto msg = new();
                                await msg.ReadAsync(_stream);
                                if (!Disposed)
                                {
                                    _onInitialResponse(msg);
                                }
                                break;
                            }
                        case MsgId_J_ResponseData:
                            {
                                J_ResponseDataDto msg = new();
                                await msg.ReadAsync(_stream);
                                if (!Disposed)
                                {
                                    _onResponseData(msg);
                                }
                                break;
                            }
                        default:
                            throw new IOException($"Unknown message '{msgId}'");
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

package com.iosoft.ubiconfor;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.function.Consumer;

import com.iosoft.helpers.Misc;
import com.iosoft.helpers.WeirdException;
import com.iosoft.helpers.WrapException;
import com.iosoft.helpers.io.DataHelper;
import com.iosoft.helpers.network.ReceiverHelper;
import com.iosoft.helpers.network.tcp.TcpConnection;
import com.iosoft.ubiconfor.dtos.IJMessage;
import com.iosoft.ubiconfor.dtos.ISendable;
import com.iosoft.ubiconfor.dtos.J_Code;
import com.iosoft.ubiconfor.dtos.WS_AbortRequest;
import com.iosoft.ubiconfor.dtos.WS_RequestDto;
import com.iosoft.ubiconfor.dtos.WS_WebserverErrorDto;
import com.iosoft.ubiconfor.dtos.WS_WebserverReady;

public class Connection {
	private final TcpConnection _tcpConnection;
	private final Consumer<Exception> _onDisconnected;
	private final Consumer<WS_RequestDto> _onRequest;
	private final Consumer<WS_AbortRequest> _onAbortRequest;
	private final Consumer<WS_WebserverReady> _onReady;
	private final Consumer<WS_WebserverErrorDto> _onError;
	private final DataHelper _dh = new DataHelper();

	public Connection(Socket socket, Consumer<Exception> onDisconnected, Consumer<WS_RequestDto> onRequest,
			Consumer<WS_AbortRequest> onAbortRequest, Consumer<WS_WebserverReady> onReady,
			Consumer<WS_WebserverErrorDto> onError) throws WeirdException {
		_onDisconnected = onDisconnected;
		_onRequest = onRequest;
		_onAbortRequest = onAbortRequest;
		_onReady = onReady;
		_onError = onError;

		_tcpConnection = new TcpConnection(socket, this::receiveWorkerThread);
		_tcpConnection.eventOnDisconnected().register(this::onDisconnected);
		// named pipes did not work, as Java does not support async access for them
		// (read&write at the same time)
		// _pipe = new RandomAccessFile("\\\\.\\pipe\\ubiconfor", "rw");
	}

	public void send(ISendable msg) {
		//System.out.println("Sending " + Misc.getClassName(msg));
		try {
			msg.writeMsg(_dh.Stream);
			_tcpConnection.send(_dh.finish());
		} catch (IOException e) {
			throw new WrapException(e);
		}
	}

	public void sendStatus(int status) {
		J_Code msg = new J_Code();
		msg.StatusCode = status;
		send(msg);
	}

//	private void sendMsg(ResponseDto dto, byte msgId) {
//		try {
//			_dh.Stream.writeByte(msgId);
//			if (dto != null) {
//				try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
//					try (DataOutputStream dos = new DataOutputStream(baos)) {
//						dto.write(dos);
//						_dh.Stream.writeInt(baos.size());
//						_dh.Stream.write(baos.toByteArray());
//					}
//				}
//			}
//			_tcpConnection.send(_dh.finish());
//		} catch (IOException e) {
//			throw new WrapException(e);
//		}
//	}

	public void kick() {
		_tcpConnection.disconnect();
	}

	private void onDisconnected(Exception e) {
		_onDisconnected.accept(e);
	}

	private void receiveWorkerThread(ReceiverHelper rh) throws IOException {
		while (true) {
			rh.checkDisconnecting();

			byte msgId = rh.Stream.readByte();

			// byte[] message = new byte[rh.Stream.readInt()];
			System.out.println("Receiving " + msgId);
			// rh.Stream.readFully(message);
			// try (DataInputStream dis = new DataInputStream(new
			// ByteArrayInputStream(message))) {
			switch (msgId) {
			case IJMessage.MsgId_WS_Request: {
				WS_RequestDto request = new WS_RequestDto();
				request.read(rh.Stream);
				rh.post(() -> _onRequest.accept(request));
				break;
			}
			case IJMessage.MsgId_WS_AbortRequest: {
				WS_AbortRequest request = new WS_AbortRequest();
				request.read(rh.Stream);
				rh.post(() -> _onAbortRequest.accept(request));
				break;
			}
			case IJMessage.MsgId_WS_WebserverError: {
				WS_WebserverErrorDto request = new WS_WebserverErrorDto();
				request.read(rh.Stream);
				rh.post(() -> _onError.accept(request));
				break;
			}
			case IJMessage.MsgId_WS_WebserverReady: {
				WS_WebserverReady request = new WS_WebserverReady();
				request.read(rh.Stream);
				rh.post(() -> _onReady.accept(request));
				break;
			}
			default:
				throw new IOException("Unknown msgId " + msgId);
			}
			// }
		}
	}
}

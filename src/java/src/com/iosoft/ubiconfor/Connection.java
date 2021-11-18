package com.iosoft.ubiconfor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.function.Consumer;

import com.iosoft.helpers.DataHelper;
import com.iosoft.helpers.WeirdException;
import com.iosoft.helpers.WrapException;
import com.iosoft.helpers.network.ReceiverHelper;
import com.iosoft.helpers.network.tcp.TcpConnection;
import com.iosoft.ubiconfor.dtos.JavaDnsReady;
import com.iosoft.ubiconfor.dtos.RequestDto;
import com.iosoft.ubiconfor.dtos.ResponseDto;
import com.iosoft.ubiconfor.dtos.WebserverErrorDto;
import com.iosoft.ubiconfor.dtos.WebserverReady;

public class Connection {
	private final TcpConnection _tcpConnection;
	private final Consumer<Exception> _onDisconnected;
	private final Consumer<RequestDto> _onRequest;
	private final Consumer<WebserverReady> _onReady;
	private final Consumer<WebserverErrorDto> _onError;
	private final DataHelper _dh = new DataHelper();

	public Connection(Socket socket, Consumer<Exception> onDisconnected, Consumer<RequestDto> onRequest,
			Consumer<WebserverReady> onReady, Consumer<WebserverErrorDto> onError) throws WeirdException {
		_onDisconnected = onDisconnected;
		_onRequest = onRequest;
		_onReady = onReady;
		_onError = onError;

		_tcpConnection = new TcpConnection(socket, this::receiveWorkerThread);
		_tcpConnection.eventOnDisconnected().register(this::onDisconnected);
		// _pipe = new RandomAccessFile("\\\\.\\pipe\\ubiconfor", "rw");
	}

	public void send(ResponseDto dto) {
		sendMsg(dto, ResponseDto.MsgId);
	}

	public void sendDnsReady() {
		sendMsg(null, JavaDnsReady.MsgId);
	}

	private void sendMsg(ResponseDto dto, byte msgId) {
		try {
			_dh.getStream().writeByte(msgId);
			if (dto != null) {
				try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
					try (DataOutputStream dos = new DataOutputStream(baos)) {
						dto.write(dos);
						_dh.getStream().writeInt(baos.size());
						_dh.getStream().write(baos.toByteArray());
					}
				}
			}
			_tcpConnection.send(_dh.finish());
		} catch (IOException e) {
			throw new WrapException(e);
		}
	}

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

			byte[] message = new byte[rh.Stream.readInt()];
			rh.Stream.readFully(message);
			try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(message))) {
				if (msgId == RequestDto.MsgId) {
					RequestDto request = new RequestDto();
					request.read(dis);
					rh.post(() -> _onRequest.accept(request));
				} else if (msgId == WebserverErrorDto.MsgId) {
					WebserverErrorDto request = new WebserverErrorDto();
					request.read(dis);
					rh.post(() -> _onError.accept(request));
				} else if (msgId == WebserverReady.MsgId) {
					WebserverReady request = new WebserverReady();
					request.read(dis);
					rh.post(() -> _onReady.accept(request));
				} else {
					throw new IOException("Unknown msgId " + msgId);
				}
			}
		}
	}
}

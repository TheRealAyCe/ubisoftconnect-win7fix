package com.iosoft.ubiconfor.dtos;

import java.io.DataOutput;
import java.io.IOException;

/**
 * Single int status code.
 */
public final class J_Code implements IJMessage {
	public static final int Code_DnsReady = 1;

	public int StatusCode;

	@Override
	public void write(DataOutput dos) throws IOException {
		dos.writeInt(StatusCode);
	}

	@Override
	public int getMsgId() {
		return MsgId_J_Code;
	}
}

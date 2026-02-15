package com.iosoft.ubiconfor.dtos;

import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;

public interface IJMessage extends ISendable {
	// Java -> C#
	public static final byte MsgId_J_Code = 20;
	public static final byte MsgId_J_ResponseInitial = 21;
	public static final byte MsgId_J_ResponseData = 22;
	
	// C# -> Java
	public static final byte MsgId_WS_WebserverError = 1;
	public static final byte MsgId_WS_WebserverReady = 2;
	public static final byte MsgId_WS_Request = 3;
	public static final byte MsgId_WS_AbortRequest = 4;

	int getMsgId();

	void write(DataOutput out) throws IOException;

	@Override
	default void writeMsg(DataOutputStream out) throws IOException {
		out.write(getMsgId());
		write(out);
	}
}

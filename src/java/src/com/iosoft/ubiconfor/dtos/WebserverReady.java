package com.iosoft.ubiconfor.dtos;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import com.iosoft.helpers.MiscIO;

public final class WebserverReady implements DataObject {
	public static final byte MsgId = 11;

	public Ready What;

	@Override
	public void read(DataInput in) throws IOException {
		What = MiscIO.readByte(in, Ready.values());
	}

	@Override
	public void write(DataOutput out) throws IOException {
		MiscIO.writeByte(out, What);
	}

	public enum Ready {
		Hosts, Starting, Running
	}
}

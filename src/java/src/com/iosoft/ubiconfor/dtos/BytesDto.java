package com.iosoft.ubiconfor.dtos;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import com.iosoft.helpers.Misc;

public class BytesDto implements IDataObject {
	public byte[] Payload = Misc.EMPTYBYTES;

	@Override
	public void read(DataInput in) throws IOException {
		Payload = new byte[in.readInt()];
		in.readFully(Payload);
	}

	@Override
	public void write(DataOutput out) throws IOException {
		out.writeInt(Payload.length);
		out.write(Payload);
	}
}

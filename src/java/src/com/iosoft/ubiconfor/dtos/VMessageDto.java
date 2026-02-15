package com.iosoft.ubiconfor.dtos;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public abstract class VMessageDto implements IDataObject {
	public int RequestId;

	@Override
	public void read(DataInput in) throws IOException {
		RequestId = in.readInt();
	}

	@Override
	public void write(DataOutput out) throws IOException {
		out.writeInt(RequestId);
	}
}

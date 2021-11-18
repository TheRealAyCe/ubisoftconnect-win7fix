package com.iosoft.ubiconfor.dtos;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public abstract class MessageDto<T extends DataObject> implements DataObject {
	public int RequestId;
	public T Data;

	@Override
	public void read(DataInput in) throws IOException {
		RequestId = in.readInt();
		Data.read(in);
	}

	@Override
	public void write(DataOutput out) throws IOException {
		out.writeInt(RequestId);
		Data.write(out);
	}
}

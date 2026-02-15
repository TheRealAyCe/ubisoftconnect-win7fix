package com.iosoft.ubiconfor.dtos;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class ResponseHeaderDto implements IDataObject {
	public int StatusCode = 500;
	public HeadersDto Headers = new HeadersDto();

	@Override
	public void read(DataInput in) throws IOException {
		StatusCode = in.readInt();
		Headers.read(in);
	}

	@Override
	public void write(DataOutput out) throws IOException {
		out.writeInt(StatusCode);
		Headers.write(out);
	}
}

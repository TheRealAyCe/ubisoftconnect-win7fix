package com.iosoft.ubiconfor.dtos;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class HttpDataDto implements DataObject {
	public HeaderDto[] Headers;
	public byte[] Content;

	@Override
	public void read(DataInput in) throws IOException {
		Headers = new HeaderDto[in.readInt()];
		for (int i = 0; i < Headers.length; i++) {
			(Headers[i] = new HeaderDto()).read(in);
		}
		Content = new byte[in.readInt()];
		in.readFully(Content);
	}

	@Override
	public void write(DataOutput out) throws IOException {
		out.writeInt(Headers.length);
		for (int i = 0; i < Headers.length; i++) {
			Headers[i].write(out);
		}
		out.writeInt(Content.length);
		out.write(Content);
	}
}

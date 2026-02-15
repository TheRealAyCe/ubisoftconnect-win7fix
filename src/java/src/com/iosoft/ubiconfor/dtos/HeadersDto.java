package com.iosoft.ubiconfor.dtos;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class HeadersDto implements IDataObject {
	private static final HeaderDto[] Empty = new HeaderDto[0];
	
	public HeaderDto[] Headers = Empty;

	@Override
	public void read(DataInput in) throws IOException {
		Headers = new HeaderDto[in.readInt()];
		for (int i = 0; i < Headers.length; i++) {
			(Headers[i] = new HeaderDto()).read(in);
		}
	}

	@Override
	public void write(DataOutput out) throws IOException {
		out.writeInt(Headers.length);
		for (int i = 0; i < Headers.length; i++) {
			Headers[i].write(out);
		}
	}
}

package com.iosoft.ubiconfor.dtos;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class ResponseDataDto extends HttpDataDto {
	public int StatusCode;

	@Override
	public void read(DataInput in) throws IOException {
		StatusCode = in.readInt();
		super.read(in);
	}

	@Override
	public void write(DataOutput out) throws IOException {
		out.writeInt(StatusCode);
		super.write(out);
	}
}

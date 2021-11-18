package com.iosoft.ubiconfor.dtos;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class RequestDataDto extends HttpDataDto {
	public String Uri;
	public String Method;

	@Override
	public void read(DataInput in) throws IOException {
		Uri = in.readUTF();
		Method = in.readUTF();
		super.read(in);
	}

	@Override
	public void write(DataOutput out) throws IOException {
		out.writeUTF(Uri);
		out.writeUTF(Method);
		super.write(out);
	}
}

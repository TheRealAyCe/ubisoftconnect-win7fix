package com.iosoft.ubiconfor.dtos;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class RequestDataDto implements IDataObject {
	public String Uri;
	public String Method;
	public HeadersDto Headers;
	public BytesDto Content;

	@Override
	public void read(DataInput in) throws IOException {
		Uri = in.readUTF();
		Method = in.readUTF();
		Headers = new HeadersDto();
		Headers.read(in);
		Content = new BytesDto();
		Content.read(in);
	}

	@Override
	public void write(DataOutput out) throws IOException {
		out.writeUTF(Uri);
		out.writeUTF(Method);
		Headers.write(out);
		Content.write(out);
	}
}

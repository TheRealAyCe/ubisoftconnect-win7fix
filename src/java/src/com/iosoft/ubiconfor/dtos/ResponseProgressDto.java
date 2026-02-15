package com.iosoft.ubiconfor.dtos;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class ResponseProgressDto implements IDataObject {
	public boolean Finish;
	public BytesDto Response = new BytesDto();

	@Override
	public void read(DataInput in) throws IOException {
		Finish = in.readBoolean();
		Response.read(in);
	}

	@Override
	public void write(DataOutput out) throws IOException {
		out.writeBoolean(Finish);
		Response.write(out);
	}
}

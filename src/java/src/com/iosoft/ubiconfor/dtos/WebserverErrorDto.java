package com.iosoft.ubiconfor.dtos;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class WebserverErrorDto implements DataObject {
	public static final byte MsgId = 12;

	public boolean Fatal;
	public String Text;

	@Override
	public void read(DataInput in) throws IOException {
		Fatal = in.readBoolean();
		Text = in.readUTF();
	}

	@Override
	public void write(DataOutput out) throws IOException {
		out.writeBoolean(Fatal);
		out.writeUTF(Text);
	}
}

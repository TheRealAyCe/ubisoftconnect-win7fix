package com.iosoft.ubiconfor.dtos;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class HeaderDto implements DataObject {
	public String Name;
	public String[] Values;

	@Override
	public void read(DataInput in) throws IOException {
		Name = in.readUTF();
		Values = new String[in.readInt()];
		for (int i = 0; i < Values.length; i++) {
			Values[i] = in.readUTF();
		}
	}

	@Override
	public void write(DataOutput out) throws IOException {
		out.writeUTF(Name);
		out.writeInt(Values.length);
		for (int i = 0; i < Values.length; i++) {
			out.writeUTF(Values[i]);
		}
	}
}

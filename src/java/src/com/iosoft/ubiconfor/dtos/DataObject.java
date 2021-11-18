package com.iosoft.ubiconfor.dtos;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public interface DataObject {
	void read(DataInput in) throws IOException;

	void write(DataOutput out) throws IOException;
}

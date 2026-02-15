package com.iosoft.ubiconfor.dtos;

import java.io.DataOutputStream;
import java.io.IOException;

public interface ISendable {
	void writeMsg(DataOutputStream dos) throws IOException;
}

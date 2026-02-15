package com.iosoft.ubiconfor.dtos;

import java.io.DataOutputStream;
import java.io.IOException;

public final class J_Hello implements ISendable {
	public String[] Domains;

	@Override
	public void writeMsg(DataOutputStream out) throws IOException {
		out.write(Domains.length);
		for (String domain : Domains) {
			out.writeUTF(domain);
		}
	}
}

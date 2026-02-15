package com.iosoft.ubiconfor;

public enum Mode {
	Normal(true, true), UseProxy(true, false), ProvideProxy(false, true);

	public final boolean UseHostsFileEntry, HostProxy;

	Mode(boolean useHostsFileEntry, boolean hostProxy) {
		UseHostsFileEntry = useHostsFileEntry;
		HostProxy = hostProxy;
	}
}

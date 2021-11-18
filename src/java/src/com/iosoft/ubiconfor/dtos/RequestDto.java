package com.iosoft.ubiconfor.dtos;

public class RequestDto extends MessageDto<RequestDataDto> {
	public static final byte MsgId = 10;

	{
		Data = new RequestDataDto();
	}
}

package com.iosoft.ubiconfor.dtos;

public final class ResponseDto extends MessageDto<ResponseDataDto> {
	public static final byte MsgId = 1;

	{
		Data = new ResponseDataDto();
	}
}

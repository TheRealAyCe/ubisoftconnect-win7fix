package com.iosoft.ubiconfor.dtos;

public final class J_ResponseProgressDto extends MessageDto<ResponseProgressDto> implements IJMessage {
	{
		Data = new ResponseProgressDto();
	}

	@Override
	public int getMsgId() {
		return MsgId_J_ResponseData;
	}
}

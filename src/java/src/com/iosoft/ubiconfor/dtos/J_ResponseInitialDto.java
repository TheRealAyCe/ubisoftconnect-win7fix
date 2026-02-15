package com.iosoft.ubiconfor.dtos;

public final class J_ResponseInitialDto extends MessageDto<ResponseHeaderDto> implements IJMessage {
	{
		Data = new ResponseHeaderDto();
	}

	@Override
	public int getMsgId() {
		return MsgId_J_ResponseInitial;
	}
}

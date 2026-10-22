package com.meokcham.service;

import java.time.ZoneId;

import com.meokcham.exception.BusinessException;
import com.meokcham.exception.ErrorCode;

final class TimezoneValidator {

	private TimezoneValidator() {
	}

	/** IANA 이름만 허용한다. ZoneId.of()는 "+09:00" 같은 오프셋도 받아서 서머타임 계산이 틀어지므로 쓰지 않는다. */
	static void requireValid(String timezone) {
		if (!ZoneId.getAvailableZoneIds().contains(timezone)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED);
		}
	}
}

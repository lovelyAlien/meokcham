package com.meokcham.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.meokcham.domain.User;
import com.meokcham.exception.BusinessException;
import com.meokcham.exception.ErrorCode;
import com.meokcham.repository.UserRepository;

@Service
public class UserService {

	private final UserRepository users;

	public UserService(UserRepository users) {
		this.users = users;
	}

	@Transactional(readOnly = true)
	public User get(long userId) {
		return find(userId);
	}

	/** 이후 기록부터 새 타임존으로 계산한다. 이미 저장된 기록의 날짜는 바뀌지 않는다. */
	@Transactional
	public User changeTimezone(long userId, String timezone) {
		TimezoneValidator.requireValid(timezone);
		User user = find(userId);
		user.changeTimezone(timezone);
		return user;
	}

	/** 하위 데이터는 DB의 ON DELETE CASCADE로 함께 지워진다. */
	@Transactional
	public void delete(long userId) {
		users.delete(find(userId));
	}

	// 계정이 삭제됐어도 만료 전 Access 토큰은 유효하므로, 없으면 인증 실패로 취급한다.
	private User find(long userId) {
		return users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
	}
}

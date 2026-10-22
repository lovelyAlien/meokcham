package com.meokcham.domain;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "social_accounts")
public class SocialAccount {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id")
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private SocialProvider provider;

	/** ID 토큰의 sub. 사용자를 식별하는 값이다(이메일이 아니다). */
	@Column(nullable = false)
	private String providerSubject;

	/** Apple은 숨기거나 처음 한 번만 주므로 참고 정보로만 둔다. */
	@Column(length = 320)
	private String email;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected SocialAccount() {
	}

	public SocialAccount(User user, SocialProvider provider, String providerSubject, String email) {
		this.user = user;
		this.provider = provider;
		this.providerSubject = providerSubject;
		this.email = email;
	}

	public Long getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public SocialProvider getProvider() {
		return provider;
	}

	public String getProviderSubject() {
		return providerSubject;
	}

	public String getEmail() {
		return email;
	}
}

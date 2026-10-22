package com.meokcham.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.meokcham.domain.SocialAccount;
import com.meokcham.domain.SocialProvider;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

	Optional<SocialAccount> findByProviderAndProviderSubject(SocialProvider provider, String providerSubject);
}

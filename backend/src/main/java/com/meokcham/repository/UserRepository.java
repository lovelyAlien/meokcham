package com.meokcham.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.meokcham.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {
}

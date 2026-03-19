package com.seoulchonnom.aggregate.common.generator;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class PasswordGenerator {
	private PasswordEncoder passwordEncoder;

	@PostConstruct
	protected void init() {
		this.passwordEncoder = new BCryptPasswordEncoder();
	}

	public String encode(CharSequence rawPassword) {
		return this.passwordEncoder.encode(rawPassword);
	}

}

package com.seoulchonnom.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.seoulchonnom.auth.filter.JwtAuthenticationFilter;
import com.seoulchonnom.auth.util.JwtTokenProvider;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class AuthConfiguration {

	private final JwtTokenProvider jwtTokenProvider;

	@Bean
	public JwtAuthenticationFilter jwtAuthenticationFilter() {
		return new JwtAuthenticationFilter(jwtTokenProvider);
	}
}

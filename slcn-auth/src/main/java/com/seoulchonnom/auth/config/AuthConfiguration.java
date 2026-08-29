package com.seoulchonnom.auth.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.seoulchonnom.auth.filter.ImageSessionAuthenticationFilter;
import com.seoulchonnom.auth.filter.JwtAuthenticationFilter;
import com.seoulchonnom.auth.store.RefreshSessionStore;
import com.seoulchonnom.auth.util.JwtTokenProvider;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class AuthConfiguration {

	private final JwtTokenProvider jwtTokenProvider;
	private final RefreshSessionStore refreshSessionStore;

	@Bean
	public JwtAuthenticationFilter jwtAuthenticationFilter() {
		return new JwtAuthenticationFilter(jwtTokenProvider);
	}

	@Bean
	public ImageSessionAuthenticationFilter imageSessionAuthenticationFilter() {
		return new ImageSessionAuthenticationFilter(refreshSessionStore);
	}

	/**
	 * Filter 빈은 서블릿 컨테이너에도 자동 등록된다.
	 * 이 필터는 시큐리티 체인 안에서만 동작해야 하므로 컨테이너 등록을 끈다.
	 */
	@Bean
	public FilterRegistrationBean<ImageSessionAuthenticationFilter> imageSessionAuthenticationFilterRegistration(
		ImageSessionAuthenticationFilter imageSessionAuthenticationFilter) {
		FilterRegistrationBean<ImageSessionAuthenticationFilter> registration =
			new FilterRegistrationBean<>(imageSessionAuthenticationFilter);
		registration.setEnabled(false);
		return registration;
	}
}

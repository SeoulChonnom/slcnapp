package com.seoulchonnom.boot.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.CacheControlHeadersWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.web.cors.CorsUtils;

import com.seoulchonnom.auth.constant.AuthConstant;
import com.seoulchonnom.auth.filter.ImageSessionAuthenticationFilter;
import com.seoulchonnom.auth.filter.JwtAuthenticationFilter;
import com.seoulchonnom.auth.handler.CommonAccessDeniedHandler;
import com.seoulchonnom.auth.handler.CommonAuthenticationEntryPoint;
import com.seoulchonnom.auth.matcher.AssetRequestMatchers;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {
	private static final String USER_AUTHORITY = "USER";
	private static final String ADMIN_AUTHORITY = "ADMIN";

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final ImageSessionAuthenticationFilter imageSessionAuthenticationFilter;
	private final CommonAuthenticationEntryPoint commonAuthenticationEntryPoint;
	private final CommonAccessDeniedHandler commonAccessDeniedHandler;

	/**
	 * 이미지 조회만 별도 체인으로 분리한다. img 태그는 요청 헤더를 붙일 수 없어
	 * sessionId 쿠키 외에 액세스 토큰을 전달할 방법이 없다.
	 *
	 * 쿠키 인증을 기본 체인에 폴백으로 넣으면 액세스 토큰이 만료된 다른 경로의 요청까지
	 * 인증된 것으로 취급되어 401 대신 403이 나가고, 프론트의 재발급 흐름이 끊긴다.
	 * 체인을 나누면 쿠키 인증이 이미지 조회 밖으로 새어나갈 수 없다.
	 *
	 * 액세스 토큰이 있으면 그쪽이 먼저 인증하므로 기존 호출은 그대로 동작한다.
	 * OPTIONS 프리플라이트는 이 체인의 GET 조건에 걸리지 않아 기본 체인에서 허용된다.
	 */
	@Bean
	@Order(1)
	public SecurityFilterChain imageFilterChain(HttpSecurity http) throws Exception {
		http.securityMatcher(AssetRequestMatchers.IMAGE_READ_MATCHER)
			.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(management -> management.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(authorizeRequests -> authorizeRequests
				.anyRequest().hasAnyAuthority(USER_AUTHORITY, AuthConstant.IMAGE_READ_AUTHORITY))
			// 이 체인은 전부 이미지 조회이므로, 캐시 헤더는 FileResource가 지정한 값을 그대로 내보낸다.
			.headers(headers -> headers.cacheControl(HeadersConfigurer.CacheControlConfig::disable))
			.exceptionHandling(handling -> handling
				.authenticationEntryPoint(commonAuthenticationEntryPoint)
				.accessDeniedHandler(commonAccessDeniedHandler))
			.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
			.addFilterAfter(imageSessionAuthenticationFilter, JwtAuthenticationFilter.class);

		return http.build();
	}

	@Bean
	@Order(2)
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		// TODO: 보안 로직 수정 필요
			http.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(management -> management.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests((authorizeRequests) -> authorizeRequests
					.requestMatchers("/swagger-ui/**", "/v3/**", "/error").permitAll()
					.requestMatchers("/users/login", "/users/token", "/users/logout").permitAll()
					.requestMatchers("/clients/token").permitAll()
					.requestMatchers(CorsUtils::isPreFlightRequest).permitAll()
					.requestMatchers("/users/register").hasAuthority(ADMIN_AUTHORITY)
						.anyRequest().hasAuthority(USER_AUTHORITY))
				.headers(headers -> headers
					// 기본 writer를 끄고, 이미지 조회를 뺀 나머지에만 다시 적용한다.
					.cacheControl(HeadersConfigurer.CacheControlConfig::disable)
					.addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
						new NegatedRequestMatcher(AssetRequestMatchers.CACHEABLE_IMAGE_MATCHER),
						new CacheControlHeadersWriter())))
				.exceptionHandling(handling -> handling
					.authenticationEntryPoint(commonAuthenticationEntryPoint)
					.accessDeniedHandler(commonAccessDeniedHandler))
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}
}

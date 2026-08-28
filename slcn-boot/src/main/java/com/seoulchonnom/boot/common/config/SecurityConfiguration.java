package com.seoulchonnom.boot.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsUtils;

import com.seoulchonnom.auth.filter.JwtAuthenticationFilter;
import com.seoulchonnom.auth.handler.CommonAccessDeniedHandler;
import com.seoulchonnom.auth.handler.CommonAuthenticationEntryPoint;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

	/**
	 * 이미지 조회 응답만 브라우저 캐시를 허용한다. 파일 ID와 저장 파일명이 불변이라 안전하며,
	 * 나머지 응답에는 Spring Security 기본 no-store 정책을 그대로 유지한다.
	 */
	private static final RequestMatcher CACHEABLE_IMAGE_MATCHER = new OrRequestMatcher(
		imageMatcher("/assets/files/"),
		imageMatcher("/assets/file")
	);

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final CommonAuthenticationEntryPoint commonAuthenticationEntryPoint;
	private final CommonAccessDeniedHandler commonAccessDeniedHandler;

	private static RequestMatcher imageMatcher(String prefix) {
		return request -> HttpMethod.GET.matches(request.getMethod())
			&& pathWithinApplication(request).startsWith(prefix);
	}

	private static String pathWithinApplication(HttpServletRequest request) {
		String uri = request.getRequestURI();
		String contextPath = request.getContextPath();
		return contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)
			? uri.substring(contextPath.length())
			: uri;
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		// TODO: 보안 로직 수정 필요
			http.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(management -> management.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests((authorizeRequests) -> authorizeRequests
					.requestMatchers("/swagger-ui/**", "/v3/**", "/error").permitAll()
					.requestMatchers("/users/login", "/users/token", "/users/logout").permitAll()
					.requestMatchers("/clients/token").permitAll()
					.requestMatchers(CorsUtils::isPreFlightRequest).permitAll()
					.requestMatchers("/users/register").hasAuthority("ADMIN")
						.anyRequest().hasAuthority("USER"))
				.headers(headers -> headers
					// 기본 writer를 끄고, 이미지 조회를 뺀 나머지에만 다시 적용한다.
					.cacheControl(HeadersConfigurer.CacheControlConfig::disable)
					.addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
						new NegatedRequestMatcher(CACHEABLE_IMAGE_MATCHER),
						new CacheControlHeadersWriter())))
				.exceptionHandling(handling -> handling
					.authenticationEntryPoint(commonAuthenticationEntryPoint)
					.accessDeniedHandler(commonAccessDeniedHandler))
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}
}

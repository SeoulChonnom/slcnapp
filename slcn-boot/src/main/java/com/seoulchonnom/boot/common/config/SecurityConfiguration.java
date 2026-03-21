package com.seoulchonnom.boot.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsUtils;

import com.seoulchonnom.auth.filter.JwtAuthenticationFilter;
import com.seoulchonnom.boot.common.entrypoint.CommonAuthenticationEntryPoint;
import com.seoulchonnom.rest.common.handler.CommonAccessDeniedHandler;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		// TODO: 보안 로직 수정 필요
		http.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(management -> management.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests((authorizeRequests) -> authorizeRequests
				.requestMatchers("/swagger-ui/**", "/v3/**").permitAll()
				.requestMatchers("/user/login", "/user/token").permitAll()
				.requestMatchers(CorsUtils::isPreFlightRequest).permitAll()
				.requestMatchers("/user/register").hasAuthority("ADMIN")
				.anyRequest().hasAuthority("USER"))
			.exceptionHandling(handling -> handling.authenticationEntryPoint(new CommonAuthenticationEntryPoint()))
			.exceptionHandling(handling -> handling.accessDeniedHandler(new CommonAccessDeniedHandler()))
			.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}
}

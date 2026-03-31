package com.seoulchonnom.auth.filter;

import java.io.IOException;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.seoulchonnom.auth.util.JwtTokenProvider;
import com.seoulchonnom.auth.util.JwtTokenProvider.TokenValidationResult;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
	private static final Set<String> PUBLIC_AUTH_PATHS = Set.of("/user/login", "/user/register", "/user/token");

	private final JwtTokenProvider jwtTokenProvider;

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return CorsUtils.isPreFlightRequest(request) || PUBLIC_AUTH_PATHS.contains(request.getServletPath());
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws IOException, ServletException {
		String token = jwtTokenProvider.resolveToken(request);
		TokenValidationResult validationResult = jwtTokenProvider.validateAccessToken(token);
		if (validationResult.valid()) {
			Authentication authentication = jwtTokenProvider.getAuthentication(validationResult.claims());
			SecurityContextHolder.getContext().setAuthentication(authentication);
		}
		chain.doFilter(request, response);
	}
}

package com.seoulchonnom.auth.filter;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.GenericFilterBean;

import com.seoulchonnom.auth.util.JwtTokenProvider;
import com.seoulchonnom.auth.util.JwtTokenProvider.TokenValidationResult;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends GenericFilterBean {

	private final JwtTokenProvider jwtTokenProvider;

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException,
		ServletException {
		String token = jwtTokenProvider.resolveToken((HttpServletRequest)request);
		TokenValidationResult validationResult = jwtTokenProvider.validateAccessToken(token);
		if (validationResult.valid()) {
			Authentication authentication = jwtTokenProvider.getAuthentication(validationResult.claims());
			SecurityContextHolder.getContext().setAuthentication(authentication);
		}
		chain.doFilter(request, response);
	}
}

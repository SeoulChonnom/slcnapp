package com.seoulchonnom.slcnapp.common.handler;

import static com.seoulchonnom.slcnapp.user.UserConstant.*;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seoulchonnom.slcnapp.common.dto.BaseResponse;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class CommonAccessDeniedHandler implements AccessDeniedHandler {
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
		AccessDeniedException accessDeniedException) throws IOException, ServletException {
		log.info(accessDeniedException.getLocalizedMessage());

		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setContentType("text/json;charset=UTF-8");
		response.setStatus(HttpStatus.FORBIDDEN.value());

		response.getWriter()
			.write(new ObjectMapper().writeValueAsString(BaseResponse.from(false, ACCESS_ROLE_DENIED_ERROR_MESSAGE)));
	}
}

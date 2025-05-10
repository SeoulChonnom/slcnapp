package com.seoulchonnom.slcnapp.common.entrypoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seoulchonnom.slcnapp.common.dto.BaseResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static com.seoulchonnom.slcnapp.user.UserConstant.ACCESS_ROLE_MISSING_ERROR_MESSAGE;

@Component
@Slf4j
public class CommonAuthenticationEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        log.info(authException.getLocalizedMessage());

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setContentType("text/json;charset=UTF-8");
        response.setStatus(HttpStatus.FORBIDDEN.value());

        response.getWriter()
                .write(new ObjectMapper().writeValueAsString(BaseResponse.from(false, ACCESS_ROLE_MISSING_ERROR_MESSAGE)));
    }
}

package com.seoulchonnom.boot.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.seoulchonnom.auth.constant.AuthConstant;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class SwaggerConfig {
	private static final String ACCESS_TOKEN_SECURITY_SCHEME = "X-AUTH-TOKEN";

	@Bean
	public OpenAPI customOpenAPI() {
		return new OpenAPI()
			.components(new Components()
				.addSecuritySchemes(ACCESS_TOKEN_SECURITY_SCHEME, new SecurityScheme()
					.type(SecurityScheme.Type.APIKEY)
					.in(SecurityScheme.In.HEADER)
					.name(AuthConstant.ACCESS_TOKEN_HEADER_NAME)
					.description("AccessToken")))
			.info(apiInfo());
	}

	private Info apiInfo() {
		return new Info()
			.title("SLCN API Test")
			.description("SLCN API description")
			.version("1.0.o");
	}
}

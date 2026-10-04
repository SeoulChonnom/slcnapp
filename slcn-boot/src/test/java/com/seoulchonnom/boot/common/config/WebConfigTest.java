package com.seoulchonnom.boot.common.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * 프론트가 쓰는 메서드가 CORS preflight를 통과하는지 확인한다.
 * 허용 목록에서 메서드가 빠지면 브라우저는 실제 요청을 보내지 않고 403으로 끝난다.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = {WebConfig.class, WebConfigTest.TestConfig.class})
class WebConfigTest {
	private static final String FRONT_ORIGIN = "http://localhost:5173";
	private static final String RESOURCE_PATH = "/inspection-visits/INSPECTION_VISIT-0001/status";

	private MockMvc mockMvc;

	@Autowired
	private WebApplicationContext webApplicationContext;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
	}

	@ParameterizedTest
	@ValueSource(strings = {"GET", "POST", "PUT", "PATCH", "DELETE"})
	void preflight_fromFrontOrigin_shouldAllowMethod(String method) throws Exception {
		mockMvc.perform(options(RESOURCE_PATH)
				.header(HttpHeaders.ORIGIN, FRONT_ORIGIN)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONT_ORIGIN))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString(method)));
	}

	@Configuration
	@EnableWebMvc
	static class TestConfig {
		@Bean
		TestController testController() {
			return new TestController();
		}
	}

	@RestController
	static class TestController {
		@GetMapping(RESOURCE_PATH)
		ResponseEntity<Void> get() {
			return ResponseEntity.ok().build();
		}

		@PostMapping(RESOURCE_PATH)
		ResponseEntity<Void> post() {
			return ResponseEntity.ok().build();
		}

		@PutMapping(RESOURCE_PATH)
		ResponseEntity<Void> put() {
			return ResponseEntity.ok().build();
		}

		@PatchMapping(RESOURCE_PATH)
		ResponseEntity<Void> patch() {
			return ResponseEntity.ok().build();
		}

		@DeleteMapping(RESOURCE_PATH)
		ResponseEntity<Void> delete() {
			return ResponseEntity.ok().build();
		}
	}
}

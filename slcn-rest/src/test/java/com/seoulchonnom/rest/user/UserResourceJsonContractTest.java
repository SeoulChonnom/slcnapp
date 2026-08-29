package com.seoulchonnom.rest.user;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.seoulchonnom.auth.flow.UserFlow;
import com.seoulchonnom.auth.flow.vo.TokenSessionVo;
import com.seoulchonnom.auth.flow.vo.UserProfileSessionVo;
import com.seoulchonnom.auth.store.projection.UserDetail;
import com.seoulchonnom.rest.common.handler.CommonExceptionHandler;
import com.seoulchonnom.spec.user.entity.User;
import com.seoulchonnom.spec.user.facade.sdo.TokenRdo;
import com.seoulchonnom.spec.user.facade.sdo.UserProfileRdo;
import com.seoulchonnom.spec.user.facade.sdo.UserProfileUdo;

class UserResourceJsonContractTest {
	private UserFlow userFlow;
	private MockMvc mockMvc;
	private LocalValidatorFactoryBean validator;

	@BeforeEach
	void setUp() {
		userFlow = mock(UserFlow.class);
		UserResource userResource = new UserResource(userFlow);
		ReflectionTestUtils.setField(userResource, "refreshCookieMaxAge", 1209600L);
		ReflectionTestUtils.setField(userResource, "refreshCookieSecure", false);
		ReflectionTestUtils.setField(userResource, "refreshCookieSameSite", "Lax");
		ReflectionTestUtils.setField(userResource, "contextPath", "/api");
		validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		mockMvc = MockMvcBuilders.standaloneSetup(userResource)
			.setControllerAdvice(new CommonExceptionHandler())
			.setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
			.setValidator(validator)
			.build();
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
			new UserDetail(currentUser()), "", List.of()));
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void getCurrentUser_shouldUseAuthenticatedUserIdAndExcludePassword() throws Exception {
		when(userFlow.getCurrentUser("USER-0001"))
			.thenReturn(UserProfileRdo.builder().username("tester").name("테스터").build());

		mockMvc.perform(get("/users/me"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("tester"))
			.andExpect(jsonPath("$.name").value("테스터"))
			.andExpect(jsonPath("$.password").doesNotExist())
			.andExpect(jsonPath("$.accessToken").doesNotExist());

		verify(userFlow).getCurrentUser("USER-0001");
	}

	@Test
	void verifyCurrentUserPassword_shouldBindRequestAndReturnNoContent() throws Exception {
		mockMvc.perform(post("/users/me/password/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"password\":\"Password1!\"}"))
			.andExpect(status().isNoContent());

		ArgumentCaptor<com.seoulchonnom.spec.user.facade.sdo.UserPasswordVerifyCdo> captor =
			ArgumentCaptor.forClass(com.seoulchonnom.spec.user.facade.sdo.UserPasswordVerifyCdo.class);
		verify(userFlow).verifyCurrentUserPassword(eq("USER-0001"), captor.capture());
		org.junit.jupiter.api.Assertions.assertEquals("Password1!", captor.getValue().getPassword());
	}

	@Test
	void verifyCurrentUserPassword_withoutPassword_shouldReturnBadRequest() throws Exception {
		mockMvc.perform(post("/users/me/password/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("password는 필수입니다."));

		verifyNoInteractions(userFlow);
	}

	@Test
	void updateCurrentUser_shouldBindOnlyAllowedFieldsAndExcludePasswordsFromResponse() throws Exception {
		when(userFlow.updateCurrentUser(eq("USER-0001"), any(UserProfileUdo.class)))
			.thenReturn(new UserProfileSessionVo(
				UserProfileRdo.builder().username("tester").name("수정된 이름").build(),
				new TokenSessionVo("new-session", TokenRdo.builder()
					.refreshToken("new-refresh")
					.build())
			));

		mockMvc.perform(put("/users/me")
				.contentType(MediaType.APPLICATION_JSON)
					.content("""
						{
						  "name": "수정된 이름",
						  "newPassword": "Password2!",
					  "profileImageFileId": "FILE-0001",
					  "userId": "USER-OTHER"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("tester"))
			.andExpect(jsonPath("$.name").value("수정된 이름"))
			.andExpect(jsonPath("$.password").doesNotExist())
			.andExpect(jsonPath("$.currentPassword").doesNotExist())
			.andExpect(jsonPath("$.newPassword").doesNotExist())
			.andExpect(header().doesNotExist("X-AUTH-TOKEN"))
			.andExpect(header().stringValues(HttpHeaders.SET_COOKIE,
				org.hamcrest.Matchers.hasItems(
					org.hamcrest.Matchers.allOf(
						org.hamcrest.Matchers.containsString("refreshToken=new-refresh"),
						org.hamcrest.Matchers.containsString("Max-Age=1209600")
					),
					org.hamcrest.Matchers.allOf(
						org.hamcrest.Matchers.containsString("sessionId=new-session"),
						org.hamcrest.Matchers.containsString("Max-Age=1209600")
					)
				)));

		ArgumentCaptor<UserProfileUdo> captor = ArgumentCaptor.forClass(UserProfileUdo.class);
		verify(userFlow).updateCurrentUser(eq("USER-0001"), captor.capture());
		org.junit.jupiter.api.Assertions.assertEquals("수정된 이름", captor.getValue().getName());
		org.junit.jupiter.api.Assertions.assertEquals("Password2!", captor.getValue().getNewPassword());
		org.junit.jupiter.api.Assertions.assertEquals("FILE-0001", captor.getValue().getProfileImageFileId());
	}

	private User currentUser() {
		User user = User.builder()
			.username("tester")
			.name("테스터")
			.password("encoded-password")
			.authorityList(List.of())
			.build();
		user.setId("USER-0001");
		return user;
	}
}

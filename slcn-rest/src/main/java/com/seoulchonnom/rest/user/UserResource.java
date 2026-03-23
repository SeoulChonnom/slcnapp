package com.seoulchonnom.rest.user;

import static com.seoulchonnom.spec.user.constant.UserConstant.*;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seoulchonnom.aggregate.user.logic.UserLogic;
import com.seoulchonnom.spec.user.facade.UserFacade;
import com.seoulchonnom.spec.user.facade.sdo.UserCdo;
import com.seoulchonnom.spec.user.facade.sdo.UserLoginCdo;
import com.seoulchonnom.spec.user.facade.sdo.UserRdo;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserResource implements UserFacade {

	private final UserLogic userLogic;

	@Override
	@PostMapping("/register")
	public ResponseEntity<String> registerUser(@RequestBody UserCdo userCdo) {
		userLogic.registerUser(userCdo);
		return new ResponseEntity<>(USER_REGISTER_SUCCESS_MESSAGE, HttpStatus.OK);
	}

	@Override
	@PostMapping("/login")
	public ResponseEntity<UserRdo> loginUser(HttpServletResponse response, @RequestBody UserLoginCdo userLoginCdo) {
		
		return new ResponseEntity<>(null);
	}

	@Override
	@GetMapping("/token")
	public ResponseEntity<UserRdo> reissueToken(@CookieValue("refreshToken") String refreshToken,
		HttpServletResponse response) {
		return new ResponseEntity<>(null);
	}
}

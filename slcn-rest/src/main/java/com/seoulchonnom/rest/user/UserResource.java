package com.seoulchonnom.rest.user;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.seoulchonnom.spec.user.facade.UserFacade;
import com.seoulchonnom.spec.user.facade.sdo.UserCdo;
import com.seoulchonnom.spec.user.facade.sdo.UserLoginCdo;
import com.seoulchonnom.spec.user.facade.sdo.UserRdo;

import jakarta.servlet.http.HttpServletResponse;

@RestController
public class UserResource implements UserFacade {
	@Override
	public ResponseEntity<Void> registerUser(UserCdo userCdo) {
		return new ResponseEntity<>(null);
	}

	@Override
	public ResponseEntity<UserRdo> loginUser(HttpServletResponse response, UserLoginCdo userLoginCdo) {
		return new ResponseEntity<>(null);
	}

	@Override
	public ResponseEntity<UserRdo> reissueToken(String refreshToken, HttpServletResponse response) {
		return new ResponseEntity<>(null);
	}
}

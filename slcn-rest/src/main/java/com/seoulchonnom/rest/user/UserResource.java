package com.seoulchonnom.rest.user;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seoulchonnom.spec.user.facade.UserFacade;
import com.seoulchonnom.spec.user.facade.sdo.UserCdo;
import com.seoulchonnom.spec.user.facade.sdo.UserLoginCdo;
import com.seoulchonnom.spec.user.facade.sdo.UserRdo;

import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/user")
public class UserResource implements UserFacade {

	@Override
	@PostMapping("/register")
	public ResponseEntity<Void> registerUser(@RequestBody UserCdo userCdo) {
		return new ResponseEntity<>(null);
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

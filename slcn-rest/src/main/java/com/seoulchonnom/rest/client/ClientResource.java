package com.seoulchonnom.rest.client;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seoulchonnom.auth.flow.ClientFlow;
import com.seoulchonnom.spec.client.facade.ClientFacade;
import com.seoulchonnom.spec.client.facade.sdo.ClientTokenCdo;
import com.seoulchonnom.spec.client.facade.sdo.ClientTokenRdo;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/clients")
@RequiredArgsConstructor
public class ClientResource implements ClientFacade {

	private final ClientFlow clientFlow;

	@Override
	@PostMapping("/token")
	public ResponseEntity<ClientTokenRdo> issueClientToken(@RequestBody ClientTokenCdo clientTokenCdo) {
		return new ResponseEntity<>(clientFlow.issueToken(clientTokenCdo), HttpStatus.OK);
	}
}

package com.seoulchonnom.aggregate.client.store;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.seoulchonnom.aggregate.client.exception.InvalidClientException;
import com.seoulchonnom.aggregate.client.store.mapper.ClientJpoMapper;
import com.seoulchonnom.aggregate.client.store.repository.ClientRepository;
import com.seoulchonnom.spec.client.entity.Client;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClientStore {
	private final ClientRepository clientRepository;
	private final ClientJpoMapper clientJpoMapper;

	public Client findByClientId(String clientId) {
		return clientJpoMapper.toDomain(clientRepository.findByClientId(clientId)
			.orElseThrow(InvalidClientException::new));
	}
}

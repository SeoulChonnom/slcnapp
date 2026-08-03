package com.seoulchonnom.aggregate.client.store.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.client.store.jpo.ClientJpo;

@Repository
public interface ClientRepository extends JpaRepository<ClientJpo, String> {
	Optional<ClientJpo> findByClientId(String clientId);
}

package com.seoulchonnom.slcnapp.user.repository;

import java.util.Optional;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.slcnapp.user.domain.RefreshToken;

@Repository
public interface RefreshTokenRepository extends CrudRepository<RefreshToken, Integer> {

	Optional<RefreshToken> findByToken(String token);

}

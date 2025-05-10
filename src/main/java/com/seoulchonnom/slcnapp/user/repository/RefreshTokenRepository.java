package com.seoulchonnom.slcnapp.user.repository;

import com.seoulchonnom.slcnapp.user.domain.RefreshToken;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends CrudRepository<RefreshToken, Integer> {

	Optional<RefreshToken> findByToken(String token);

}

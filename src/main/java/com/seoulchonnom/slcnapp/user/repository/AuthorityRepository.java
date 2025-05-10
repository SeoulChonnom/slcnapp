package com.seoulchonnom.slcnapp.user.repository;

import com.seoulchonnom.slcnapp.user.domain.Authority;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthorityRepository extends JpaRepository<Authority, Integer> {

}

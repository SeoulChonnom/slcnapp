package com.seoulchonnom.slcnapp.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.seoulchonnom.slcnapp.user.domain.Authority;

public interface AuthorityRepository extends JpaRepository<Authority, Integer> {

}

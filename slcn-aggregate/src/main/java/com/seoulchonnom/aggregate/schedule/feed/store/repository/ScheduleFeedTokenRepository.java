package com.seoulchonnom.aggregate.schedule.feed.store.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.seoulchonnom.aggregate.schedule.feed.store.jpo.ScheduleFeedTokenJpo;

public interface ScheduleFeedTokenRepository extends JpaRepository<ScheduleFeedTokenJpo, String> {
	Optional<ScheduleFeedTokenJpo> findByTokenHash(String tokenHash);
}

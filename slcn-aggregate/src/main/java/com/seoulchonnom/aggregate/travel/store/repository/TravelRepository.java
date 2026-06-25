package com.seoulchonnom.aggregate.travel.store.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.seoulchonnom.aggregate.travel.store.jpo.TravelJpo;

public interface TravelRepository extends JpaRepository<TravelJpo, String> {
	List<TravelJpo> findAllByHiddenFalseOrderByStartDateDesc();
}

package com.seoulchonnom.aggregate.house.store.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.house.store.jpo.HouseComplexJpo;

@Repository
public interface HouseComplexRepository extends JpaRepository<HouseComplexJpo, String> {
	List<HouseComplexJpo> findByAptSeqIn(Collection<String> aptSeqs);
}

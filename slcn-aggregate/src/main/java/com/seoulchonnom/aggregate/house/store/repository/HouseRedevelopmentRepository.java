package com.seoulchonnom.aggregate.house.store.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.house.store.jpo.HouseRedevelopmentJpo;
import com.seoulchonnom.spec.house.entity.vo.GeocodeStatus;

/**
 * 정비사업은 수백 건이라 전체 조회·JPA 저장을 그대로 쓴다. 쿼리는 모두 메서드 이름에서 만들어지는 것이다.
 */
@Repository
public interface HouseRedevelopmentRepository extends JpaRepository<HouseRedevelopmentJpo, String> {
	long countByGeocodeStatus(GeocodeStatus status);

	long countByGeocodeStatusIn(Collection<GeocodeStatus> statuses);

	List<HouseRedevelopmentJpo> findByGeocodeStatusOrderByLawdCdAscSourceKeyAsc(GeocodeStatus status);

	List<HouseRedevelopmentJpo> findByGeocodeStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(GeocodeStatus status);

	List<HouseRedevelopmentJpo> findByGeocodeStatusInOrderByLawdCdAscNameAscSourceKeyAsc(
		Collection<GeocodeStatus> statuses, Pageable pageable);
}

package com.seoulchonnom.aggregate.travel.store.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.seoulchonnom.aggregate.travel.store.jpo.TravelPhotoJpo;

public interface TravelPhotoRepository extends JpaRepository<TravelPhotoJpo, String> {
	List<TravelPhotoJpo> findAllByTravelIdOrderByTravelDayIdAscTravelPlaceIdAscSortOrderAsc(String travelId);

	List<TravelPhotoJpo> findAllByTravelIdAndTravelDayIdOrderByTravelPlaceIdAscSortOrderAsc(String travelId,
		String travelDayId);

	List<TravelPhotoJpo> findAllByTravelIdAndTravelPlaceIdOrderBySortOrderAsc(String travelId, String travelPlaceId);

	List<TravelPhotoJpo> findAllByTravelIdAndTravelDayIdIsNullAndTravelPlaceIdIsNull(String travelId);

	List<TravelPhotoJpo> findAllByTravelDayIdIn(Collection<String> travelDayIds);

	List<TravelPhotoJpo> findAllByTravelPlaceIdIn(Collection<String> travelPlaceIds);

	boolean existsByTravelDayIdIn(Collection<String> travelDayIds);

	boolean existsByTravelIdAndTravelDayIdIsNullAndTravelPlaceIdIsNullAndPhotoFileId(String travelId,
		String photoFileId);

	boolean existsByTravelIdAndTravelDayIdAndTravelPlaceIdIsNullAndPhotoFileId(String travelId, String travelDayId,
		String photoFileId);

	boolean existsByTravelIdAndTravelDayIdAndTravelPlaceIdAndPhotoFileId(String travelId, String travelDayId,
		String travelPlaceId, String photoFileId);

	int countByTravelDayIdAndTravelPlaceId(String travelDayId, String travelPlaceId);

	int countByTravelIdAndTravelDayIdIsNullAndTravelPlaceIdIsNull(String travelId);

	int countByTravelDayIdAndTravelPlaceIdIsNull(String travelDayId);
}

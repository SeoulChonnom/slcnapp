package com.seoulchonnom.aggregate.travel.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.aggregate.travel.exception.TravelPeriodConflictException;
import com.seoulchonnom.aggregate.travel.store.TravelStore;
import com.seoulchonnom.spec.travel.entity.Travel;
import com.seoulchonnom.spec.travel.entity.TravelDay;
import com.seoulchonnom.spec.travel.entity.TravelPhoto;
import com.seoulchonnom.spec.travel.entity.TravelPlace;
import com.seoulchonnom.spec.travel.entity.vo.TravelPlaceCategory;
import com.seoulchonnom.spec.travel.entity.TravelReview;
import com.seoulchonnom.spec.travel.entity.TravelTag;
import com.seoulchonnom.spec.travel.facade.sdo.TravelCdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelDayUdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelDetailRdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelPhotoCdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelPlaceUdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelReviewUdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelTagCdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelUdo;
import com.seoulchonnom.spec.travel.mapper.TravelMapper;

class TravelLogicTest {
	private final TravelStore travelStore = mock(TravelStore.class);
	private final TravelMapper travelMapper = mock(TravelMapper.class);
	private final TravelLogic travelLogic = new TravelLogic(travelStore, travelMapper);

	@Test
	void registerTravel_shouldCreateDaysForOvernightTrip() {
		TravelCdo cdo = new TravelCdo("서울", "서울", "2026-06-01", "2026-06-02", "cover-1", List.of("맛집"));
		when(travelStore.save(any(Travel.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(travelStore.findById(anyString())).thenAnswer(invocation ->
			Travel.builder().title("서울").startDate(LocalDate.of(2026, 6, 1)).endDate(LocalDate.of(2026, 6, 2)).build());
		when(travelMapper.toTravelDetailRdo(any(), anyList(), anyList(), anyList(), anyList(), any()))
			.thenReturn(new TravelDetailRdo());

		travelLogic.registerTravel(cdo);

		ArgumentCaptor<List<TravelDay>> daysCaptor = ArgumentCaptor.forClass(List.class);
		verify(travelStore).saveDays(daysCaptor.capture());
		assertThat(daysCaptor.getValue()).hasSize(2);
		assertThat(daysCaptor.getValue()).extracting(TravelDay::getDayNumber).containsExactly(1, 2);
		verify(travelStore).deleteTagsByTravelId(anyString());
		verify(travelStore).save(any(TravelTag.class));
	}

	@Test
	void registerTravel_shouldRejectSameDayTrip() {
		TravelCdo cdo = new TravelCdo("서울", "서울", "2026-06-01", "2026-06-01", "cover-1", null);

		assertThatThrownBy(() -> travelLogic.registerTravel(cdo))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("1박 이상 여행만 등록할 수 있습니다.");
	}

	@Test
	void registerTravel_shouldRejectBlankRegion() {
		TravelCdo cdo = new TravelCdo("서울", " ", "2026-06-01", "2026-06-02", "cover-1", null);

		assertThatThrownBy(() -> travelLogic.registerTravel(cdo))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("region은 필수입니다.");
	}

	@Test
	void registerTravel_shouldTrimRegion() {
		TravelCdo cdo = new TravelCdo("서울", "  서울  ", "2026-06-01", "2026-06-02", "cover-1", null);
		when(travelStore.save(any(Travel.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(travelStore.findById(anyString())).thenAnswer(invocation ->
			Travel.builder().title("서울").region("서울").startDate(LocalDate.of(2026, 6, 1))
				.endDate(LocalDate.of(2026, 6, 2)).build());
		when(travelMapper.toTravelDetailRdo(any(), anyList(), anyList(), anyList(), anyList(), any()))
			.thenReturn(new TravelDetailRdo());

		travelLogic.registerTravel(cdo);

		ArgumentCaptor<Travel> travelCaptor = ArgumentCaptor.forClass(Travel.class);
		verify(travelStore, atLeastOnce()).save(travelCaptor.capture());
		assertThat(travelCaptor.getAllValues().get(0).getRegion()).isEqualTo("서울");
	}

	@Test
	void registerTravel_shouldSyncNestedDaysPlacesPhotosAndReview() {
		TravelCdo cdo = new TravelCdo("강릉", "강릉", "2026-06-01", "2026-06-02", "cover-1", List.of("바다"));
		TravelDayUdo dayUdo = new TravelDayUdo("1일차", "바다 산책", "day-cover", 1);
		dayUdo.setPhotos(List.of(new TravelPhotoCdo(null, null, "day-file", "날짜 사진", 1)));
		TravelPlaceUdo placeUdo = new TravelPlaceUdo();
		placeUdo.setName("안목해변");
		placeUdo.setCategory(TravelPlaceCategory.TOURIST_SPOT);
		placeUdo.setDescription("해변");
		placeUdo.setCoverPhotoId("place-cover");
		placeUdo.setPhotos(List.of(new TravelPhotoCdo(null, null, "place-file", "장소 사진", 1)));
		dayUdo.setPlaces(List.of(placeUdo));
		cdo.setTravelDays(List.of(dayUdo));
		cdo.setPhotos(List.of(new TravelPhotoCdo(null, null, "travel-file", "전체 사진", 1)));
		cdo.setReview(new TravelReviewUdo(null, "한줄", "좋음", "아쉬움", "안목해변", "좋았다"));
		TravelDay day1 = day("day-1", "travel-1", "2026-06-01", 1);
		TravelDay day2 = day("day-2", "travel-1", "2026-06-02", 2);
		when(travelStore.save(any(Travel.class))).thenAnswer(invocation -> {
			Travel travel = invocation.getArgument(0);
			travel.setId("travel-1");
			return travel;
		});
		when(travelStore.findById("travel-1")).thenReturn(travel("travel-1", "강릉", "2026-06-01", "2026-06-02"));
		when(travelStore.findDaysByTravelId("travel-1")).thenReturn(List.of(day1, day2));
		when(travelStore.save(any(TravelDay.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(travelStore.save(any(TravelPlace.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(travelStore.save(any(TravelPhoto.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(travelStore.save(any(TravelTag.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(travelStore.save(any(TravelReview.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(travelStore.findReviewByTravelId("travel-1")).thenReturn(Optional.empty());
		when(travelMapper.toTravelDetailRdo(any(), anyList(), anyList(), anyList(), anyList(), any()))
			.thenReturn(new TravelDetailRdo());

		travelLogic.registerTravel(cdo);

		verify(travelStore).deletePhotosByTravelId("travel-1");
		verify(travelStore).deletePlacesByDayIds(List.of("day-1", "day-2"));
		verify(travelStore, times(3)).save(any(TravelPhoto.class));
		verify(travelStore).save(any(TravelPlace.class));
		verify(travelStore).save(any(TravelReview.class));
	}

	@Test
	void getTravel_shouldBulkLoadChildrenByTravelId() {
		Travel travel = travel("travel-1", "서울", "2026-06-01", "2026-06-02");
		when(travelStore.findById("travel-1")).thenReturn(travel);
		when(travelStore.findReviewByTravelId("travel-1")).thenReturn(Optional.empty());
		TravelDetailRdo detailRdo = new TravelDetailRdo();
		when(travelMapper.toTravelDetailRdo(any(), anyList(), anyList(), anyList(), anyList(), any()))
			.thenReturn(detailRdo);

		TravelDetailRdo result = travelLogic.getTravel("travel-1");

		assertThat(result).isSameAs(detailRdo);
		verify(travelStore).findDaysByTravelId("travel-1");
		verify(travelStore).findPlacesByTravelId("travel-1");
		verify(travelStore).findPhotosByTravelId("travel-1");
		verify(travelStore).findTagsByTravelId("travel-1");
		verify(travelStore).findReviewByTravelId("travel-1");
	}

	@Test
	void modifyTravel_shouldIncreasePeriodAndAppendMissingDays() {
		Travel travel = travel("travel-1", "서울", "2026-06-01", "2026-06-02");
		TravelDay day1 = day("day-1", "travel-1", "2026-06-01", 1);
		TravelDay day2 = day("day-2", "travel-1", "2026-06-02", 2);
		when(travelStore.findById("travel-1")).thenReturn(travel);
		when(travelStore.findDaysByTravelId("travel-1")).thenReturn(List.of(day1, day2));
		when(travelStore.findReviewByTravelId("travel-1")).thenReturn(Optional.empty());
		when(travelMapper.toTravelDetailRdo(any(), anyList(), anyList(), anyList(), anyList(), any()))
			.thenReturn(new TravelDetailRdo());

		travelLogic.modifyTravel("travel-1", new TravelUdo("서울", "서울", "2026-06-01", "2026-06-03",
			"cover-1", null, null));

		ArgumentCaptor<List<TravelDay>> daysCaptor = ArgumentCaptor.forClass(List.class);
		verify(travelStore).saveDays(daysCaptor.capture());
		assertThat(daysCaptor.getValue()).hasSize(3);
		assertThat(daysCaptor.getValue()).extracting(TravelDay::getDayNumber).containsExactly(1, 2, 3);
		assertThat(travel.getEndDate()).isEqualTo(LocalDate.of(2026, 6, 3));
	}

	@Test
	void modifyTravel_shouldConflictWhenShrinkingWrittenDaysWithoutConfirm() {
		Travel travel = travel("travel-1", "서울", "2026-06-01", "2026-06-03");
		TravelDay day1 = day("day-1", "travel-1", "2026-06-01", 1);
		TravelDay day2 = day("day-2", "travel-1", "2026-06-02", 2);
		TravelDay day3 = day("day-3", "travel-1", "2026-06-03", 3);
		when(travelStore.findById("travel-1")).thenReturn(travel);
		when(travelStore.findDaysByTravelId("travel-1")).thenReturn(List.of(day1, day2, day3));
		when(travelStore.existsPlaceByDayIds(List.of("day-3"))).thenReturn(true);

		assertThatThrownBy(() -> travelLogic.modifyTravel("travel-1",
			new TravelUdo("서울", "서울", "2026-06-01", "2026-06-02", "cover-1", null, null)))
			.isInstanceOf(TravelPeriodConflictException.class)
			.hasMessageContaining("2026-06-03");
	}

	@Test
	void modifyTravel_shouldDeleteShrunkDayContentWhenConfirmed() {
		Travel travel = travel("travel-1", "서울", "2026-06-01", "2026-06-03");
		TravelDay day1 = day("day-1", "travel-1", "2026-06-01", 1);
		TravelDay day2 = day("day-2", "travel-1", "2026-06-02", 2);
		TravelDay day3 = day("day-3", "travel-1", "2026-06-03", 3);
		when(travelStore.findById("travel-1")).thenReturn(travel);
		when(travelStore.findDaysByTravelId("travel-1")).thenReturn(List.of(day1, day2, day3));
		when(travelStore.findReviewByTravelId("travel-1")).thenReturn(Optional.empty());
		when(travelMapper.toTravelDetailRdo(any(), anyList(), anyList(), anyList(), anyList(), any()))
			.thenReturn(new TravelDetailRdo());

		travelLogic.modifyTravel("travel-1", new TravelUdo("서울", "서울", "2026-06-01", "2026-06-02",
			"cover-1", null, true));

		verify(travelStore).deletePhotosByDayIds(List.of("day-3"));
		verify(travelStore).deletePlacesByDayIds(List.of("day-3"));
		verify(travelStore).deleteDays(List.of(day3));
	}

	@Test
	void modifyTravel_shouldConflictWhenShrinkingDayWithOwnContentWithoutConfirm() {
		Travel travel = travel("travel-1", "서울", "2026-06-01", "2026-06-03");
		TravelDay day1 = day("day-1", "travel-1", "2026-06-01", 1);
		TravelDay day2 = day("day-2", "travel-1", "2026-06-02", 2);
		TravelDay day3 = day("day-3", "travel-1", "2026-06-03", 3);
		day3.setCoverPhotoId("day-cover");
		when(travelStore.findById("travel-1")).thenReturn(travel);
		when(travelStore.findDaysByTravelId("travel-1")).thenReturn(List.of(day1, day2, day3));

		assertThatThrownBy(() -> travelLogic.modifyTravel("travel-1",
			new TravelUdo("서울", "서울", "2026-06-01", "2026-06-02", "cover-1", null, null)))
			.isInstanceOf(TravelPeriodConflictException.class)
			.hasMessageContaining("2026-06-03");
	}

	@Test
	void modifyTravel_shouldRejectBlankCoverPhotoId() {
		when(travelStore.findById("travel-1")).thenReturn(travel("travel-1", "서울", "2026-06-01", "2026-06-02"));

		assertThatThrownBy(() -> travelLogic.modifyTravel("travel-1",
			new TravelUdo("서울", "서울", "2026-06-01", "2026-06-02", " ", null, null)))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("coverPhotoId는 필수입니다.");
	}

	@Test
	void modifyTravel_shouldRejectBlankRegion() {
		when(travelStore.findById("travel-1")).thenReturn(travel("travel-1", "서울", "2026-06-01", "2026-06-02"));

		assertThatThrownBy(() -> travelLogic.modifyTravel("travel-1",
			new TravelUdo("서울", null, "2026-06-01", "2026-06-02", "cover-1", null, null)))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("region은 필수입니다.");
	}

	@Test
	void modifyTravel_shouldTrimRegion() {
		Travel travel = travel("travel-1", "서울", "2026-06-01", "2026-06-02");
		when(travelStore.findById("travel-1")).thenReturn(travel);
		when(travelStore.findDaysByTravelId("travel-1")).thenReturn(List.of(
			day("day-1", "travel-1", "2026-06-01", 1),
			day("day-2", "travel-1", "2026-06-02", 2)));
		when(travelStore.findReviewByTravelId("travel-1")).thenReturn(Optional.empty());
		when(travelMapper.toTravelDetailRdo(any(), anyList(), anyList(), anyList(), anyList(), any()))
			.thenReturn(new TravelDetailRdo());

		travelLogic.modifyTravel("travel-1", new TravelUdo("서울", "  부산  ", "2026-06-01", "2026-06-02",
			"cover-1", null, null));

		assertThat(travel.getRegion()).isEqualTo("부산");
	}

	@Test
	void modifyTravel_withReviewOnly_shouldNotDeleteExistingPlacesOrPhotos() {
		Travel travel = travel("travel-1", "서울", "2026-06-01", "2026-06-02");
		when(travelStore.findById("travel-1")).thenReturn(travel);
		when(travelStore.findDaysByTravelId("travel-1")).thenReturn(List.of(
			day("day-1", "travel-1", "2026-06-01", 1),
			day("day-2", "travel-1", "2026-06-02", 2)));
		when(travelStore.findReviewByTravelId("travel-1")).thenReturn(Optional.empty());
		when(travelStore.save(any(TravelReview.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(travelMapper.toTravelDetailRdo(any(), anyList(), anyList(), anyList(), anyList(), any()))
			.thenReturn(new TravelDetailRdo());
		TravelUdo udo = new TravelUdo("서울", "서울", "2026-06-01", "2026-06-02", "cover-1", null, null);
		udo.setReview(new TravelReviewUdo(null, "요약", "좋음", "아쉬움", "한강", "좋았음"));

		travelLogic.modifyTravel("travel-1", udo);

		verify(travelStore, never()).deletePhotosByTravelId(anyString());
		verify(travelStore, never()).deleteTravelLevelPhotosByTravelId(anyString());
		verify(travelStore).save(any(TravelReview.class));
	}

	@Test
	void modifyDay_shouldRequireCoverPhotoId() {
		when(travelStore.findById("travel-1")).thenReturn(travel("travel-1", "서울", "2026-06-01", "2026-06-02"));
		when(travelStore.findDayById("day-1")).thenReturn(day("day-1", "travel-1", "2026-06-01", 1));

		assertThatThrownBy(() -> travelLogic.modifyDay("travel-1", "day-1", new TravelDayUdo("제목", "메모", " ", 3)))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("coverPhotoId는 필수입니다.");
	}

	@Test
	void registerTag_shouldTrimName() {
		when(travelStore.findById("travel-1")).thenReturn(travel("travel-1", "서울", "2026-06-01", "2026-06-02"));
		when(travelStore.save(any(TravelTag.class))).thenAnswer(invocation -> invocation.getArgument(0));

		travelLogic.registerTag("travel-1", new TravelTagCdo("  맛집  "));

		ArgumentCaptor<TravelTag> tagCaptor = ArgumentCaptor.forClass(TravelTag.class);
		verify(travelStore).save(tagCaptor.capture());
		assertThat(tagCaptor.getValue().getName()).isEqualTo("맛집");
	}

	@Test
	void registerTag_shouldRejectBlankName() {
		when(travelStore.findById("travel-1")).thenReturn(travel("travel-1", "서울", "2026-06-01", "2026-06-02"));

		assertThatThrownBy(() -> travelLogic.registerTag("travel-1", new TravelTagCdo(" ")))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("name은 필수입니다.");
	}

	@Test
	void registerTag_shouldRejectDuplicateInSameTravel() {
		when(travelStore.findById("travel-1")).thenReturn(travel("travel-1", "서울", "2026-06-01", "2026-06-02"));
		when(travelStore.existsTag("travel-1", "맛집")).thenReturn(true);

		assertThatThrownBy(() -> travelLogic.registerTag("travel-1", new TravelTagCdo("맛집")))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("이미 등록된 태그입니다.");
	}

	@Test
	void registerTag_shouldRejectMoreThanTenTags() {
		when(travelStore.findById("travel-1")).thenReturn(travel("travel-1", "서울", "2026-06-01", "2026-06-02"));
		when(travelStore.countTags("travel-1")).thenReturn(10);

		assertThatThrownBy(() -> travelLogic.registerTag("travel-1", new TravelTagCdo("새태그")))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("태그는 최대 10개까지 등록할 수 있습니다.");
	}

	@Test
	void registerPhoto_shouldRejectDayOwnedByAnotherTravel() {
		when(travelStore.findById("travel-1")).thenReturn(travel("travel-1", "서울", "2026-06-01", "2026-06-02"));
		when(travelStore.findDayById("day-1")).thenReturn(day("day-1", "travel-2", "2026-06-01", 1));

		assertThatThrownBy(() -> travelLogic.registerPhoto("travel-1",
			new TravelPhotoCdo("day-1", null, "file-1", null, null)))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("여행에 속한 일자가 아닙니다.");
	}

	@Test
	void registerPhoto_shouldRejectPlaceNotInDay() {
		when(travelStore.findById("travel-1")).thenReturn(travel("travel-1", "서울", "2026-06-01", "2026-06-02"));
		when(travelStore.findDayById("day-1")).thenReturn(day("day-1", "travel-1", "2026-06-01", 1));
		when(travelStore.findPlaceById("place-1")).thenReturn(place("place-1", "travel-1", "day-2"));

		assertThatThrownBy(() -> travelLogic.registerPhoto("travel-1",
			new TravelPhotoCdo("day-1", "place-1", "file-1", null, null)))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("travelPlaceId가 travelDayId에 속하지 않습니다.");
	}

	@Test
	void registerPhoto_shouldRejectDuplicateTargetAndFile() {
		when(travelStore.findById("travel-1")).thenReturn(travel("travel-1", "서울", "2026-06-01", "2026-06-02"));
		when(travelStore.findDayById("day-1")).thenReturn(day("day-1", "travel-1", "2026-06-01", 1));
		when(travelStore.existsPhotoByTarget("travel-1", "day-1", null, "file-1")).thenReturn(true);

		assertThatThrownBy(() -> travelLogic.registerPhoto("travel-1",
			new TravelPhotoCdo("day-1", null, "file-1", null, null)))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("이미 연결된 사진입니다.");
	}

	@Test
	void registerPhoto_shouldAllowTravelPhotoWithoutDay() {
		when(travelStore.findById("travel-1")).thenReturn(travel("travel-1", "서울", "2026-06-01", "2026-06-02"));
		when(travelStore.save(any(TravelPhoto.class))).thenAnswer(invocation -> invocation.getArgument(0));

		travelLogic.registerPhoto("travel-1", new TravelPhotoCdo(null, null, "file-1", "대표", null));

		ArgumentCaptor<TravelPhoto> photoCaptor = ArgumentCaptor.forClass(TravelPhoto.class);
		verify(travelStore).save(photoCaptor.capture());
		assertThat(photoCaptor.getValue().getTravelDayId()).isNull();
		assertThat(photoCaptor.getValue().getTravelPlaceId()).isNull();
	}

	@Test
	void getPhotoQueries_shouldDelegateToStore() {
		when(travelStore.findById("travel-1")).thenReturn(travel("travel-1", "서울", "2026-06-01", "2026-06-02"));
		when(travelStore.findDayById("day-1")).thenReturn(day("day-1", "travel-1", "2026-06-01", 1));
		when(travelStore.findPlaceById("place-1")).thenReturn(place("place-1", "travel-1", "day-1"));

		travelLogic.getPhotos("travel-1");
		travelLogic.getDayPhotos("travel-1", "day-1");
		travelLogic.getPlacePhotos("travel-1", "place-1");

		verify(travelStore).findPhotosByTravelId("travel-1");
		verify(travelStore).findPhotosByTravelIdAndDayId("travel-1", "day-1");
		verify(travelStore).findPhotosByTravelIdAndPlaceId("travel-1", "place-1");
	}

	@Test
	void putReview_shouldUpsertExistingReview() {
		TravelReview review = new TravelReview("travel-1", "기존");
		when(travelStore.findById("travel-1")).thenReturn(travel("travel-1", "서울", "2026-06-01", "2026-06-02"));
		when(travelStore.findReviewByTravelId("travel-1")).thenReturn(Optional.of(review));
		when(travelStore.save(review)).thenReturn(review);

		travelLogic.putReview("travel-1", new TravelReviewUdo(null, "요약", "좋음", "아쉬움", "한강", "좋았음"));

		assertThat(review.getOneLineSummary()).isEqualTo("요약");
		assertThat(review.getFinalReview()).isEqualTo("좋았음");
		verify(travelStore).save(review);
	}

	private Travel travel(String id, String title, String startDate, String endDate) {
		Travel travel = Travel.builder()
			.title(title)
			.region("서울")
			.startDate(LocalDate.parse(startDate))
			.endDate(LocalDate.parse(endDate))
			.coverPhotoId("cover-1")
			.build();
		travel.setId(id);
		return travel;
	}

	private TravelDay day(String id, String travelId, String date, int dayNumber) {
		TravelDay travelDay = TravelDay.builder()
			.travelId(travelId)
			.date(LocalDate.parse(date))
			.dayNumber(dayNumber)
			.sortOrder(dayNumber)
			.build();
		travelDay.setId(id);
		return travelDay;
	}

	private TravelPlace place(String id, String travelId, String travelDayId) {
		TravelPlace travelPlace = TravelPlace.builder()
			.travelId(travelId)
			.travelDayId(travelDayId)
			.name("장소")
			.category(TravelPlaceCategory.ETC)
			.build();
		travelPlace.setId(id);
		return travelPlace;
	}
}

package com.seoulchonnom.spec.travel.mapper;

import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

@SuppressWarnings("unchecked")
class TravelMapperTest {
	private static Object enumValue(String className, String name) throws Exception {
		return Enum.valueOf(enumType(className), name);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Class<Enum> enumType(String className) throws Exception {
		return (Class<Enum>)Class.forName(className);
	}

	private static Object newInstance(String className) throws Exception {
		return Class.forName(className).getDeclaredConstructor().newInstance();
	}

	private static Object invoke(Object target, String methodName, Object... args) throws Exception {
		for (Method method : target.getClass().getMethods()) {
			if (method.getName().equals(methodName) && method.getParameterCount() == args.length &&
				isCompatible(method.getParameterTypes(), args)) {
				return method.invoke(target, args);
			}
		}
		fail("No method named %s with %d argument(s) found on %s", methodName, args.length,
			target.getClass().getName());
		return null;
	}

	private static boolean isCompatible(Class<?>[] parameterTypes, Object[] args) {
		for (int i = 0; i < parameterTypes.length; i++) {
			if (args[i] != null && !wrap(parameterTypes[i]).isInstance(args[i])) {
				return false;
			}
		}
		return true;
	}

	private static Object get(Object target, String propertyName) throws Exception {
		String methodName = "get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
		return target.getClass().getMethod(methodName).invoke(target);
	}

	private static void set(Object target, String propertyName, Object value) throws Exception {
		Method setter = findSetter(target.getClass(), propertyName, value);
		setter.invoke(target, value);
	}

	private static Method findSetter(Class<?> type, String propertyName, Object value) {
		String methodName = "set" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
		for (Method method : type.getMethods()) {
			if (!method.getName().equals(methodName) || method.getParameterCount() != 1) {
				continue;
			}
			if (value == null || wrap(method.getParameterTypes()[0]).isInstance(value)) {
				return method;
			}
		}
		fail("No setter named %s found on %s", methodName, type.getName());
		return null;
	}

	private static Class<?> wrap(Class<?> type) {
		if (!type.isPrimitive()) {
			return type;
		}
		if (type == int.class) {
			return Integer.class;
		}
		if (type == boolean.class) {
			return Boolean.class;
		}
		if (type == long.class) {
			return Long.class;
		}
		return type;
	}

	@Test
	void toTravelDetailRdo_shouldAssembleFileBoxItemsIntoNestedCoverAndPhotos() throws Exception {
		Object travel = newInstance("com.seoulchonnom.spec.travel.entity.Travel");
		set(travel, "id", "travel-1");
		set(travel, "title", "서울");
		set(travel, "region", "Seoul");
		set(travel, "startDate", LocalDate.of(2026, 6, 1));
		set(travel, "endDate", LocalDate.of(2026, 6, 2));
		set(travel, "days", List.of(
			travelDay(LocalDate.of(2026, 6, 1), "첫째 날", List.of(travelPlace("place-1", "광화문"))),
			travelDay(LocalDate.of(2026, 6, 2), "둘째 날", List.of())));
		set(travel, "tags", List.of("가족", "도보"));
		set(travel, "review", travelReview("한 줄 후기"));
		Object travelCover = fileItemRdo("file-item-1", "file-1", "TRAVEL", null, "COVER", 1);
		Object travelGallery = fileItemRdo("file-item-2", "file-2", "TRAVEL", null, "GALLERY", 2);
		Object dayCover = fileItemRdo("file-item-3", "file-3", "TRAVEL_DAY", "2026-06-01", "COVER", 1);
		Object dayGallery = fileItemRdo("file-item-4", "file-4", "TRAVEL_DAY", "2026-06-01", "GALLERY", 2);
		Object placeCover = fileItemRdo("file-item-5", "file-5", "TRAVEL_PLACE", "place-1", "COVER", 1);
		Object placeGallery = fileItemRdo("file-item-6", "file-6", "TRAVEL_PLACE", "place-1", "GALLERY", 2);

		Object detailRdo = invoke(newMapper(), "toTravelDetailRdo", travel,
			List.of(travelCover, travelGallery, dayCover, dayGallery, placeCover, placeGallery));

		assertThat(get(detailRdo, "cover")).isSameAs(travelCover);
		assertThat((List<Object>)get(detailRdo, "files"))
			.containsExactly(travelCover, travelGallery, dayCover, dayGallery, placeCover, placeGallery);
		assertThat(get(detailRdo, "tags")).isEqualTo(List.of("가족", "도보"));
		assertThat(get(detailRdo, "oneLineReview")).isEqualTo("한 줄 후기");

		Object firstDayRdo = ((List<?>)get(detailRdo, "travelDays")).get(0);
		assertThat(get(firstDayRdo, "date")).isEqualTo("2026-06-01");
		assertThat(get(firstDayRdo, "cover")).isSameAs(dayCover);
		assertThat(get(firstDayRdo, "photos")).isEqualTo(List.of(dayGallery));

		Object placeRdo = ((List<?>)get(firstDayRdo, "places")).get(0);
		assertThat(get(placeRdo, "placeKey")).isEqualTo("place-1");
		assertThat(get(placeRdo, "cover")).isSameAs(placeCover);
		assertThat(get(placeRdo, "photos")).isEqualTo(List.of(placeGallery));
	}

	@Test
	void toTravelRdo_shouldDeriveSummaryCoverAndOneLineReview() throws Exception {
		Object travel = newInstance("com.seoulchonnom.spec.travel.entity.Travel");
		set(travel, "id", "travel-1");
		set(travel, "title", "서울");
		set(travel, "region", "Seoul");
		set(travel, "startDate", LocalDate.of(2026, 6, 1));
		set(travel, "endDate", LocalDate.of(2026, 6, 3));
		set(travel, "tags", List.of("가족", "도보"));
		set(travel, "review", travelReview("다시 가고 싶은 서울"));
		Object cover = fileItemRdo("file-item-1", "file-1", "TRAVEL", null, "COVER", 1);

		Object rdo = invoke(newMapper(), "toTravelRdo", travel, cover);

		assertThat(get(rdo, "travelId")).isEqualTo("travel-1");
		assertThat(get(rdo, "nights")).isEqualTo(2);
		assertThat(get(rdo, "days")).isEqualTo(3);
		assertThat(get(rdo, "cover")).isSameAs(cover);
		assertThat(get(rdo, "oneLineReview")).isEqualTo("다시 가고 싶은 서울");
		assertThat(get(rdo, "tags")).isEqualTo(List.of("가족", "도보"));
	}

	private Object newMapper() throws Exception {
		return newInstance("com.seoulchonnom.spec.travel.mapper.TravelMapper");
	}

	private Object travelDay(LocalDate date, String title, List<Object> places) throws Exception {
		Object day = newInstance("com.seoulchonnom.spec.travel.entity.vo.TravelDay");
		set(day, "date", date);
		set(day, "title", title);
		set(day, "dayNumber", date.getDayOfMonth());
		set(day, "sortOrder", date.getDayOfMonth());
		set(day, "places", places);
		return day;
	}

	private Object travelPlace(String placeKey, String name) throws Exception {
		Object place = newInstance("com.seoulchonnom.spec.travel.entity.vo.TravelPlace");
		set(place, "placeKey", placeKey);
		set(place, "name", name);
		set(place, "sortOrder", 1);
		return place;
	}

	private Object travelReview(String oneLineSummary) throws Exception {
		Object review = newInstance("com.seoulchonnom.spec.travel.entity.vo.TravelReview");
		set(review, "oneLineSummary", oneLineSummary);
		return review;
	}

	private Object fileItemRdo(String id, String fileAssetId, String targetType, String targetId, String role,
		int sortOrder) throws Exception {
		Object rdo = newInstance("com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemRdo");
		set(rdo, "id", id);
		set(rdo, "fileAssetId", fileAssetId);
		set(rdo, "targetType", enumValue("com.seoulchonnom.spec.filebox.entity.vo.FileBoxTargetType", targetType));
		set(rdo, "targetId", targetId);
		set(rdo, "role", enumValue("com.seoulchonnom.spec.filebox.entity.vo.FileBoxItemRole", role));
		set(rdo, "sortOrder", sortOrder);
		return rdo;
	}
}

package com.seoulchonnom.spec.trip.mapper;

import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.seoulchonnom.spec.file.entity.vo.FileType;
import com.seoulchonnom.spec.file.facade.sdo.FileAssetRdo;
import com.seoulchonnom.spec.trip.entity.vo.Option;
import com.seoulchonnom.spec.trip.entity.vo.Quiz;
import com.seoulchonnom.spec.trip.facade.sdo.OptionCdo;
import com.seoulchonnom.spec.trip.facade.sdo.QuizCdo;
import com.seoulchonnom.spec.trip.facade.sdo.QuizRdo;
import com.seoulchonnom.spec.trip.facade.sdo.QuizResultRdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripDetailRdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripListRdo;

class TripMapperTest {
	private final TripMapper tripMapper = Mappers.getMapper(TripMapper.class);

	private static boolean hasProperty(Class<?> type, String propertyName) {
		String suffix = Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
		for (Method method : type.getMethods()) {
			if (method.getParameterCount() == 0 && method.getName().equals("get" + suffix)) {
				return true;
			}
			if (method.getParameterCount() == 0 && method.getName().equals("is" + suffix)) {
				return true;
			}
		}
		return false;
	}

	private static void set(Object target, String propertyName, Object value) throws Exception {
		String methodName = "set" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
		for (Method method : target.getClass().getMethods()) {
			if (method.getName().equals(methodName) && method.getParameterCount() == 1 &&
				wrap(method.getParameterTypes()[0]).isInstance(value)) {
				method.invoke(target, value);
				return;
			}
		}
		fail("No setter named %s found on %s", methodName, target.getClass().getName());
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
	void tripCdo_shouldExposeFileBoxItemsInsteadOfDirectFileIds() throws Exception {
		Class<?> tripCdoType = Class.forName("com.seoulchonnom.spec.trip.facade.sdo.TripCdo");

		assertThat(hasProperty(tripCdoType, "files")).isTrue();
		assertThat(hasProperty(tripCdoType, "logoFileId")).isFalse();
		assertThat(hasProperty(tripCdoType, "firstMapFileId")).isFalse();
		assertThat(hasProperty(tripCdoType, "secondMapFileId")).isFalse();
	}

	@Test
	void toQuizRdo_shouldPreserveOptionOrder() {
		Quiz quiz = Quiz.builder()
			.title("Quiz Title")
			.options(List.of(
				Option.builder().id("OPT-1").text("first").build(),
				Option.builder().id("OPT-2").text("second").build()))
			.build();

		QuizRdo quizRdo = tripMapper.toQuizRdo(quiz);

		assertThat(quizRdo.getTitle()).isEqualTo("Quiz Title");
		assertThat(quizRdo.getOptions())
			.extracting(option -> option.getId() + ":" + option.getText())
			.containsExactly("OPT-1:first", "OPT-2:second");
	}

	@Test
	void toQuizDetailRdo_shouldReturnAnswerPayloadForCorrectOption() {
		Quiz quiz = Quiz.builder()
			.correctOptionId("OPT-2")
			.answerTitle("정답")
			.answerText("정답 설명")
			.errorTitle("오답")
			.errorText("오답 설명")
			.build();

		QuizResultRdo quizResultRdo = tripMapper.toQuizDetailRdo(quiz, "OPT-2");

		assertThat(quizResultRdo.isCorrect()).isTrue();
		assertThat(quizResultRdo.getTitle()).isEqualTo("정답");
		assertThat(quizResultRdo.getText()).isEqualTo("정답 설명");
	}

	@Test
	void toQuizDetailRdo_shouldReturnErrorPayloadForWrongOption() {
		Quiz quiz = Quiz.builder()
			.correctOptionId("OPT-2")
			.answerTitle("정답")
			.answerText("정답 설명")
			.errorTitle("오답")
			.errorText("오답 설명")
			.build();

		QuizResultRdo quizResultRdo = tripMapper.toQuizDetailRdo(quiz, "OPT-1");

		assertThat(quizResultRdo.isCorrect()).isFalse();
		assertThat(quizResultRdo.getTitle()).isEqualTo("오답");
		assertThat(quizResultRdo.getText()).isEqualTo("오답 설명");
	}

	@Test
	void toTripListRdo_shouldMapTripSummaryWithResolvedLogoAsset() throws Exception {
		Object trip = newTrip();
		FileAssetRdo logo = file("logo-file-1", FileType.LOGO);

		TripListRdo tripListRdo = tripMapper.toTripListRdo(castTrip(trip), logo);

		assertThat(tripListRdo.getId()).isEqualTo("trip-1");
		assertThat(tripListRdo.getType()).isEqualTo("ryu");
		assertThat(tripListRdo.getName()).isEqualTo("Trip Name");
		assertThat(tripListRdo.getLogo()).isSameAs(logo);
	}

	@Test
	void toTripDetailRdo_shouldExposeRoleAssetsWithoutFileBoxMetadata() throws Exception {
		Object trip = newTrip();
		FileAssetRdo logo = file("logo-file-1", FileType.LOGO);
		FileAssetRdo firstMap = file("map-file-1", FileType.MAP);
		FileAssetRdo secondMap = file("map-file-2", FileType.MAP);

		TripDetailRdo tripDetailRdo = tripMapper.toTripDetailRdo(castTrip(trip), logo, firstMap, secondMap);

		assertThat(tripDetailRdo.getDriveUrl()).isEqualTo("https://drive.example");
		assertThat(tripDetailRdo.getPreviousButtonText()).isEqualTo("prev");
		assertThat(tripDetailRdo.getLogo()).isSameAs(logo);
		assertThat(tripDetailRdo.getFirstMap()).isSameAs(firstMap);
		assertThat(tripDetailRdo.getSecondMap()).isSameAs(secondMap);
		assertThat(hasProperty(tripDetailRdo.getClass(), "files")).isFalse();
	}

	@Test
	void toQuiz_shouldAssignStableOptionIdsAndPickCorrectOptionId() {
		QuizCdo quizCdo = new QuizCdo();
		quizCdo.setTitle("Quiz Title");
		quizCdo.setAnswerTitle("Answer Title");
		quizCdo.setAnswerText("Answer Text");
		quizCdo.setErrorTitle("Error Title");
		quizCdo.setErrorText("Error Text");
		quizCdo.setOptions(List.of(new OptionCdo("wrong", false), new OptionCdo("right", true)));

		Quiz quiz = tripMapper.toQuiz(quizCdo);

		assertThat(quiz.getCorrectOptionId()).isEqualTo("OPT-2");
		assertThat(quiz.getOptions())
			.extracting(option -> option.getId() + ":" + option.getText())
			.containsExactly("OPT-1:wrong", "OPT-2:right");
	}

	private Object newTrip() throws Exception {
		Object trip = Class.forName("com.seoulchonnom.spec.trip.entity.Trip").getDeclaredConstructor().newInstance();
		set(trip, "id", "trip-1");
		set(trip, "date", "2026-03-31");
		set(trip, "type", "ryu");
		set(trip, "name", "Trip Name");
		set(trip, "nextButtonText", "next");
		set(trip, "previousButtonText", "prev");
		set(trip, "driveUrl", "https://drive.example");
		return trip;
	}

	private com.seoulchonnom.spec.trip.entity.Trip castTrip(Object trip) {
		return (com.seoulchonnom.spec.trip.entity.Trip)trip;
	}

	private FileAssetRdo file(String id, FileType type) {
		return new FileAssetRdo(id, type, id + ".png", id + "-stored.png",
			type.name().toLowerCase() + "/" + id + ".png",
			"image/png", 10L);
	}
}

package com.seoulchonnom.rest.trip;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.seoulchonnom.aggregate.trip.logic.TripLogic;
import com.seoulchonnom.rest.common.handler.CommonExceptionHandler;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxItemRole;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxTargetType;
import com.seoulchonnom.spec.trip.facade.sdo.TripCdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripDetailRdo;

class TripResourceJsonContractTest {
	private TripLogic tripLogic;
	private MockMvc mockMvc;
	private LocalValidatorFactoryBean validator;

	@BeforeEach
	void setUp() {
		tripLogic = mock(TripLogic.class);
		validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		mockMvc = MockMvcBuilders.standaloneSetup(new TripResource(tripLogic))
			.setControllerAdvice(new CommonExceptionHandler())
			.setValidator(validator)
			.build();
	}

	@Test
	void createTrip_shouldBindFileBoxPayload() throws Exception {
		when(tripLogic.registerTrip(any(TripCdo.class))).thenReturn(new TripDetailRdo());

		mockMvc.perform(post("/trips")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "date": "2026-04-01",
					  "type": "ryu",
					  "name": "봄 나들이",
					  "nextButtonText": "다음 지도",
					  "previousButtonText": "이전 지도",
					  "driveUrl": "https://drive.example/trip",
					  "quiz": {
					    "title": "퀴즈",
					    "answerTitle": "정답",
					    "answerText": "정답 설명",
					    "errorTitle": "오답",
					    "errorText": "오답 설명",
					    "options": [
					      {
					        "text": "보기 1",
					        "isCorrect": true
					      }
					    ]
					  },
					  "files": [
					    {
					      "fileAssetId": "FILE-LOGO",
					      "targetType": "TRIP",
					      "targetId": null,
					      "role": "LOGO",
					      "sortOrder": 1
					    },
					    {
					      "fileAssetId": "FILE-MAP-1",
					      "targetType": "TRIP",
					      "targetId": null,
					      "role": "FIRST_MAP",
					      "sortOrder": 1
					    },
					    {
					      "fileAssetId": "FILE-MAP-2",
					      "targetType": "TRIP",
					      "targetId": null,
					      "role": "SECOND_MAP",
					      "sortOrder": 1
					    }
					  ]
					}
					"""))
			.andExpect(status().isOk());

		ArgumentCaptor<TripCdo> captor = ArgumentCaptor.forClass(TripCdo.class);
		verify(tripLogic).registerTrip(captor.capture());
		TripCdo tripCdo = captor.getValue();
		assertEquals("2026-04-01", tripCdo.getDate());
		assertEquals(FileBoxTargetType.TRIP, tripCdo.getFiles().get(0).getTargetType());
		assertEquals(FileBoxItemRole.LOGO, tripCdo.getFiles().get(0).getRole());
		assertEquals(FileBoxItemRole.FIRST_MAP, tripCdo.getFiles().get(1).getRole());
		assertEquals(FileBoxItemRole.SECOND_MAP, tripCdo.getFiles().get(2).getRole());
	}

	@Test
	void createTrip_withoutFiles_shouldReturnBadRequest() throws Exception {
		mockMvc.perform(post("/trips")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "date": "2026-04-01",
					  "type": "ryu",
					  "name": "봄 나들이",
					  "driveUrl": "https://drive.example/trip",
					  "quiz": {
					    "title": "퀴즈",
					    "answerTitle": "정답",
					    "answerText": "정답 설명",
					    "errorTitle": "오답",
					    "errorText": "오답 설명",
					    "options": [
					      {
					        "text": "보기 1",
					        "isCorrect": true
					      }
					    ]
					  }
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("나들이 파일 데이터는 필수값 입니다."));

		verifyNoInteractions(tripLogic);
	}

	@Test
	void createTrip_withInvalidFileRole_shouldReturnBadRequest() throws Exception {
		mockMvc.perform(post("/trips")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "date": "2026-04-01",
					  "type": "ryu",
					  "name": "봄 나들이",
					  "driveUrl": "https://drive.example/trip",
					  "quiz": {
					    "title": "퀴즈",
					    "answerTitle": "정답",
					    "answerText": "정답 설명",
					    "errorTitle": "오답",
					    "errorText": "오답 설명",
					    "options": [
					      {
					        "text": "보기 1",
					        "isCorrect": true
					      }
					    ]
					  },
					  "files": [
					    {
					      "fileAssetId": "FILE-LOGO",
					      "targetType": "TRIP",
					      "targetId": null,
					      "role": "UNKNOWN",
					      "sortOrder": 1
					    }
					  ]
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("입력이 올바르지 않습니다."));

		verifyNoInteractions(tripLogic);
	}
}

package com.seoulchonnom.rest.travel;

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

import com.seoulchonnom.aggregate.travel.logic.TravelLogic;
import com.seoulchonnom.rest.common.handler.CommonExceptionHandler;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxItemRole;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxTargetType;
import com.seoulchonnom.spec.travel.facade.sdo.TravelCdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelDetailRdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelUdo;

class TravelResourceJsonContractTest {
	private TravelLogic travelLogic;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		travelLogic = mock(TravelLogic.class);
		mockMvc = MockMvcBuilders.standaloneSetup(new TravelResource(travelLogic))
			.setControllerAdvice(new CommonExceptionHandler())
			.build();
	}

	@Test
	void registerTravel_shouldBindFileBoxPayload() throws Exception {
		when(travelLogic.registerTravel(any(TravelCdo.class))).thenReturn(new TravelDetailRdo());

		mockMvc.perform(post("/travels")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "서울 여행",
					  "region": "서울",
					  "startDate": "2026-03-15",
					  "endDate": "2026-03-17",
					  "tags": ["맛집", "산책"],
					  "travelDays": [
					    {
					      "date": "2026-03-15",
					      "title": "첫째 날",
					      "memo": "도착",
					      "sortOrder": 1,
					      "places": [
					        {
					          "placeKey": "550e8400-e29b-41d4-a716-446655440000",
					          "name": "광화문",
					          "category": "TOURIST_SPOT",
					          "address": "서울 종로구",
					          "memo": "오전 방문",
					          "description": "산책 코스",
					          "sortOrder": 1
					        }
					      ]
					    }
					  ],
					  "review": {
					    "oneLineSummary": "다시 가고 싶은 여행",
					    "goodPoint": "동선이 편했다",
					    "badPoint": "비가 왔다",
					    "revisitPlace": "광화문",
					    "finalReview": "만족"
					  },
					  "files": [
					    {
					      "fileAssetId": "FILE-TRAVEL-COVER",
					      "targetType": "TRAVEL",
					      "targetId": null,
					      "role": "COVER",
					      "caption": "대표 사진",
					      "sortOrder": 1
					    },
					    {
					      "fileAssetId": "FILE-PLACE-1",
					      "targetType": "TRAVEL_PLACE",
					      "targetId": "550e8400-e29b-41d4-a716-446655440000",
					      "role": "GALLERY",
					      "caption": "광화문 사진",
					      "sortOrder": 1
					    }
					  ]
					}
					"""))
			.andExpect(status().isOk());

		ArgumentCaptor<TravelCdo> captor = ArgumentCaptor.forClass(TravelCdo.class);
		verify(travelLogic).registerTravel(captor.capture());
		TravelCdo travelCdo = captor.getValue();
		assertEquals("서울 여행", travelCdo.getTitle());
		assertEquals("2026-03-15", travelCdo.getTravelDays().get(0).getDate());
		assertEquals("550e8400-e29b-41d4-a716-446655440000",
			travelCdo.getTravelDays().get(0).getPlaces().get(0).getPlaceKey());
		assertEquals(FileBoxTargetType.TRAVEL, travelCdo.getFiles().get(0).getTargetType());
		assertEquals(FileBoxItemRole.COVER, travelCdo.getFiles().get(0).getRole());
		assertEquals(FileBoxTargetType.TRAVEL_PLACE, travelCdo.getFiles().get(1).getTargetType());
		assertEquals(FileBoxItemRole.GALLERY, travelCdo.getFiles().get(1).getRole());
	}

	@Test
	void modifyTravel_shouldBindExistingAndNewFileBoxItems() throws Exception {
		when(travelLogic.modifyTravel(eq("TRAVEL-1"), any(TravelUdo.class))).thenReturn(new TravelDetailRdo());

		mockMvc.perform(put("/travels/TRAVEL-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "서울 여행 수정",
					  "region": "서울",
					  "startDate": "2026-03-15",
					  "endDate": "2026-03-17",
					  "confirmDeleteDays": false,
					  "travelDays": [],
					  "files": [
					    {
					      "id": "9f33f0f8-60b3-4a94-a167-2a8f2d3cda12",
					      "fileAssetId": "FILE-TRAVEL-COVER",
					      "targetType": "TRAVEL",
					      "targetId": null,
					      "role": "COVER",
					      "caption": "대표 사진",
					      "sortOrder": 1
					    },
					    {
					      "fileAssetId": "FILE-TRAVEL-GALLERY",
					      "targetType": "TRAVEL",
					      "targetId": null,
					      "role": "GALLERY",
					      "caption": "추가 사진",
					      "sortOrder": 2
					    }
					  ]
					}
					"""))
			.andExpect(status().isOk());

		ArgumentCaptor<TravelUdo> captor = ArgumentCaptor.forClass(TravelUdo.class);
		verify(travelLogic).modifyTravel(eq("TRAVEL-1"), captor.capture());
		TravelUdo travelUdo = captor.getValue();
		assertEquals("9f33f0f8-60b3-4a94-a167-2a8f2d3cda12", travelUdo.getFiles().get(0).getId());
		assertNull(travelUdo.getFiles().get(1).getId());
		assertEquals(FileBoxItemRole.GALLERY, travelUdo.getFiles().get(1).getRole());
	}
}

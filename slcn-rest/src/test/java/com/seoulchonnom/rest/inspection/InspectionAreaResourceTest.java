package com.seoulchonnom.rest.inspection;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.seoulchonnom.aggregate.flow.inspection.InspectionAreaFlow;
import com.seoulchonnom.aggregate.flow.inspection.InspectionAreaQueryFlow;
import com.seoulchonnom.spec.inspection.entity.vo.InspectionAreaSort;
import com.seoulchonnom.spec.inspection.entity.vo.RevisitIntent;
import com.seoulchonnom.spec.inspection.facade.sdo.AreaViewedPropertyRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaDetailRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaListRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaUdo;

class InspectionAreaResourceTest {
	private final InspectionAreaQueryFlow inspectionAreaQueryFlow = mock(InspectionAreaQueryFlow.class);
	private final InspectionAreaFlow inspectionAreaFlow = mock(InspectionAreaFlow.class);
	private final InspectionAreaResource inspectionAreaResource = new InspectionAreaResource(
		inspectionAreaQueryFlow, inspectionAreaFlow);

	@Test
	void getInspectionAreas_shouldPassKeywordAndPagingParams() {
		InspectionAreaListRdo response = new InspectionAreaListRdo();
		when(inspectionAreaQueryFlow.getInspectionAreas("성수", RevisitIntent.YES, InspectionAreaSort.VISIT_COUNT, 1,
			30)).thenReturn(response);

		var result = inspectionAreaResource.getInspectionAreas("성수", RevisitIntent.YES,
			InspectionAreaSort.VISIT_COUNT, 1, 30);

		assertThat(result.getBody()).isSameAs(response);
		verify(inspectionAreaQueryFlow).getInspectionAreas("성수", RevisitIntent.YES, InspectionAreaSort.VISIT_COUNT, 1,
			30);
	}

	/**
	 * page/size의 실제 기본값(0/20)은 @RequestParam(defaultValue=...)가 Spring MVC 바인딩
	 * 시점에 채운다 — 이 테스트는 Resource 메서드를 직접 호출하므로 그 바인딩 자체는
	 * 검증 대상이 아니다. keyword/revisitIntent/sort가 전부 null이어도 Flow로 그대로
	 * 위임되는지만 확인한다.
	 */
	@Test
	void getInspectionAreas_shouldDelegateWithNullFiltersUntouched() {
		when(inspectionAreaQueryFlow.getInspectionAreas(null, null, null, 0, 20))
			.thenReturn(new InspectionAreaListRdo());

		inspectionAreaResource.getInspectionAreas(null, null, null, 0, 20);

		verify(inspectionAreaQueryFlow).getInspectionAreas(null, null, null, 0, 20);
	}

	@Test
	void getInspectionArea_shouldPassVisitIdAndIncludeProperties() {
		when(inspectionAreaQueryFlow.getInspectionArea("INSPECTION_AREA-0001", "v1", false))
			.thenReturn(new InspectionAreaDetailRdo());

		inspectionAreaResource.getInspectionArea("INSPECTION_AREA-0001", "v1", false);

		verify(inspectionAreaQueryFlow).getInspectionArea("INSPECTION_AREA-0001", "v1", false);
	}

	@Test
	void getAreaProperties_shouldDelegateToQueryFlow() {
		List<AreaViewedPropertyRdo> properties = List.of(new AreaViewedPropertyRdo());
		when(inspectionAreaQueryFlow.getAreaProperties("INSPECTION_AREA-0001")).thenReturn(properties);

		var response = inspectionAreaResource.getAreaProperties("INSPECTION_AREA-0001");

		assertThat(response.getBody()).isSameAs(properties);
		verify(inspectionAreaQueryFlow).getAreaProperties("INSPECTION_AREA-0001");
	}

	@Test
	void registerInspectionArea_shouldDelegateToFlow() {
		InspectionAreaCdo cdo = new InspectionAreaCdo("성수동", null);
		InspectionAreaRdo rdo = new InspectionAreaRdo();
		when(inspectionAreaFlow.registerInspectionArea(cdo)).thenReturn(rdo);

		assertThat(inspectionAreaResource.registerInspectionArea(cdo).getBody()).isSameAs(rdo);
	}

	@Test
	void modifyInspectionArea_shouldDelegateToFlow() {
		InspectionAreaUdo udo = new InspectionAreaUdo("성수동", "설명");
		when(inspectionAreaFlow.modifyInspectionArea("INSPECTION_AREA-0001", udo))
			.thenReturn(new InspectionAreaRdo());

		inspectionAreaResource.modifyInspectionArea("INSPECTION_AREA-0001", udo);

		verify(inspectionAreaFlow).modifyInspectionArea("INSPECTION_AREA-0001", udo);
	}

	@Test
	void deleteInspectionArea_shouldReturnNoContent() {
		var response = inspectionAreaResource.deleteInspectionArea("INSPECTION_AREA-0001");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		verify(inspectionAreaFlow).deleteInspectionArea("INSPECTION_AREA-0001");
	}
}

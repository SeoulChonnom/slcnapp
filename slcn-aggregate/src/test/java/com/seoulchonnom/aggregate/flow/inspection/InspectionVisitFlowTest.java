package com.seoulchonnom.aggregate.flow.inspection;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.seoulchonnom.aggregate.inspection.exception.InvalidInspectionVisitException;
import com.seoulchonnom.aggregate.inspection.logic.InspectionAreaLogic;
import com.seoulchonnom.aggregate.inspection.logic.InspectionTagLogic;
import com.seoulchonnom.aggregate.inspection.logic.InspectionVisitLogic;
import com.seoulchonnom.aggregate.inspection.store.InspectionTagStore;
import com.seoulchonnom.spec.common.generator.IdGenerator;
import com.seoulchonnom.spec.inspection.entity.InspectionArea;
import com.seoulchonnom.spec.inspection.entity.InspectionTag;
import com.seoulchonnom.spec.inspection.entity.InspectionVisit;
import com.seoulchonnom.spec.inspection.entity.vo.InspectionStatus;
import com.seoulchonnom.spec.inspection.entity.vo.RevisitIntent;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitUdo;

class InspectionVisitFlowTest {
	private final InspectionVisitLogic inspectionVisitLogic = mock(InspectionVisitLogic.class);
	private final InspectionAreaLogic inspectionAreaLogic = mock(InspectionAreaLogic.class);
	private final InspectionTagLogic inspectionTagLogic = mock(InspectionTagLogic.class);
	private final InspectionTagStore inspectionTagStore = mock(InspectionTagStore.class);
	private final InspectionPhotoSupport inspectionPhotoSupport = mock(InspectionPhotoSupport.class);
	private final IdGenerator idGenerator = mock(IdGenerator.class);
	private final InspectionVisitFlow inspectionVisitFlow = new InspectionVisitFlow(inspectionVisitLogic,
		inspectionAreaLogic, inspectionTagLogic, inspectionTagStore, inspectionPhotoSupport, idGenerator);

	private static InspectionVisitCdo cdo(String areaId, InspectionAreaCdo area) {
		InspectionVisitCdo cdo = new InspectionVisitCdo();
		cdo.setAreaId(areaId);
		cdo.setArea(area);
		cdo.setVisitedAt("2026-09-17T14:00:00");
		return cdo;
	}

	private static InspectionArea area(String id, String name) {
		return new InspectionArea(id, name, null);
	}

	private void realVisitLogicBehaviour() {
		when(inspectionVisitLogic.parseVisitedAt(anyString())).thenReturn(LocalDateTime.of(2026, 9, 17, 14, 0));
		when(inspectionVisitLogic.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void registerInspectionVisit_shouldUseExistingAreaWhenAreaIdGiven() {
		realVisitLogicBehaviour();
		when(idGenerator.nextDomainId("INSPECTION_VISIT")).thenReturn("INSPECTION_VISIT-0001");
		when(inspectionAreaLogic.getInspectionArea("INSPECTION_AREA-0001"))
			.thenReturn(area("INSPECTION_AREA-0001", "성수동"));

		InspectionVisit visit = inspectionVisitFlow.registerInspectionVisit(cdo("INSPECTION_AREA-0001", null));

		assertThat(visit.getId()).isEqualTo("INSPECTION_VISIT-0001");
		assertThat(visit.getAreaId()).isEqualTo("INSPECTION_AREA-0001");
		assertThat(visit.getStatus()).isEqualTo(InspectionStatus.DRAFT);
		verify(inspectionAreaLogic, never()).registerInspectionArea(any());
	}

	@Test
	void registerInspectionVisit_shouldCreateAreaWhenInlineAreaGiven() {
		realVisitLogicBehaviour();
		when(idGenerator.nextDomainId("INSPECTION_VISIT")).thenReturn("INSPECTION_VISIT-0002");
		when(inspectionAreaLogic.registerInspectionArea(any()))
			.thenReturn(area("INSPECTION_AREA-0009", "성수동"));

		InspectionVisit visit = inspectionVisitFlow.registerInspectionVisit(
			cdo(null, new InspectionAreaCdo("성수동", "서울숲 ~ 뚝섬역")));

		assertThat(visit.getAreaId()).isEqualTo("INSPECTION_AREA-0009");
	}

	@Test
	void registerInspectionVisit_shouldRejectWhenAreaIsMissing() {
		assertThatThrownBy(() -> inspectionVisitFlow.registerInspectionVisit(cdo(null, null)))
			.isInstanceOf(InvalidInspectionVisitException.class);
		verifyNoInteractions(idGenerator);
	}

	@Test
	void registerInspectionVisit_shouldLinkTagsAndPhotosAfterSave() {
		realVisitLogicBehaviour();
		when(idGenerator.nextDomainId(anyString())).thenReturn("INSPECTION_VISIT-0001");
		when(inspectionAreaLogic.getInspectionArea(anyString())).thenReturn(area("INSPECTION_AREA-0001", "성수동"));
		InspectionTag tag = InspectionTag.builder().name("한강").build();
		when(inspectionTagLogic.resolveTags(anyList())).thenReturn(List.of(tag));

		InspectionVisitCdo cdo = cdo("INSPECTION_AREA-0001", null);
		cdo.setTags(List.of("#한강"));
		inspectionVisitFlow.registerInspectionVisit(cdo);

		InOrder order = inOrder(inspectionVisitLogic, inspectionTagStore, inspectionPhotoSupport);
		order.verify(inspectionVisitLogic).save(any());
		order.verify(inspectionTagStore).linkVisit("INSPECTION_VISIT-0001", List.of(tag));
		order.verify(inspectionPhotoSupport).syncVisitPhotos(eq("INSPECTION_VISIT-0001"), any());
	}

	@Test
	void modifyInspectionVisit_shouldKeepTagsWhenOmitted() {
		InspectionVisit visit = new InspectionVisit("INSPECTION_VISIT-0001", "INSPECTION_AREA-0001",
			LocalDateTime.of(2026, 9, 17, 14, 0));
		when(inspectionVisitLogic.getInspectionVisit("INSPECTION_VISIT-0001")).thenReturn(visit);
		when(inspectionVisitLogic.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		InspectionVisitUdo udo = new InspectionVisitUdo();
		udo.setVisitedAt("2026-09-17T14:00:00");
		inspectionVisitFlow.modifyInspectionVisit("INSPECTION_VISIT-0001", udo);

		// tags 생략은 "수정하지 않음"이다. travel처럼 태그를 지우면 안 된다
		verifyNoInteractions(inspectionTagStore);
		verifyNoInteractions(inspectionTagLogic);
	}

	@Test
	void modifyInspectionVisit_shouldClearTagsWhenEmptyListGiven() {
		InspectionVisit visit = new InspectionVisit("INSPECTION_VISIT-0001", "INSPECTION_AREA-0001",
			LocalDateTime.of(2026, 9, 17, 14, 0));
		visit.setRevisitIntent(RevisitIntent.YES);
		when(inspectionVisitLogic.getInspectionVisit("INSPECTION_VISIT-0001")).thenReturn(visit);
		when(inspectionVisitLogic.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(inspectionTagLogic.resolveTags(List.of())).thenReturn(List.of());

		InspectionVisitUdo udo = new InspectionVisitUdo();
		udo.setVisitedAt("2026-09-17T14:00:00");
		udo.setTags(List.of());
		inspectionVisitFlow.modifyInspectionVisit("INSPECTION_VISIT-0001", udo);

		verify(inspectionTagStore).linkVisit("INSPECTION_VISIT-0001", List.of());
	}
}

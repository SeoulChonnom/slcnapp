package com.seoulchonnom.aggregate.inspection.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.aggregate.inspection.exception.InspectionAreaDuplicatedException;
import com.seoulchonnom.aggregate.inspection.store.InspectionAreaStore;
import com.seoulchonnom.spec.common.generator.IdGenerator;
import com.seoulchonnom.spec.inspection.entity.InspectionArea;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaUdo;
import com.seoulchonnom.spec.inspection.mapper.InspectionAreaMapper;

class InspectionAreaLogicTest {
	private final InspectionAreaStore inspectionAreaStore = mock(InspectionAreaStore.class);
	private final IdGenerator idGenerator = mock(IdGenerator.class);
	private final InspectionAreaLogic inspectionAreaLogic = new InspectionAreaLogic(inspectionAreaStore,
		new InspectionAreaMapper(), idGenerator);

	private void echoSave() {
		when(inspectionAreaStore.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void registerInspectionArea_shouldNormalizeNameWhitespace() {
		when(idGenerator.nextDomainId("INSPECTION_AREA")).thenReturn("INSPECTION_AREA-0001");
		when(inspectionAreaStore.findOptionalByName("성수동 일대")).thenReturn(Optional.empty());
		echoSave();

		InspectionArea area = inspectionAreaLogic.registerInspectionArea(
			new InspectionAreaCdo("  성수동   일대 ", "  서울숲 ~ 뚝섬역  "));

		assertThat(area.getId()).isEqualTo("INSPECTION_AREA-0001");
		assertThat(area.getName()).isEqualTo("성수동 일대");
		assertThat(area.getDescription()).isEqualTo("서울숲 ~ 뚝섬역");
	}

	@Test
	void registerInspectionArea_shouldRejectDuplicatedNameWithExistingId() {
		InspectionArea existing = new InspectionArea("INSPECTION_AREA-0001", "성수동", null);
		when(inspectionAreaStore.findOptionalByName("성수동")).thenReturn(Optional.of(existing));

		assertThatThrownBy(() -> inspectionAreaLogic.registerInspectionArea(new InspectionAreaCdo("성수동", null)))
			.isInstanceOf(InspectionAreaDuplicatedException.class)
			.hasMessageContaining("INSPECTION_AREA-0001");
		verify(inspectionAreaStore, never()).save(any());
	}

	@Test
	void registerInspectionArea_shouldRejectBlankName() {
		assertThatThrownBy(() -> inspectionAreaLogic.registerInspectionArea(new InspectionAreaCdo("   ", null)))
			.isInstanceOf(BadRequestException.class);
		verifyNoInteractions(idGenerator);
	}

	@Test
	void registerInspectionArea_shouldKeepDescriptionNullWhenBlank() {
		when(idGenerator.nextDomainId(anyString())).thenReturn("INSPECTION_AREA-0002");
		when(inspectionAreaStore.findOptionalByName("잠실")).thenReturn(Optional.empty());
		echoSave();

		InspectionArea area = inspectionAreaLogic.registerInspectionArea(new InspectionAreaCdo("잠실", "  "));

		assertThat(area.getDescription()).isNull();
	}

	@Test
	void modifyInspectionArea_shouldAllowKeepingItsOwnName() {
		InspectionArea area = new InspectionArea("INSPECTION_AREA-0001", "성수동", "옛 설명");
		when(inspectionAreaStore.findById("INSPECTION_AREA-0001")).thenReturn(area);
		when(inspectionAreaStore.findOptionalByName("성수동")).thenReturn(Optional.of(area));
		echoSave();

		InspectionArea modified = inspectionAreaLogic.modifyInspectionArea("INSPECTION_AREA-0001",
			new InspectionAreaUdo("성수동", "새 설명"));

		assertThat(modified.getDescription()).isEqualTo("새 설명");
	}

	@Test
	void modifyInspectionArea_shouldRejectRenameIntoAnotherArea() {
		InspectionArea area = new InspectionArea("INSPECTION_AREA-0001", "성수동", null);
		InspectionArea other = new InspectionArea("INSPECTION_AREA-0002", "잠실", null);
		when(inspectionAreaStore.findById("INSPECTION_AREA-0001")).thenReturn(area);
		when(inspectionAreaStore.findOptionalByName("잠실")).thenReturn(Optional.of(other));

		assertThatThrownBy(() -> inspectionAreaLogic.modifyInspectionArea("INSPECTION_AREA-0001",
			new InspectionAreaUdo("잠실", null)))
			.isInstanceOf(InspectionAreaDuplicatedException.class);
		assertThat(area.getName()).isEqualTo("성수동");
	}
}

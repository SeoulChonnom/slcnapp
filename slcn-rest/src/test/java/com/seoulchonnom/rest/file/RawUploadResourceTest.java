package com.seoulchonnom.rest.file;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.seoulchonnom.aggregate.file.logic.RawUploadLogic;
import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.entity.vo.FileKind;
import com.seoulchonnom.spec.file.entity.vo.FileStatus;
import com.seoulchonnom.spec.file.facade.sdo.RawUploadCompleteCdo;
import com.seoulchonnom.spec.file.facade.sdo.RawUploadPartCdo;
import com.seoulchonnom.spec.file.facade.sdo.RawUploadPartUrlRdo;
import com.seoulchonnom.spec.file.facade.sdo.RawUploadSessionCdo;
import com.seoulchonnom.spec.file.facade.sdo.RawUploadSessionRdo;

class RawUploadResourceTest {
	private final RawUploadLogic rawUploadLogic = mock(RawUploadLogic.class);
	private final RawUploadResource resource = new RawUploadResource(rawUploadLogic);

	@Test
	void createSession_shouldRespondCreatedWithPartUrls() {
		RawUploadSessionCdo cdo = new RawUploadSessionCdo("travel", "DSCF1234.RAF", 1L);
		RawUploadSessionRdo session = new RawUploadSessionRdo("raw-1", "upload-1", 16L, OffsetDateTime.now(),
			List.of(new RawUploadPartUrlRdo(1, "https://r2.example/part/1")));
		when(rawUploadLogic.createSession(cdo)).thenReturn(session);

		var response = resource.createSession(cdo);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).isSameAs(session);
	}

	@Test
	void complete_shouldReturnReadyRawAsset() {
		RawUploadCompleteCdo cdo = new RawUploadCompleteCdo("upload-1", List.of(new RawUploadPartCdo(1, "\"e\"")));
		FileAsset ready = FileAsset.pendingRaw("DSCF1234.RAF", "72d768d4-2b05-48f9-bee8-fee3b52e909f.raf",
			"image/x-fujifilm-raf", 1L, "upload-1");
		ready.setId("raw-1");
		ready.markUploadCompleted();
		when(rawUploadLogic.complete("raw-1", cdo)).thenReturn(ready);

		var response = resource.complete("raw-1", cdo);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().getFileId()).isEqualTo("raw-1");
		assertThat(response.getBody().getKind()).isEqualTo(FileKind.RAW);
		assertThat(response.getBody().getStatus()).isEqualTo(FileStatus.READY);
	}

	@Test
	void delete_shouldRespondNoContent() {
		var response = resource.delete("raw-1");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		verify(rawUploadLogic).delete("raw-1");
	}
}

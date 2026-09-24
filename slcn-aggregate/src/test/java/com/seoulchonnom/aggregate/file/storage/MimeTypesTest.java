package com.seoulchonnom.aggregate.file.storage;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MimeTypesTest {
	@Test
	void ofFilename_shouldMapKnownImageExtensions() {
		assertThat(MimeTypes.ofFilename("a.jpg")).isEqualTo("image/jpeg");
		assertThat(MimeTypes.ofFilename("a.jpeg")).isEqualTo("image/jpeg");
		assertThat(MimeTypes.ofFilename("a.png")).isEqualTo("image/png");
		assertThat(MimeTypes.ofFilename("a.gif")).isEqualTo("image/gif");
		assertThat(MimeTypes.ofFilename("a.svg")).isEqualTo("image/svg+xml");
		assertThat(MimeTypes.ofFilename("a.webp")).isEqualTo("image/webp");
	}

	@Test
	void ofFilename_shouldIgnoreCase() {
		assertThat(MimeTypes.ofFilename("A.PNG")).isEqualTo("image/png");
	}

	@Test
	void ofFilename_shouldFallBackToOctetStreamWhenExtensionIsUnknownOrMissing() {
		assertThat(MimeTypes.ofFilename("a.bin")).isEqualTo("application/octet-stream");
		assertThat(MimeTypes.ofFilename("noextension")).isEqualTo("application/octet-stream");
		assertThat(MimeTypes.ofFilename(null)).isEqualTo("application/octet-stream");
	}
}

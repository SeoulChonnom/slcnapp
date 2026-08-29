package com.seoulchonnom.auth.matcher;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AssetRequestMatchersTest {
	private static final String CONTEXT_PATH = "/api";
	private static final String FILE_ID = "52cce541-8752-4c00-9b91-452272a5d98f";

	@Test
	void imageReadMatcher_shouldMatchImageLookupPaths() {
		assertThat(AssetRequestMatchers.IMAGE_READ_MATCHER.matches(get("/assets/files/" + FILE_ID))).isTrue();
		assertThat(AssetRequestMatchers.IMAGE_READ_MATCHER.matches(get("/assets/file"))).isTrue();
	}

	@Test
	void imageReadMatcher_shouldNotMatchDownload() {
		assertThat(AssetRequestMatchers.IMAGE_READ_MATCHER.matches(get("/assets/files/" + FILE_ID + "/download")))
			.isFalse();
	}

	@Test
	void imageReadMatcher_shouldNotMatchUploadOrOtherMethods() {
		assertThat(AssetRequestMatchers.IMAGE_READ_MATCHER.matches(request("POST", "/assets/file"))).isFalse();
		assertThat(AssetRequestMatchers.IMAGE_READ_MATCHER.matches(request("POST", "/assets/files"))).isFalse();
		assertThat(AssetRequestMatchers.IMAGE_READ_MATCHER.matches(request("OPTIONS", "/assets/files/" + FILE_ID)))
			.isFalse();
	}

	@Test
	void imageReadMatcher_shouldNotMatchOtherResources() {
		assertThat(AssetRequestMatchers.IMAGE_READ_MATCHER.matches(get("/travels"))).isFalse();
		assertThat(AssetRequestMatchers.IMAGE_READ_MATCHER.matches(get("/users/me"))).isFalse();
		assertThat(AssetRequestMatchers.IMAGE_READ_MATCHER.matches(get("/assets/files"))).isFalse();
	}

	@Test
	void cacheableImageMatcher_shouldIncludeDownloadButNotUnrelatedPaths() {
		assertThat(AssetRequestMatchers.CACHEABLE_IMAGE_MATCHER.matches(get("/assets/files/" + FILE_ID))).isTrue();
		assertThat(AssetRequestMatchers.CACHEABLE_IMAGE_MATCHER.matches(get("/assets/files/" + FILE_ID + "/download")))
			.isTrue();
		assertThat(AssetRequestMatchers.CACHEABLE_IMAGE_MATCHER.matches(get("/assets/file"))).isTrue();
		assertThat(AssetRequestMatchers.CACHEABLE_IMAGE_MATCHER.matches(get("/travels"))).isFalse();
	}

	@Test
	void matchers_shouldIgnoreContextPath() {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", CONTEXT_PATH + "/assets/files/" + FILE_ID);
		request.setContextPath(CONTEXT_PATH);

		assertThat(AssetRequestMatchers.IMAGE_READ_MATCHER.matches(request)).isTrue();
		assertThat(AssetRequestMatchers.CACHEABLE_IMAGE_MATCHER.matches(request)).isTrue();
	}

	private MockHttpServletRequest get(String path) {
		return request("GET", path);
	}

	private MockHttpServletRequest request(String method, String path) {
		return new MockHttpServletRequest(method, path);
	}
}

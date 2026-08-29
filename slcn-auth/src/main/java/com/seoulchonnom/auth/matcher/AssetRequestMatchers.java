package com.seoulchonnom.auth.matcher;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.matcher.RequestMatcher;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 이미지 자산 경로 판정을 한곳에 모은다.
 * 시큐리티 설정(slcn-boot)과 쿠키 인증 필터(slcn-auth)가 같은 기준으로 판단해야 하므로
 * 경로 문자열을 각자 복제하지 않는다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AssetRequestMatchers {
	/** 경로 기반 단건 조회. FileResource의 GET /assets/file과 대응한다. */
	private static final String FILE_PATH = "/assets/file";
	/** ID 기반 조회와 다운로드. FileResource의 GET /assets/files/{fileId}와 대응한다. */
	private static final String FILES_PATH_PREFIX = "/assets/files/";
	private static final String DOWNLOAD_PATH_SUFFIX = "/download";

	/**
	 * 이미지 조회 응답만 브라우저 캐시를 허용한다. 파일 ID와 저장 파일명이 불변이라 안전하며,
	 * 나머지 응답에는 Spring Security 기본 no-store 정책을 그대로 유지한다.
	 * 다운로드도 같은 바이트를 반환하므로 함께 포함한다.
	 */
	public static final RequestMatcher CACHEABLE_IMAGE_MATCHER = request -> {
		if (!isGet(request)) {
			return false;
		}

		String path = pathWithinApplication(request);
		return FILE_PATH.equals(path) || path.startsWith(FILES_PATH_PREFIX);
	};

	/**
	 * sessionId 쿠키만으로 인증을 허용하는 경로다.
	 * img 태그는 요청 헤더를 붙일 수 없어 쿠키 외에 액세스 토큰을 전달할 방법이 없다.
	 * 다운로드는 사용자의 명시적 클릭으로 발생해 img 태그 제약이 없으므로 제외하고 액세스 토큰을 계속 요구한다.
	 */
	public static final RequestMatcher IMAGE_READ_MATCHER = request -> {
		if (!isGet(request)) {
			return false;
		}

		String path = pathWithinApplication(request);
		if (FILE_PATH.equals(path)) {
			return true;
		}

		return path.startsWith(FILES_PATH_PREFIX) && !path.endsWith(DOWNLOAD_PATH_SUFFIX);
	};

	private static boolean isGet(HttpServletRequest request) {
		return HttpMethod.GET.matches(request.getMethod());
	}

	/**
	 * context-path를 제외한 경로를 돌려준다. 컨트롤러 매핑과 같은 기준으로 비교해야 한다.
	 */
	private static String pathWithinApplication(HttpServletRequest request) {
		String uri = request.getRequestURI();
		String contextPath = request.getContextPath();
		return contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)
			? uri.substring(contextPath.length())
			: uri;
	}
}

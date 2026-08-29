package com.seoulchonnom.auth.filter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import com.seoulchonnom.aggregate.user.exception.InvalidRefreshTokenException;
import com.seoulchonnom.auth.constant.AuthConstant;
import com.seoulchonnom.auth.matcher.AssetRequestMatchers;
import com.seoulchonnom.auth.store.RefreshSessionStore;
import com.seoulchonnom.auth.store.projection.RefreshSession;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * img 태그로 부르는 이미지 조회에 한해 sessionId 쿠키만으로 인증한다.
 * img 태그는 요청 헤더를 붙일 수 없어 액세스 토큰을 전달할 방법이 없다.
 *
 * 쿠키 인증은 CSRF에 노출되므로 세 겹으로 막는다.
 * 1. sessionId 쿠키가 SameSite=Lax라 제3자 사이트의 subresource 요청에는 전송되지 않는다.
 * 2. Sec-Fetch-Site/Sec-Fetch-Dest로 같은 사이트에서 발생한 이미지 요청인지 확인한다.
 * 3. 부여하는 권한을 IMAGE_READ로 제한해 다른 endpoint에는 도달할 수 없게 한다.
 */
@RequiredArgsConstructor
@Slf4j
public class ImageSessionAuthenticationFilter extends OncePerRequestFilter {
	private static final String SEC_FETCH_SITE_HEADER = "Sec-Fetch-Site";
	private static final String SEC_FETCH_DEST_HEADER = "Sec-Fetch-Dest";
	private static final Set<String> SAME_SITE_FETCH_VALUES = Set.of("same-origin", "same-site");
	private static final String IMAGE_FETCH_DEST = "image";
	private static final List<GrantedAuthority> IMAGE_READ_AUTHORITIES =
		List.of(new SimpleGrantedAuthority(AuthConstant.IMAGE_READ_AUTHORITY));

	private final RefreshSessionStore refreshSessionStore;

	/**
	 * 이미지 조회 외의 경로에서 동작하면 액세스 토큰이 만료된 요청이 401 대신 403이 되어
	 * 프론트의 재발급 흐름이 끊긴다. 필터 등록 방식과 무관하게 경로를 여기서 한 번 더 막는다.
	 */
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !AssetRequestMatchers.IMAGE_READ_MATCHER.matches(request);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws IOException, ServletException {
		if (!isAlreadyAuthenticated()) {
			authenticateBySessionId(request);
		}

		chain.doFilter(request, response);
	}

	private boolean isAlreadyAuthenticated() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return authentication != null && authentication.isAuthenticated();
	}

	private void authenticateBySessionId(HttpServletRequest request) {
		String sessionId = resolveSessionId(request);
		if (!StringUtils.hasText(sessionId) || !isSameSiteImageRequest(request)) {
			return;
		}

		findLiveSession(sessionId).ifPresent(refreshSession ->
			SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(refreshSession.userId(), "", IMAGE_READ_AUTHORITIES)));
	}

	/**
	 * Redis TTL이 이미 만료된 세션을 지우지만, expiresAt을 한 번 더 확인해 TTL 설정 실패에 대비한다.
	 */
	private Optional<RefreshSession> findLiveSession(String sessionId) {
		try {
			return refreshSessionStore.findBySessionId(sessionId)
				.filter(refreshSession -> refreshSession.expiresAt() > System.currentTimeMillis());
		} catch (InvalidRefreshTokenException e) {
			// 세션 값이 깨졌다. 이미지 조회는 인증하지 않고 401로 끝낸다.
			log.warn("Discarded a corrupted refresh session for an image request.");
			return Optional.empty();
		}
	}

	/**
	 * Sec-Fetch-* 는 forbidden header name이라 스크립트가 위조하거나 제거할 수 없다.
	 * 헤더가 없으면 통과시킨다. 이 헤더를 보내지 않는 구형 브라우저를 막지 않기 위해서이고,
	 * 헤더를 생략할 수 있는 비브라우저 클라이언트는 애초에 피해자의 쿠키를 갖고 있지 않으므로
	 * 우회 경로가 되지 않는다.
	 */
	private boolean isSameSiteImageRequest(HttpServletRequest request) {
		String fetchSite = request.getHeader(SEC_FETCH_SITE_HEADER);
		if (StringUtils.hasText(fetchSite) && !SAME_SITE_FETCH_VALUES.contains(fetchSite)) {
			return false;
		}

		String fetchDest = request.getHeader(SEC_FETCH_DEST_HEADER);
		return !StringUtils.hasText(fetchDest) || IMAGE_FETCH_DEST.equals(fetchDest);
	}

	private String resolveSessionId(HttpServletRequest request) {
		Cookie cookie = WebUtils.getCookie(request, AuthConstant.SESSION_ID_COOKIE_NAME);
		return cookie == null ? null : cookie.getValue();
	}
}

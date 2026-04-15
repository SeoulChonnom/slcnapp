package com.seoulchonnom.auth.util;

import java.time.Duration;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.seoulchonnom.auth.constant.AuthConstant;
import com.seoulchonnom.auth.logic.UserAuthDetailLogic;
import com.seoulchonnom.spec.user.facade.sdo.TokenRdo;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.MacAlgorithm;
import io.jsonwebtoken.security.SecurityException;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {
	private static final String ACCESS_TOKEN_TYPE = "access";
	private static final String REFRESH_TOKEN_TYPE = "refresh";
	private static final String CLAIM_USERNAME = "username";
	private static final String CLAIM_LEGACY_USERNAME = "userName";
	private static final String CLAIM_ROLES = "roles";
	private static final String CLAIM_TOKEN_TYPE = "token_type";
	private static final long ACCESS_TOKEN_VALID_TIME = 30 * 60 * 1000L;
	private static final long REFRESH_TOKEN_VALID_TIME = 14 * 24 * 60 * 60 * 1000L;

	@Value("${spring.jwt.secretKey}")
	private String key;

	@Value("${spring.jwt.algorithm:HS512}")
	private String algorithm;

	@Value("${spring.application.name}")
	private String issuer;

	@Value("#{'${spring.jwt.accessAudiences:slcn-platform}'.split(',')}")
	private List<String> accessAudiences;

	@Value("${spring.jwt.refreshAudience:slcn-auth-refresh}")
	private String refreshAudience;

	private SecretKey secretKey;
	private MacAlgorithm macAlgorithm;

	private final UserAuthDetailLogic userAuthDetailLogic;

	@PostConstruct
	protected void init() {
		this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(key));
		this.macAlgorithm = resolveMacAlgorithm(algorithm);
	}

	public TokenRdo createToken(UserDetails userDetails, String userId) {
		Date now = new Date();
		List<String> roles = userDetails.getAuthorities().stream()
			.map(GrantedAuthority::getAuthority)
			.toList();

		String accessToken = Jwts.builder()
			.subject(userId)
			.id(UUID.randomUUID().toString())
			.claim(CLAIM_USERNAME, userDetails.getUsername())
			.claim(CLAIM_LEGACY_USERNAME, userDetails.getUsername())
			.claim(CLAIM_ROLES, roles)
			.claim(CLAIM_TOKEN_TYPE, ACCESS_TOKEN_TYPE)
			.claim(Claims.AUDIENCE, accessAudiences)
			.issuer(issuer)
			.issuedAt(now)
			.expiration(new Date(now.getTime() + ACCESS_TOKEN_VALID_TIME))
			.signWith(secretKey, macAlgorithm)
			.compact();

		String refreshToken = Jwts.builder()
			.subject(userId)
			.id(UUID.randomUUID().toString())
			.claim(CLAIM_TOKEN_TYPE, REFRESH_TOKEN_TYPE)
			.claim(Claims.AUDIENCE, refreshAudience)
			.issuer(issuer)
			.issuedAt(now)
			.expiration(new Date(now.getTime() + REFRESH_TOKEN_VALID_TIME))
			.signWith(secretKey, macAlgorithm)
			.compact();

		return TokenRdo.builder()
			.userId(userId)
			.accessToken(accessToken)
			.refreshToken(refreshToken)
			.build();
	}

	public TokenValidationResult validateAccessToken(String token) {
		return validateToken(token, ACCESS_TOKEN_TYPE, accessAudiences, true);
	}

	public TokenValidationResult validateRefreshToken(String token) {
		return validateToken(token, REFRESH_TOKEN_TYPE, List.of(refreshAudience), false);
	}

	public Duration getRefreshTokenTtl() {
		return Duration.ofMillis(REFRESH_TOKEN_VALID_TIME);
	}

	public String resolveToken(HttpServletRequest req) {
		String token = req.getHeader(AuthConstant.ACCESS_TOKEN_HEADER_NAME);
		if (StringUtils.hasText(token)) {
			return token;
		}

		String authorization = req.getHeader(AuthConstant.AUTHORIZATION_HEADER_NAME);
		if (StringUtils.hasText(authorization) && authorization.startsWith(AuthConstant.BEARER_PREFIX)) {
			return authorization.substring(AuthConstant.BEARER_PREFIX.length());
		}

		return null;
	}

	public Authentication getAuthentication(Claims claims) {
		if (!ACCESS_TOKEN_TYPE.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
			throw new IllegalArgumentException("Access token is required.");
		}

		UserDetails userDetails = userAuthDetailLogic.loadUserByUsername(getUserName(claims));
		return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
	}

	public String getUserName(String token) {
		TokenValidationResult validationResult = validateAccessToken(token);
		if (!validationResult.valid()) {
			throw new IllegalArgumentException("Invalid access token.");
		}
		return getUserName(validationResult.claims());
	}

	private String getUserName(Claims claims) {
		String username = claims.get(CLAIM_USERNAME, String.class);
		if (StringUtils.hasText(username)) {
			return username;
		}
		return claims.get(CLAIM_LEGACY_USERNAME, String.class);
	}

	private TokenValidationResult validateToken(
		String token,
		String expectedTokenType,
		List<String> expectedAudiences,
		boolean requireRoles
	) {
		if (!StringUtils.hasText(token)) {
			return TokenValidationResult.invalid(TokenValidationStatus.MISSING, null);
		}

		try {
			Claims claims = Jwts.parser()
				.verifyWith(secretKey)
				.build()
				.parseSignedClaims(token)
				.getPayload();

			if (!issuer.equals(claims.getIssuer())) {
				log.warn("JWT validation failed: issuer mismatch. expectedIssuer={}, actualIssuer={}, {}",
					issuer,
					claims.getIssuer(),
					describeClaims(claims));
				return TokenValidationResult.invalid(TokenValidationStatus.INVALID_ISSUER, claims);
			}

			String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
			if (!expectedTokenType.equals(tokenType)) {
				log.warn("JWT validation failed: token type mismatch. expectedType={}, actualType={}, {}",
					expectedTokenType,
					tokenType,
					describeClaims(claims));
				return TokenValidationResult.invalid(TokenValidationStatus.INVALID_TOKEN_TYPE, claims);
			}

			if (!hasExpectedAudience(claims.get(Claims.AUDIENCE), expectedAudiences)) {
				log.warn("JWT validation failed: audience mismatch. expectedAudiences={}, actualAudience={}, {}",
					expectedAudiences,
					claims.get(Claims.AUDIENCE),
					describeClaims(claims));
				return TokenValidationResult.invalid(TokenValidationStatus.INVALID_AUDIENCE, claims);
			}

			if (!StringUtils.hasText(claims.getSubject()) || (requireRoles && !StringUtils.hasText(
				getUserName(claims)))) {
				log.warn("JWT validation failed: missing required identity claims. {}", describeClaims(claims));
				return TokenValidationResult.invalid(TokenValidationStatus.MISSING_REQUIRED_CLAIM, claims);
			}

			if (requireRoles && !hasRolesClaim(claims)) {
				log.warn("JWT validation failed: missing roles claim. {}", describeClaims(claims));
				return TokenValidationResult.invalid(TokenValidationStatus.MISSING_REQUIRED_CLAIM, claims);
			}

			return TokenValidationResult.valid(claims);
		} catch (ExpiredJwtException e) {
			log.warn("JWT validation failed: token expired. {}", describeClaims(e.getClaims()));
			return TokenValidationResult.invalid(TokenValidationStatus.EXPIRED, e.getClaims());
		} catch (MalformedJwtException e) {
			log.warn("JWT validation failed: malformed token.");
			return TokenValidationResult.invalid(TokenValidationStatus.MALFORMED, null);
		} catch (UnsupportedJwtException e) {
			log.warn("JWT validation failed: unsupported token format.");
			return TokenValidationResult.invalid(TokenValidationStatus.UNSUPPORTED, null);
		} catch (SecurityException e) {
			log.warn("JWT validation failed: invalid signature.");
			return TokenValidationResult.invalid(TokenValidationStatus.INVALID_SIGNATURE, null);
		} catch (IllegalArgumentException e) {
			log.warn("JWT validation failed: illegal argument.");
			return TokenValidationResult.invalid(TokenValidationStatus.INVALID, null);
		}
	}

	private boolean hasRolesClaim(Claims claims) {
		Object roles = claims.get(CLAIM_ROLES);
		if (roles instanceof Collection<?> collection) {
			return !collection.isEmpty();
		}
		return roles instanceof String stringRoles && StringUtils.hasText(stringRoles);
	}

	private boolean hasExpectedAudience(Object actualAudience, List<String> expectedAudiences) {
		if (actualAudience instanceof String audience) {
			return expectedAudiences.contains(audience);
		}

		if (actualAudience instanceof Collection<?> audiences) {
			return audiences.stream()
				.filter(Objects::nonNull)
				.map(String::valueOf)
				.anyMatch(expectedAudiences::contains);
		}

		return false;
	}

	private String describeClaims(Claims claims) {
		if (claims == null) {
			return "claims=unavailable";
		}

		return "sub=" + claims.getSubject()
			+ ", username=" + getUserName(claims)
			+ ", jti=" + claims.getId();
	}

	private MacAlgorithm resolveMacAlgorithm(String configuredAlgorithm) {
		return switch (configuredAlgorithm.toUpperCase()) {
			case "HS256" -> Jwts.SIG.HS256;
			case "HS384" -> Jwts.SIG.HS384;
			case "HS512" -> Jwts.SIG.HS512;
			default -> throw new IllegalArgumentException("Unsupported JWT algorithm: " + configuredAlgorithm);
		};
	}

	public enum TokenValidationStatus {
		VALID,
		MISSING,
		EXPIRED,
		MALFORMED,
		UNSUPPORTED,
		INVALID_SIGNATURE,
		INVALID_ISSUER,
		INVALID_TOKEN_TYPE,
		INVALID_AUDIENCE,
		MISSING_REQUIRED_CLAIM,
		INVALID
	}

	public record TokenValidationResult(boolean valid, TokenValidationStatus status, Claims claims) {
		public static TokenValidationResult valid(Claims claims) {
			return new TokenValidationResult(true, TokenValidationStatus.VALID, claims);
		}

		public static TokenValidationResult invalid(TokenValidationStatus status, Claims claims) {
			return new TokenValidationResult(false, status, claims);
		}
	}
}

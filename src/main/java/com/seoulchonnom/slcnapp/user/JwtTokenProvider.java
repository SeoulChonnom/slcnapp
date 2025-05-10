package com.seoulchonnom.slcnapp.user;

import com.seoulchonnom.slcnapp.user.dto.Token;
import com.seoulchonnom.slcnapp.user.service.UserDetailService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {
    @Value("${spring.jwt.secretKey}")
    private String key;
    @Value("${spring.application.name}")
    private String issuer;
    private SecretKey secretKey;

    private final UserDetailService userDetailService;

    private static final long ACCESS_TOKEN_VALID_TIME = 30 * 60 * 1000L; // access 토큰 유효시간 30분
    private static final long REFRESH_TOKEN_VALID_TIME = 14 * 24 * 60 * 60 * 1000L; // refresh 토큰 유효시간 14일

    @PostConstruct
    protected void init() {
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(key));
    }

    public Token createToken(UserDetails userDetails, int userId) {
        Date now = new Date();

        String accessToken = Jwts.builder()
                .claim("userName", userDetails.getUsername())
                .issuer(issuer)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ACCESS_TOKEN_VALID_TIME))
                .signWith(secretKey, Jwts.SIG.HS512)
                .compact();

        String refreshToken = Jwts.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + REFRESH_TOKEN_VALID_TIME))
                .signWith(secretKey, Jwts.SIG.HS512)
                .compact();

        return Token.builder().userId(userId).accessToken(accessToken).refreshToken(refreshToken).build();
    }

    public boolean validateToken(String token) {
        try {
            Jws<Claims> claimsJws = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
            return !claimsJws.getPayload().getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public String resolveToken(HttpServletRequest req) {
        return req.getHeader("X-AUTH-TOKEN");
    }

    public String getUserName(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("userName", String.class);
    }

    public Authentication getAuthentication(String token) {
        UserDetails userDetails = userDetailService.loadUserByUsername(getUserName(token));
        return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
    }
}

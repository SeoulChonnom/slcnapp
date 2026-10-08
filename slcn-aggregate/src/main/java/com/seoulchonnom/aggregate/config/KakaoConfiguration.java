package com.seoulchonnom.aggregate.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.seoulchonnom.aggregate.external.kakao.KakaoWalkingRouteGateway;
import com.seoulchonnom.aggregate.inspection.geo.DisabledWalkingRouteGateway;
import com.seoulchonnom.aggregate.inspection.geo.WalkingRouteGateway;

/**
 * 도보 경로 게이트웨이 선택. REST 키가 없으면 대역을 써서 개발 환경과 테스트가 키 없이 그대로 돈다.
 */
@Configuration
public class KakaoConfiguration {
	@Bean
	public WalkingRouteGateway walkingRouteGateway(
		@Value("${slcn.geo.kakao.base-url:https://dapi.kakao.com}") String baseUrl,
		@Value("${slcn.geo.kakao.rest-key:}") String restKey,
		@Value("${slcn.geo.kakao.timeout-seconds:5}") int timeoutSeconds) {
		if (timeoutSeconds < 1) {
			// 0은 HttpURLConnection에서 무한 대기를 뜻하므로 막는다.
			throw new IllegalStateException("slcn.geo.kakao.timeout-seconds는 1 이상이어야 합니다. 현재 값: " + timeoutSeconds);
		}
		if (restKey.isBlank()) {
			return new DisabledWalkingRouteGateway();
		}
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(timeoutSeconds * 1000);
		factory.setReadTimeout(timeoutSeconds * 1000);
		RestClient restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
		return new KakaoWalkingRouteGateway(restClient, restKey);
	}
}

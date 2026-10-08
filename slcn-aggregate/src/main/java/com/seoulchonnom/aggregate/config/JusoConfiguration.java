package com.seoulchonnom.aggregate.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.seoulchonnom.aggregate.external.juso.JusoAddressGateway;
import com.seoulchonnom.aggregate.inspection.geo.AddressGateway;
import com.seoulchonnom.aggregate.inspection.geo.DisabledAddressGateway;

/**
 * 주소 게이트웨이 선택. 두 키가 모두 없으면 대역을 써서 개발 환경과 테스트가 키 없이 그대로 돈다.
 * 키가 하나라도 있으면 실제 구현을 쓰고, 키가 없는 쪽 호출은 구현 안에서 503이 된다.
 */
@Configuration
public class JusoConfiguration {
	@Bean
	public AddressGateway addressGateway(
		@Value("${slcn.geo.juso.base-url:https://business.juso.go.kr}") String baseUrl,
		@Value("${slcn.geo.juso.search-key:}") String searchKey,
		@Value("${slcn.geo.juso.coord-key:}") String coordKey,
		@Value("${slcn.geo.juso.timeout-seconds:5}") int timeoutSeconds) {
		if (searchKey.isBlank() && coordKey.isBlank()) {
			return new DisabledAddressGateway();
		}
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(timeoutSeconds * 1000);
		factory.setReadTimeout(timeoutSeconds * 1000);
		RestClient restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
		return new JusoAddressGateway(restClient, searchKey, coordKey);
	}
}

package com.seoulchonnom.aggregate.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.seoulchonnom.aggregate.external.datagokr.MolitTradeGateway;
import com.seoulchonnom.aggregate.house.collect.DisabledTradeGateway;
import com.seoulchonnom.aggregate.house.collect.HouseSleeper;
import com.seoulchonnom.aggregate.house.collect.TradeGateway;

/**
 * 공공데이터포털 게이트웨이 선택. 서비스 키가 없으면 대역을 써서 개발 환경과 테스트가 키 없이 그대로 돈다.
 * 키는 Decoding 키여야 한다. RestClient가 URI 템플릿 변수를 한 번 인코딩하므로 Encoding 키를 넣으면 이중 인코딩된다.
 */
@Configuration
public class DataGoKrConfiguration {
	@Bean
	public TradeGateway tradeGateway(HouseProperties properties, HouseSleeper houseSleeper) {
		HouseProperties.DataGoKr dataGoKr = properties.getDataGoKr();
		RestClient restClient = restClient(dataGoKr);
		String serviceKey = dataGoKr.getServiceKey() == null ? "" : dataGoKr.getServiceKey().trim();
		if (serviceKey.isEmpty()) {
			return new DisabledTradeGateway();
		}
		return new MolitTradeGateway(restClient, serviceKey, houseSleeper);
	}

	private RestClient restClient(HouseProperties.DataGoKr dataGoKr) {
		int timeoutSeconds = dataGoKr.getTimeoutSeconds();
		if (timeoutSeconds < 1) {
			// 0은 HttpURLConnection에서 무한 대기를 뜻하므로 막는다.
			throw new IllegalStateException(
				"slcn.house.data-go-kr.timeout-seconds는 1 이상이어야 합니다. 현재 값: " + timeoutSeconds);
		}
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(timeoutSeconds * 1000);
		factory.setReadTimeout(timeoutSeconds * 1000);
		return RestClient.builder().baseUrl(dataGoKr.getBaseUrl()).requestFactory(factory).build();
	}
}

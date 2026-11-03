package com.seoulchonnom.aggregate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * slcn.house.* 설정 바인딩. 값의 의미와 기본값은 application.yml 주석이 기준이다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "slcn.house")
public class HouseProperties {
	/** 스케줄러가 @Scheduled 표현식으로 직접 읽는다. 여기서는 바인딩만 해 둔다. */
	private String nightlyCron = "-";
	private DataGoKr dataGoKr = new DataGoKr();
	private CallLimit callLimit = new CallLimit();
	/** 행안부 호출 사이 간격(ms). 쉬지 않고 연달아 부르면 20건 남짓에서 순간 호출 제한에 걸린다. */
	private long jusoIntervalMillis = 1000;

	@Getter
	@Setter
	public static class DataGoKr {
		private String baseUrl = "https://apis.data.go.kr";
		private String schoolBaseUrl = "https://api.data.go.kr";
		/** Decoding 키 */
		private String serviceKey = "";
		private int timeoutSeconds = 10;
	}

	@Getter
	@Setter
	public static class CallLimit {
		private int trade = 1000;
		private int buildingRegister = 5000;
		private int school = 100;
		private int juso = 4000;
	}
}

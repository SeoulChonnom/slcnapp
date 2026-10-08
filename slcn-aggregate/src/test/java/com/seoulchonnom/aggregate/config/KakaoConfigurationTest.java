package com.seoulchonnom.aggregate.config;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.seoulchonnom.aggregate.external.kakao.KakaoWalkingRouteGateway;
import com.seoulchonnom.aggregate.inspection.geo.DisabledWalkingRouteGateway;
import com.seoulchonnom.aggregate.inspection.geo.WalkingRouteGateway;

class KakaoConfigurationTest {
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(KakaoConfiguration.class);

	@Test
	void noKey_shouldUseDisabledGateway() {
		runner.run(context -> assertThat(context.getBean(WalkingRouteGateway.class))
			.isInstanceOf(DisabledWalkingRouteGateway.class));
		runner.withPropertyValues("slcn.geo.kakao.rest-key=  ")
			.run(context -> assertThat(context.getBean(WalkingRouteGateway.class))
				.isInstanceOf(DisabledWalkingRouteGateway.class));
	}

	@Test
	void key_shouldUseKakaoGateway() {
		runner.withPropertyValues("slcn.geo.kakao.rest-key=k")
			.run(context -> assertThat(context.getBean(WalkingRouteGateway.class))
				.isInstanceOf(KakaoWalkingRouteGateway.class));
	}
}

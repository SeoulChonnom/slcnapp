package com.seoulchonnom.aggregate.config;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import com.seoulchonnom.aggregate.external.datagokr.MolitTradeGateway;
import com.seoulchonnom.aggregate.house.collect.DisabledTradeGateway;
import com.seoulchonnom.aggregate.house.collect.TradeGateway;

class DataGoKrConfigurationTest {
	@Configuration
	@EnableConfigurationProperties(HouseProperties.class)
	static class PropertiesConfiguration {
	}

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(PropertiesConfiguration.class, HouseConfiguration.class, DataGoKrConfiguration.class);

	@Test
	void noKey_shouldUseDisabledGateway() {
		runner.run(context -> {
			assertThat(context.getBean(TradeGateway.class)).isInstanceOf(DisabledTradeGateway.class);
			assertThat(context.getBean(TradeGateway.class).available()).isFalse();
		});
		runner.withPropertyValues("slcn.house.data-go-kr.service-key=  ")
			.run(context -> assertThat(context.getBean(TradeGateway.class)).isInstanceOf(DisabledTradeGateway.class));
	}

	@Test
	void key_shouldUseMolitGateway() {
		runner.withPropertyValues("slcn.house.data-go-kr.service-key=k")
			.run(context -> assertThat(context.getBean(TradeGateway.class)).isInstanceOf(MolitTradeGateway.class));
	}

	@Test
	void timeoutBelowOne_shouldFailAtStartup() {
		for (String value : new String[] {"0", "-1"}) {
			runner.withPropertyValues("slcn.house.data-go-kr.service-key=k",
				"slcn.house.data-go-kr.timeout-seconds=" + value)
				.run(context -> assertThat(context).hasFailed().getFailure().rootCause()
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("slcn.house.data-go-kr.timeout-seconds"));
		}
	}

	@Test
	void properties_shouldBindCallLimitsAndDefaults() {
		runner.withPropertyValues("slcn.house.call-limit.trade=77", "slcn.house.call-limit.building-register=88")
			.run(context -> {
				HouseProperties properties = context.getBean(HouseProperties.class);

				assertThat(properties.getCallLimit().getTrade()).isEqualTo(77);
				assertThat(properties.getCallLimit().getBuildingRegister()).isEqualTo(88);
				assertThat(properties.getCallLimit().getJuso()).isEqualTo(4000);
				assertThat(properties.getDataGoKr().getBaseUrl()).isEqualTo("https://apis.data.go.kr");
			});
	}
}

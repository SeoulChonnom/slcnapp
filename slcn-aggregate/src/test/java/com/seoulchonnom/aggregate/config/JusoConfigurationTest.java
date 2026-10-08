package com.seoulchonnom.aggregate.config;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.seoulchonnom.aggregate.external.juso.JusoAddressGateway;
import com.seoulchonnom.aggregate.inspection.geo.AddressGateway;
import com.seoulchonnom.aggregate.inspection.geo.DisabledAddressGateway;

class JusoConfigurationTest {
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(JusoConfiguration.class);

	@Test
	void noKeys_shouldUseDisabledGateway() {
		runner.run(context -> assertThat(context.getBean(AddressGateway.class))
			.isInstanceOf(DisabledAddressGateway.class));
		runner.withPropertyValues("slcn.geo.juso.search-key=", "slcn.geo.juso.coord-key=  ")
			.run(context -> assertThat(context.getBean(AddressGateway.class))
				.isInstanceOf(DisabledAddressGateway.class));
	}

	@Test
	void anyKey_shouldUseJusoGateway() {
		runner.withPropertyValues("slcn.geo.juso.search-key=k")
			.run(context -> assertThat(context.getBean(AddressGateway.class)).isInstanceOf(JusoAddressGateway.class));
		runner.withPropertyValues("slcn.geo.juso.coord-key=k")
			.run(context -> assertThat(context.getBean(AddressGateway.class)).isInstanceOf(JusoAddressGateway.class));
	}
}

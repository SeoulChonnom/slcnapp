package com.seoulchonnom.aggregate.house.collect;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CallBudgetTest {
	@Test
	void consume_shouldStopAtLimit() {
		CallBudget budget = new CallBudget("trade", 2, 0);

		assertThat(budget.consume()).isTrue();
		assertThat(budget.consume()).isTrue();
		assertThat(budget.consume()).isFalse();
		assertThat(budget.used()).isEqualTo(2);
		assertThat(budget.remaining()).isZero();
	}

	@Test
	void consume_shouldSubtractCallsAlreadyUsedToday() {
		CallBudget budget = new CallBudget("trade", 5, 3);

		assertThat(budget.remaining()).isEqualTo(2);
		assertThat(budget.consume()).isTrue();
		assertThat(budget.consume()).isTrue();
		assertThat(budget.consume()).isFalse();
		assertThat(budget.used()).isEqualTo(2);
	}

	@Test
	void consume_shouldNeverCallWhenAlreadyOverLimit() {
		CallBudget budget = new CallBudget("trade", 5, 9);

		assertThat(budget.remaining()).isZero();
		assertThat(budget.consume()).isFalse();
		assertThat(budget.used()).isZero();
	}
}

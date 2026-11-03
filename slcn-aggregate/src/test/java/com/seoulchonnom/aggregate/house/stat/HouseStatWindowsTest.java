package com.seoulchonnom.aggregate.house.stat;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class HouseStatWindowsTest {
	@Test
	void of_shouldMatchDesignTableFor20261020() {
		HouseStatWindows w = HouseStatWindows.of(LocalDate.of(2026, 10, 20));

		assertThat(w.today()).isEqualTo(LocalDate.of(2026, 10, 20));
		// (start, end]라 start는 제외되는 날짜다. 표의 "2026-07-21 ~"는 start + 1일이다.
		assertThat(w.w3mStart()).isEqualTo(LocalDate.of(2026, 7, 20));
		assertThat(w.w6mStart()).isEqualTo(LocalDate.of(2026, 4, 20));
		assertThat(w.w12mStart()).isEqualTo(LocalDate.of(2025, 10, 20));
		assertThat(w.w3m1yStart()).isEqualTo(LocalDate.of(2025, 7, 20));
		assertThat(w.w3m1yEnd()).isEqualTo(LocalDate.of(2025, 10, 20));
		assertThat(w.w3m3yStart()).isEqualTo(LocalDate.of(2023, 7, 20));
		assertThat(w.w3m3yEnd()).isEqualTo(LocalDate.of(2023, 10, 20));
		assertThat(w.outlierSince()).isEqualTo(LocalDate.of(2023, 6, 20));
	}

	@Test
	void of_shouldClampToMonthEndOn0531() {
		HouseStatWindows w = HouseStatWindows.of(LocalDate.of(2026, 5, 31));

		assertThat(w.w3mStart()).isEqualTo(LocalDate.of(2026, 2, 28));
		assertThat(w.w6mStart()).isEqualTo(LocalDate.of(2025, 11, 30));
		assertThat(w.w12mStart()).isEqualTo(LocalDate.of(2025, 5, 31));
		assertThat(w.w3m1yStart()).isEqualTo(LocalDate.of(2025, 2, 28));
		assertThat(w.w3m1yEnd()).isEqualTo(LocalDate.of(2025, 5, 31));
		assertThat(w.w3m3yStart()).isEqualTo(LocalDate.of(2023, 2, 28));
		assertThat(w.w3m3yEnd()).isEqualTo(LocalDate.of(2023, 5, 31));
		assertThat(w.outlierSince()).isEqualTo(LocalDate.of(2023, 1, 31));
	}

	@Test
	void of_shouldClampToMonthEndOn0331() {
		HouseStatWindows w = HouseStatWindows.of(LocalDate.of(2026, 3, 31));

		assertThat(w.w3mStart()).isEqualTo(LocalDate.of(2025, 12, 31));
		assertThat(w.w6mStart()).isEqualTo(LocalDate.of(2025, 9, 30));
		assertThat(w.w12mStart()).isEqualTo(LocalDate.of(2025, 3, 31));
		assertThat(w.w3m1yStart()).isEqualTo(LocalDate.of(2024, 12, 31));
		assertThat(w.w3m3yStart()).isEqualTo(LocalDate.of(2022, 12, 31));
		assertThat(w.w3m3yEnd()).isEqualTo(LocalDate.of(2023, 3, 31));
		assertThat(w.outlierSince()).isEqualTo(LocalDate.of(2022, 11, 30));
	}
}

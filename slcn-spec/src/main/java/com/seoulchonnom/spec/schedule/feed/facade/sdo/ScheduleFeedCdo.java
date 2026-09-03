package com.seoulchonnom.spec.schedule.feed.facade.sdo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleFeedCdo {
	@NotBlank(message = "name은 필수입니다.")
	@Size(max = 100, message = "name은 100자 이하여야 합니다.")
	@Schema(description = "일정 피드 이름", example = "Google Calendar")
	private String name;
}

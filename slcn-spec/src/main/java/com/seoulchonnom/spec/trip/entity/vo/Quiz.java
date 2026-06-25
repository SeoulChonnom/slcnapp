package com.seoulchonnom.spec.trip.entity.vo;

import java.util.List;

import com.seoulchonnom.spec.common.entity.vo.JsonSerializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class Quiz implements JsonSerializable {
	private String title;
	private String correctOptionId;
	private String answerTitle;
	private String answerText;
	private String errorTitle;
	private String errorText;
	private List<Option> options;

}

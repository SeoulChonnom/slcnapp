package com.seoulchonnom.spec.trip.entity;

import java.util.List;

import org.springframework.beans.BeanUtils;

import com.seoulchonnom.spec.common.entity.DomainEntity;
import com.seoulchonnom.spec.trip.facade.sdo.TripCdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripInfoRdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripListRdo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Builder
@AllArgsConstructor
@Getter
@Setter
public class Trip extends DomainEntity {
	private String date;
	private String type;
	private String name;
	private String logo;

	private String firstMap;
	private String secondMap;

	private String nextButtonText;
	private String previousButtonText;

	private String driveUrl;

	private String quizTitle;
	private String quizAnswer;
	private String quizAnswerTitle;
	private String quizAnswerText;
	private String quizErrorTitle;
	private String quizErrorText;

	private List<Quiz> quizList;

	public Trip(TripCdo tripCdo, String id) {
		super(id);
		BeanUtils.copyProperties(tripCdo, this);
		this.quizList = tripCdo.getQuizCdoList().stream().map(Quiz::new).toList();
	}

	public TripListRdo toListRdo() {
		TripListRdo tripListRdo = new TripListRdo();
		BeanUtils.copyProperties(this, tripListRdo, "quizList");
		tripListRdo.setQuizList(this.quizList.stream().map(Quiz::toRdo).toList());
		return tripListRdo;
	}

	public TripInfoRdo toInfoRdo() {
		TripInfoRdo tripInfoRdo = new TripInfoRdo();
		BeanUtils.copyProperties(this, tripInfoRdo);
		return tripInfoRdo;
	}
}

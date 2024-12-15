package com.seoulchonnom.slcnapp.trip.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.seoulchonnom.slcnapp.trip.FileUtils;
import com.seoulchonnom.slcnapp.trip.domain.Quiz;
import com.seoulchonnom.slcnapp.trip.dto.QuizRegisterRequest;
import com.seoulchonnom.slcnapp.trip.dto.TripRegisterRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seoulchonnom.slcnapp.trip.domain.Trip;
import com.seoulchonnom.slcnapp.trip.dto.TripInfoResponse;
import com.seoulchonnom.slcnapp.trip.dto.TripListResponse;
import com.seoulchonnom.slcnapp.trip.exception.TripNotFoundException;
import com.seoulchonnom.slcnapp.trip.repository.TripRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional
public class TripService {
	private final TripRepository tripRepository;
	private final FileUtils fileUtils;

	private final String logoPath = "logo/";
	private final String mapPath = "map/";

	public List<TripListResponse> getAllTripList() {
		return tripRepository.findAll().stream()
			.map(TripListResponse::from)
			.collect(Collectors.toList());
	}

	public TripInfoResponse getTripByDate(String date) {
		Trip trip = tripRepository.findByDate(date).orElseThrow(TripNotFoundException::new);
		return TripInfoResponse.from(trip);
	}

	public boolean registerTrip(TripRegisterRequest tripRegisterRequest, MultipartFile logo, MultipartFile map1, MultipartFile map2){
		String logoFile;
		String map1File;
		String map2File = "";
		try {
			logoFile = fileUtils.saveImages(logo, logoPath);
			map1File = fileUtils.saveImages(map1, mapPath);
			if(!map2.isEmpty()){
				map2File = fileUtils.saveImages(map2, mapPath);
			}
		} catch(IOException e) {
			return false;
		}

		Trip trip = tripRegisterRequest.of(logoFile, map1File);

		if (!map2.isEmpty()){
			trip.setMap2(map2File);
		}

		List<Quiz> quizList = tripRegisterRequest.getQuizRegisterRequestList()
				.stream()
				.map(QuizRegisterRequest::of)
				.toList();

		trip.setQuizList(quizList);

		tripRepository.save(trip);

		return true;
	}
}

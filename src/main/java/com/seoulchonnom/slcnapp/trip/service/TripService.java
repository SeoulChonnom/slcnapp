package com.seoulchonnom.slcnapp.trip.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.seoulchonnom.slcnapp.trip.FileUtils;
import com.seoulchonnom.slcnapp.trip.domain.Quiz;
import com.seoulchonnom.slcnapp.trip.domain.Trip;
import com.seoulchonnom.slcnapp.trip.dto.ImageFile;
import com.seoulchonnom.slcnapp.trip.dto.TripInfoResponse;
import com.seoulchonnom.slcnapp.trip.dto.TripListResponse;
import com.seoulchonnom.slcnapp.trip.dto.TripRegisterRequest;
import com.seoulchonnom.slcnapp.trip.exception.FilePathInvaildException;
import com.seoulchonnom.slcnapp.trip.exception.TripFileUploadException;
import com.seoulchonnom.slcnapp.trip.exception.TripNotFoundException;
import com.seoulchonnom.slcnapp.trip.repository.TripRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class TripService {
	private final TripRepository tripRepository;
	private final FileUtils fileUtils;

	@Value("${upload.path}")
	private String directory;
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

	public boolean registerTrip(TripRegisterRequest tripRegisterRequest, MultipartFile logo, MultipartFile map1,
		MultipartFile map2) {

		String logoFile, map1File;
		String map2File = "";

		try {
			logoFile = fileUtils.saveImages(logo, logoPath);
			map1File = fileUtils.saveImages(map1, mapPath);

			map2File = "";
			if (map2 != null) {
				map2File = fileUtils.saveImages(map2, mapPath);
			}
		} catch (IOException e) {
			throw new TripFileUploadException();
		}
		Trip trip = tripRegisterRequest.of(logoFile, map1File);

		if (map2 != null) {
			trip.setMap2(map2File);
		}

		List<Quiz> quizList = tripRegisterRequest.getQuizRegisterRequestList()
			.stream()
			.map(a -> a.of(trip))
			.toList();

		trip.setQuizList(quizList);

		tripRepository.save(trip);

		return true;
	}

	public ImageFile getImageFile(String path) {
		try {
			Path filePath = Paths.get(directory + path);
			return ImageFile.builder().image(Files.readAllBytes(filePath)).mimeType(Files.probeContentType(filePath))
				.build();

		} catch (IOException e) {
			throw new FilePathInvaildException();
		}
	}
}

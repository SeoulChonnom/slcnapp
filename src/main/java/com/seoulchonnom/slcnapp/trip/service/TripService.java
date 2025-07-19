package com.seoulchonnom.slcnapp.trip.service;

import com.seoulchonnom.slcnapp.depot.dto.ImageFile;
import com.seoulchonnom.slcnapp.depot.service.DepotService;
import com.seoulchonnom.slcnapp.trip.domain.Quiz;
import com.seoulchonnom.slcnapp.trip.domain.Trip;
import com.seoulchonnom.slcnapp.trip.dto.TripInfoResponse;
import com.seoulchonnom.slcnapp.trip.dto.TripListResponse;
import com.seoulchonnom.slcnapp.trip.dto.TripRegisterRequest;
import com.seoulchonnom.slcnapp.trip.exception.TripNotFoundException;
import com.seoulchonnom.slcnapp.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class TripService {
    private final TripRepository tripRepository;
    private final DepotService depotService;

    public List<TripListResponse> getAllTripList() {
        return tripRepository.findAllByOrderByDateDesc().stream()
                .map(TripListResponse::from)
                .collect(Collectors.toList());
    }

    public TripInfoResponse getTripByDate(String date) {
        Trip trip = tripRepository.findByDate(date).orElseThrow(TripNotFoundException::new);
        return TripInfoResponse.from(trip);
    }

    public boolean registerTrip(TripRegisterRequest tripRegisterRequest) {
        Trip trip = tripRegisterRequest.of();

        List<Quiz> quizList = tripRegisterRequest.getQuizRegisterRequestList()
                .stream()
                .map(a -> a.of(trip))
                .toList();

        trip.setQuizList(quizList);

        tripRepository.save(trip);

        return true;
    }

    public ImageFile getImageFile(String path) {
        return depotService.getImageFile(path);
    }
}

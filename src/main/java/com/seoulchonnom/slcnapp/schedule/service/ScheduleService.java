package com.seoulchonnom.slcnapp.schedule.service;

import com.seoulchonnom.slcnapp.schedule.domain.Schedule;
import com.seoulchonnom.slcnapp.schedule.dto.ScheduleRegisterRequest;
import com.seoulchonnom.slcnapp.schedule.dto.ScheduleResponse;
import com.seoulchonnom.slcnapp.schedule.exception.InvalidScheduleDateException;
import com.seoulchonnom.slcnapp.schedule.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ScheduleService {
    private final ScheduleRepository scheduleRepository;

    @Transactional(readOnly = true)
    public List<ScheduleResponse> getSchedulesForNow() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

        int year = now.getYear();
        int month = now.getMonthValue();

        return getSchedulesForMonth(year, month);
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> getSchedulesForMonth(int year, int month) {
        if(!isValidDate(year, month)) {
            throw new InvalidScheduleDateException();
        }

        LocalDateTime startDate = LocalDateTime.of(year, month, 1,0,0,0);
        LocalDateTime endDate = startDate.plusMonths(1);

        List<Schedule> scheduleList = scheduleRepository.findAllByStartBetween(startDate, endDate);

        return scheduleList.stream().map(ScheduleResponse::from).collect(Collectors.toList());
    }

    public String registerSchedule(ScheduleRegisterRequest request) {
        Schedule schedule = Schedule.from(request);

        scheduleRepository.save(schedule);
        return schedule.getId();
    }

    private boolean isValidDate(int year, int month) {
        if(year < 1900 || year > 2100) {
            return false;
        }

        return month >= 1 && month <= 12;
    }

    private boolean isValidRegisterRequest(ScheduleRegisterRequest request) {

        return true;
    }
}

package com.seoulchonnom.slcnapp.schedule.controller;

import com.seoulchonnom.slcnapp.common.dto.BaseResponse;
import com.seoulchonnom.slcnapp.schedule.dto.ScheduleRegisterRequest;
import com.seoulchonnom.slcnapp.schedule.dto.ScheduleSearchRequest;
import com.seoulchonnom.slcnapp.schedule.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.seoulchonnom.slcnapp.schedule.ScheduleConstant.REGISTER_SCHEDULE_COMPLETE_MESSAGE;
import static com.seoulchonnom.slcnapp.schedule.ScheduleConstant.RETRIEVE_SCHEDULE_COMPLETE_MESSAGE;

@RestController
@RequestMapping("/schedule")
@RequiredArgsConstructor
public class ScheduleController implements ScheduleControllerDocs {

    private final ScheduleService scheduleService;

    @Override
    @GetMapping("/")
    public ResponseEntity<BaseResponse> getSchedulesForNow() {
        return new ResponseEntity<>(
                BaseResponse.from(true, RETRIEVE_SCHEDULE_COMPLETE_MESSAGE, scheduleService.getSchedulesForNow()),
                HttpStatus.OK);
    }

    @Override
    @GetMapping("/date")
    public ResponseEntity<BaseResponse> getSchedulesForYearAndMonth(ScheduleSearchRequest request) {
        return new ResponseEntity<>(
                BaseResponse.from(true, RETRIEVE_SCHEDULE_COMPLETE_MESSAGE, scheduleService.getSchedulesForMonth(request.getYear(), request.getMonth())),
                HttpStatus.OK);
    }

    @Override
    @PostMapping("/register")
    public ResponseEntity<BaseResponse> registerSchedule(@RequestBody ScheduleRegisterRequest request) {
        return new ResponseEntity<>(
                BaseResponse.from(true, REGISTER_SCHEDULE_COMPLETE_MESSAGE, scheduleService.registerSchedule(request)),
                HttpStatus.OK
        );
    }


}

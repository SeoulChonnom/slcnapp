package com.seoulchonnom.slcnapp.schedule.domain;

import com.seoulchonnom.slcnapp.schedule.dto.ScheduleRegisterRequest;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.seoulchonnom.slcnapp.schedule.ScheduleConstant.DATE_TIME_FORMATTER;

@Entity
@NoArgsConstructor
@Builder
@AllArgsConstructor
@Getter
public class Schedule {
    @Id
    private String id;

    @Column(nullable = false)
    private String calendarId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String body;

    @Column(nullable = false)
    private boolean isAllDay;

    @Column(nullable = false)
    private LocalDateTime start;

    @Column(nullable = false)
    private LocalDateTime end;

    private Long goingDuration;
    private Long comingDuration;

    @Column(nullable = false)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScheduleCategory category;

    private String dueDateClass;
    private String recurrenceRule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScheduleState state;

    private boolean isVisible;
    private boolean isPending;
    private boolean isFocused;
    private boolean isReadOnly;
    private boolean isPrivate;

    private String color;
    private String backgroundColor;
    private String dragBackgroundColor;
    private String borderColor;
    private String customStyle;

    public static Schedule from(ScheduleRegisterRequest request) {
        String id = UUID.randomUUID().toString();
        System.out.println(request.getStart());
        return Schedule.builder()
                .id(id)
                .calendarId(request.getCalendarId())
                .title(request.getTitle())
                .body(request.getBody())
                .isAllDay(request.isAllDay())
                .start(LocalDateTime.parse(request.getStart(), DATE_TIME_FORMATTER))
                .end(LocalDateTime.parse(request.getEnd(), DATE_TIME_FORMATTER))
                .goingDuration(request.getGoingDuration())
                .comingDuration(request.getComingDuration())
                .location(request.getLocation())
                .category(request.getCategory())
                .dueDateClass(request.getDueDateClass())
                .recurrenceRule(request.getRecurrenceRule())
                .state(request.getState())
                .isVisible(request.isVisible())
                .isPending(request.isPending())
                .isFocused(request.isFocused())
                .isReadOnly(request.isReadOnly())
                .isPrivate(request.isPrivate())
                .color(request.getColor())
                .backgroundColor(request.getBackgroundColor())
                .dragBackgroundColor(request.getDragBackgroundColor())
                .borderColor(request.getBorderColor())
                .customStyle(request.getCustomStyle())
                .build();
    }
}
/*
  id?: string;
  calendarId?: string;
  title?: string;
  body?: string;
  isAllday?: boolean;
  start?: Date | string | number | TZDate;
  end?: Date | string | number | TZDate;
  goingDuration?: number;
  comingDuration?: number;
  location?: string;
  attendees?: string[];
  category?: 'milestone' | 'task' | 'allday' | 'time';
  recurrenceRule?: string;
  state?: 'Busy' | 'Free';
  isVisible?: boolean;
  isPending?: boolean;
  isFocused?: boolean;
  isReadOnly?: boolean;
  isPrivate?: boolean;
  color?: string;
  backgroundColor?: string;
  dragBackgroundColor?: string;
  borderColor?: string;
  customStyle?: JS

    "title": "test",
    "location": "test",
    "isAllday": false,
    "isPrivate": false,
    "state": "Busy",
    "calendarId": "cal1",
    "start": {
        "tzOffset": null,
        "d": {
            "d": "2025-03-05T15:00:00.000Z"
        }
    },
    "end": {
        "tzOffset": null,
        "d": {
            "d": "2025-03-05T15:00:00.000Z"
        }
    },
    "id": "36be05d0-3584-477a-9992-c4f49c27272f",
    "category": "time"

 */
package com.airplanehome.flight.scheduler;

import com.airplanehome.flight.model.Tracking;
import com.airplanehome.flight.service.FlightService;
import com.airplanehome.flight.service.KakaoNotificationService;
import com.airplanehome.flight.service.PriceDropNotification;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PriceTrackerSchedulerTest {

    @Test
    void shouldSkipDuplicateNotification() {
        FlightService flightService = mock(FlightService.class);
        KakaoNotificationService kakaoNotificationService = mock(KakaoNotificationService.class);
        PriceTrackerScheduler scheduler = new PriceTrackerScheduler(
                flightService,
                kakaoNotificationService,
                BigDecimal.valueOf(10000),
                BigDecimal.valueOf(5));

        PriceDropNotification notification = new PriceDropNotification(
                1L,
                "ICN",
                "NRT",
                LocalDate.of(2026, 5, 1),
                BigDecimal.valueOf(320000),
                BigDecimal.valueOf(270000),
                "KRW");
        Tracking tracking = new Tracking();
        tracking.setId(1L);
        tracking.setOrigin("ICN");
        tracking.setDestination("NRT");
        tracking.setDepartureDate(LocalDate.of(2026, 5, 1));
        tracking.setPhoneNumber("01012345678");
        tracking.setKakaoOptIn(Boolean.TRUE);
        tracking.setLastNotifiedPrice(BigDecimal.valueOf(270000));

        given(flightService.checkTrackedPrices()).willReturn(Collections.singletonList(notification));
        given(flightService.getTracking(1L)).willReturn(tracking);

        scheduler.checkTrackedPrices();

        verify(kakaoNotificationService, never()).sendAlimTalk(same(tracking), anyInt(), anyInt());
        verify(flightService, never()).saveTracking(same(tracking));
    }

    @Test
    void shouldSaveLastNotifiedPriceAfterSuccessfulSend() {
        FlightService flightService = mock(FlightService.class);
        KakaoNotificationService kakaoNotificationService = mock(KakaoNotificationService.class);
        PriceTrackerScheduler scheduler = new PriceTrackerScheduler(
                flightService,
                kakaoNotificationService,
                BigDecimal.valueOf(10000),
                BigDecimal.valueOf(5));

        PriceDropNotification notification = new PriceDropNotification(
                1L,
                "ICN",
                "NRT",
                LocalDate.of(2026, 5, 1),
                BigDecimal.valueOf(320000),
                BigDecimal.valueOf(270000),
                "KRW");
        Tracking tracking = new Tracking();
        tracking.setId(1L);
        tracking.setOrigin("ICN");
        tracking.setDestination("NRT");
        tracking.setDepartureDate(LocalDate.of(2026, 5, 1));
        tracking.setPhoneNumber("01012345678");
        tracking.setKakaoOptIn(Boolean.TRUE);

        given(flightService.checkTrackedPrices()).willReturn(Collections.singletonList(notification));
        given(flightService.getTracking(1L)).willReturn(tracking);
        given(kakaoNotificationService.sendAlimTalk(tracking, 320000, 270000)).willReturn(true);

        scheduler.checkTrackedPrices();

        verify(kakaoNotificationService).sendAlimTalk(tracking, 320000, 270000);
        verify(flightService).saveTracking(tracking);
        org.junit.jupiter.api.Assertions.assertEquals(BigDecimal.valueOf(270000), tracking.getLastNotifiedPrice());
    }

    @Test
    void shouldSkipNotificationBelowThreshold() {
        FlightService flightService = mock(FlightService.class);
        KakaoNotificationService kakaoNotificationService = mock(KakaoNotificationService.class);
        PriceTrackerScheduler scheduler = new PriceTrackerScheduler(
                flightService,
                kakaoNotificationService,
                BigDecimal.valueOf(10000),
                BigDecimal.valueOf(5));

        PriceDropNotification notification = new PriceDropNotification(
                1L,
                "ICN",
                "NRT",
                LocalDate.of(2026, 5, 1),
                BigDecimal.valueOf(320000),
                BigDecimal.valueOf(315000),
                "KRW");
        Tracking tracking = new Tracking();
        tracking.setId(1L);
        tracking.setOrigin("ICN");
        tracking.setDestination("NRT");
        tracking.setDepartureDate(LocalDate.of(2026, 5, 1));
        tracking.setPhoneNumber("01012345678");
        tracking.setKakaoOptIn(Boolean.TRUE);

        given(flightService.checkTrackedPrices()).willReturn(Collections.singletonList(notification));
        given(flightService.getTracking(1L)).willReturn(tracking);

        scheduler.checkTrackedPrices();

        verify(kakaoNotificationService, never()).sendAlimTalk(same(tracking), anyInt(), anyInt());
        verify(flightService, never()).saveTracking(same(tracking));
    }
}

package com.airplanehome.flight.service;

import com.airplanehome.flight.model.KakaoAuthConnection;
import com.airplanehome.flight.model.Tracking;
import com.airplanehome.flight.repository.PriceHistoryRepository;
import com.airplanehome.flight.repository.TrackingRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class FlightServiceTrackingAccessTest {

    @Test
    void shouldReturnOnlyTrackingsOwnedByBrowserToken() throws Exception {
        TrackingRepository trackingRepository = mock(TrackingRepository.class);
        FlightService flightService = new FlightService(
                trackingRepository,
                mock(PriceHistoryRepository.class),
                mock(ExchangeRateService.class),
                mock(FlightPrefetchService.class),
                mock(KakaoAuthService.class));

        Tracking tracking = tracking(1L, hash("browser-token"), null, LocalDateTime.of(2026, 5, 14, 11, 0));
        given(trackingRepository.findByOwnerTokenHashOrderByLastUpdatedAtDesc(anyString()))
                .willReturn(Collections.singletonList(tracking));

        List<Tracking> results = flightService.getTrackings("browser-token", null);

        assertEquals(1, results.size());
        assertEquals(Long.valueOf(1L), results.get(0).getId());
        verify(trackingRepository, never()).findByKakaoUserIdOrderByLastUpdatedAtDesc(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void shouldMergeBrowserAndKakaoTrackingsWithoutDuplicates() throws Exception {
        TrackingRepository trackingRepository = mock(TrackingRepository.class);
        KakaoAuthService kakaoAuthService = mock(KakaoAuthService.class);
        FlightService flightService = new FlightService(
                trackingRepository,
                mock(PriceHistoryRepository.class),
                mock(ExchangeRateService.class),
                mock(FlightPrefetchService.class),
                kakaoAuthService);

        Tracking sharedTracking = tracking(1L, hash("browser-token"), 1001L, LocalDateTime.of(2026, 5, 14, 12, 0));
        Tracking kakaoOnlyTracking = tracking(2L, null, 1001L, LocalDateTime.of(2026, 5, 14, 13, 0));
        KakaoAuthConnection connection = new KakaoAuthConnection();
        connection.setId("connection-1");
        connection.setKakaoUserId(1001L);

        given(kakaoAuthService.getConnection("connection-1")).willReturn(connection);
        given(trackingRepository.findByOwnerTokenHashOrderByLastUpdatedAtDesc(anyString()))
                .willReturn(Collections.singletonList(sharedTracking));
        given(trackingRepository.findByKakaoUserIdOrderByLastUpdatedAtDesc(1001L))
                .willReturn(Arrays.asList(sharedTracking, kakaoOnlyTracking));

        List<Tracking> results = flightService.getTrackings("browser-token", "connection-1");

        assertEquals(2, results.size());
        assertEquals(Long.valueOf(2L), results.get(0).getId());
        assertEquals(Long.valueOf(1L), results.get(1).getId());
    }

    @Test
    void shouldRejectDeleteWhenViewerDoesNotOwnTracking() throws Exception {
        TrackingRepository trackingRepository = mock(TrackingRepository.class);
        PriceHistoryRepository priceHistoryRepository = mock(PriceHistoryRepository.class);
        FlightService flightService = new FlightService(
                trackingRepository,
                priceHistoryRepository,
                mock(ExchangeRateService.class),
                mock(FlightPrefetchService.class),
                mock(KakaoAuthService.class));

        Tracking tracking = tracking(3L, hash("owner-a"), null, LocalDateTime.of(2026, 5, 14, 10, 0));
        given(trackingRepository.findById(3L)).willReturn(Optional.of(tracking));

        assertThrows(TrackingAccessDeniedException.class, () -> flightService.deleteTracking(3L, "owner-b", null));
        verify(priceHistoryRepository, never()).deleteByTrackingId(3L);
        verify(trackingRepository, never()).deleteById(3L);
    }

    @Test
    void shouldAllowDeleteWhenKakaoConnectionMatchesTrackingOwner() {
        TrackingRepository trackingRepository = mock(TrackingRepository.class);
        PriceHistoryRepository priceHistoryRepository = mock(PriceHistoryRepository.class);
        KakaoAuthService kakaoAuthService = mock(KakaoAuthService.class);
        FlightService flightService = new FlightService(
                trackingRepository,
                priceHistoryRepository,
                mock(ExchangeRateService.class),
                mock(FlightPrefetchService.class),
                kakaoAuthService);

        Tracking tracking = tracking(4L, null, 1001L, LocalDateTime.of(2026, 5, 14, 9, 0));
        KakaoAuthConnection connection = new KakaoAuthConnection();
        connection.setId("connection-1");
        connection.setKakaoUserId(1001L);

        given(trackingRepository.findById(4L)).willReturn(Optional.of(tracking));
        given(kakaoAuthService.getConnection("connection-1")).willReturn(connection);

        flightService.deleteTracking(4L, null, "connection-1");

        verify(priceHistoryRepository).deleteByTrackingId(4L);
        verify(trackingRepository).deleteById(4L);
    }

    private Tracking tracking(Long id, String ownerTokenHash, Long kakaoUserId, LocalDateTime lastUpdatedAt) {
        Tracking tracking = new Tracking();
        tracking.setId(id);
        tracking.setOwnerTokenHash(ownerTokenHash);
        tracking.setKakaoUserId(kakaoUserId);
        tracking.setLastUpdatedAt(lastUpdatedAt);
        return tracking;
    }

    private static String hash(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}

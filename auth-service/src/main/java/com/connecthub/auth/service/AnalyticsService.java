package com.connecthub.auth.service;

import com.connecthub.auth.client.MediaClient;
import com.connecthub.auth.client.MessageClient;
import com.connecthub.auth.client.PresenceClient;
import com.connecthub.auth.client.RoomClient;
import com.connecthub.auth.entity.AnalyticsSnapshot;
import com.connecthub.auth.repository.AnalyticsSnapshotRepository;
import com.connecthub.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AnalyticsService — Periodic platform metric snapshots.
 *
 * Every 15 minutes, captures a snapshot of:
 *   - Live online user count (from presence-service via Feign)
 *   - Total, active, and suspended user counts (from the local user table)
 *
 * Snapshots are kept for 7 days (672 data points at 15-min cadence) and then
 * purged to prevent unbounded table growth. The last 96 snapshots (24h) are
 * served to the admin dashboard for the analytics charts.
 *
 * If presence-service is unavailable the online count defaults to 0; the rest
 * of the snapshot is still written so historical user counts are not lost.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final AnalyticsSnapshotRepository snapshotRepo;
    private final UserRepository userRepo;
    private final PresenceClient presenceClient;
    private final MessageClient messageClient;
    private final RoomClient roomClient;
    private final MediaClient mediaClient;

    @Scheduled(fixedDelay = 15 * 60 * 1000)
    @Transactional
    public void snapshot() {
        try {
            int online = 0;
            try {
                online = presenceClient.getOnlineCount();
            } catch (Exception e) {
                log.warn("Could not fetch online count for analytics snapshot: {}", e.getMessage());
            }

            long total     = userRepo.count();
            long active    = userRepo.countByActiveTrue();
            long suspended = userRepo.countByActiveFalse();

            long messagesToday = 0;
            try { messagesToday = messageClient.countToday(); }
            catch (Exception e) { log.warn("Could not fetch message count for analytics: {}", e.getMessage()); }

            long activeRooms = 0;
            try { activeRooms = roomClient.countActiveRooms(); }
            catch (Exception e) { log.warn("Could not fetch active room count for analytics: {}", e.getMessage()); }

            double storageMb = 0;
            try { storageMb = mediaClient.getTotalStorageMb(); }
            catch (Exception e) { log.warn("Could not fetch storage total for analytics: {}", e.getMessage()); }

            snapshotRepo.save(AnalyticsSnapshot.builder()
                    .onlineCount(online)
                    .totalUsers(total)
                    .activeUsers(active)
                    .suspendedUsers(suspended)
                    .messagesToday(messagesToday)
                    .activeRooms(activeRooms)
                    .storageMb(storageMb)
                    .build());

            snapshotRepo.deleteOlderThan(LocalDateTime.now().minusDays(7));
            log.debug("Analytics snapshot: online={} total={} active={} suspended={} msgs={} rooms={} storageMb={}",
                    online, total, active, suspended, messagesToday, activeRooms, storageMb);
        } catch (Exception e) {
            log.error("Analytics snapshot failed: {}", e.getMessage());
        }
    }

    /**
     * Returns the last 96 snapshots (24 h at 15-min intervals) ordered newest-first.
     * The frontend reverses these so the chart reads left-to-right oldest-to-newest.
     */
    @Transactional(readOnly = true)
    public List<AnalyticsSnapshot> getRecentSnapshots() {
        return snapshotRepo.findTop96ByOrderBySnapshotAtDesc();
    }
}

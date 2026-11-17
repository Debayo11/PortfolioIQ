package com.portfolioiq.notification;

import com.portfolioiq.common.ApiException;
import com.portfolioiq.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class NotificationService {
    public record NotificationResponse(Long id, String type, String title, String message, boolean read,
                                       Instant createdAt) {
        static NotificationResponse of(Notification n) {
            return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getMessage(),
                    n.getReadAt() != null, n.getCreatedAt());
        }
    }

    private final NotificationRepository repo;
    private final Clock clock;

    public NotificationService(NotificationRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    /** Joins the caller's transaction, so a rolled-back action leaves no notification behind. */
    public void notify(Long userId, NotificationType type, String title, String message) {
        repo.save(new Notification(userId, type, title, message));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(Long userId, boolean unreadOnly, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        var result = unreadOnly ? repo.findByUserIdAndReadAtIsNull(userId, pageable) : repo.findByUserId(userId, pageable);
        return PageResponse.of(result.map(NotificationResponse::of));
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) { return repo.countByUserIdAndReadAtIsNull(userId); }

    @Transactional
    public void markRead(Long userId, Long id) {
        Notification n = repo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Notification not found"));
        n.markRead(clock.instant());
    }

    @Transactional
    public int markAllRead(Long userId) { return repo.markAllRead(userId, clock.instant()); }
}

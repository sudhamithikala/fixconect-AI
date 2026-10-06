package com.fixconnect.service;

import com.fixconnect.common.ApiException;
import com.fixconnect.domain.Notification;
import com.fixconnect.domain.User;
import com.fixconnect.dto.CommonDtos.NotificationListResponse;
import com.fixconnect.dto.CommonDtos.NotificationResponse;
import com.fixconnect.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository repo;
    private final DtoMapper mapper;

    public NotificationService(NotificationRepository repo, DtoMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    @Transactional
    public void notify(User user, String title, String message, String type, Long referenceId) {
        Notification n = new Notification();
        n.setUser(user);
        n.setTitle(title);
        n.setMessage(message);
        n.setType(type);
        n.setReferenceId(referenceId);
        repo.save(n);
    }

    @Transactional(readOnly = true)
    public NotificationListResponse list(Long userId, boolean unreadOnly) {
        List<Notification> list = unreadOnly
                ? repo.findByUser_IdAndReadFalseOrderByCreatedAtDesc(userId)
                : repo.findByUser_IdOrderByCreatedAtDesc(userId);
        return new NotificationListResponse(repo.countByUser_IdAndReadFalse(userId),
                list.stream().map(mapper::notification).toList());
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> recent(Long userId, int limit) {
        return repo.findByUser_IdOrderByCreatedAtDesc(userId).stream().limit(limit).map(mapper::notification).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repo.countByUser_IdAndReadFalse(userId);
    }

    @Transactional
    public NotificationResponse markRead(Long userId, Long id) {
        Notification n = repo.findByIdAndUser_Id(id, userId).orElseThrow(() -> ApiException.notFound("Notification"));
        n.setRead(true);
        return mapper.notification(n);
    }

    @Transactional
    public int markAllRead(Long userId) {
        List<Notification> unread = repo.findByUser_IdAndReadFalseOrderByCreatedAtDesc(userId);
        unread.forEach(n -> n.setRead(true));
        return unread.size();
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Notification n = repo.findByIdAndUser_Id(id, userId).orElseThrow(() -> ApiException.notFound("Notification"));
        repo.delete(n);
    }
}

package com.fixconnect.repository;

import com.fixconnect.domain.StatusEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatusEventRepository extends JpaRepository<StatusEvent, Long> {
    List<StatusEvent> findByRequest_IdOrderByCreatedAtAscIdAsc(Long requestId);
}

package com.fixconnect.repository;

import com.fixconnect.domain.JobPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobPhotoRepository extends JpaRepository<JobPhoto, Long> {
    List<JobPhoto> findByRequest_IdOrderByUploadedAtAsc(Long requestId);

    List<JobPhoto> findByRequest_Customer_IdOrderByUploadedAtDesc(Long customerId);

    List<JobPhoto> findByRequest_Provider_IdOrderByUploadedAtDesc(Long providerId);
}

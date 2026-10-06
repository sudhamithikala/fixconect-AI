package com.fixconnect.service;

import com.fixconnect.common.ApiException;
import com.fixconnect.domain.User;
import com.fixconnect.dto.AuthDtos.UserSummary;
import com.fixconnect.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/** Profile pictures for every user (customer, provider, admin). No photo = the UI shows the user's initials. */
@Service
public class ProfilePhotoService {

    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final UserRepository users;
    private final FileStorageService files;
    private final Lookup lookup;
    private final DtoMapper mapper;

    public ProfilePhotoService(UserRepository users, FileStorageService files, Lookup lookup, DtoMapper mapper) {
        this.users = users;
        this.files = files;
        this.lookup = lookup;
        this.mapper = mapper;
    }

    @Transactional
    public UserSummary upload(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Choose a photo to upload");
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!IMAGE_TYPES.contains(type)) {
            throw ApiException.badRequest("Profile photo must be a JPG, PNG, WEBP or GIF image");
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("Profile photo must be 5 MB or smaller");
        }
        User u = lookup.user(userId);
        String old = u.getPhotoFile();
        u.setPhotoFile(files.store(file));
        users.save(u);
        files.deleteQuietly(old);
        return mapper.user(u);
    }

    @Transactional
    public UserSummary remove(Long userId) {
        User u = lookup.user(userId);
        String old = u.getPhotoFile();
        u.setPhotoFile(null);
        users.save(u);
        files.deleteQuietly(old);
        return mapper.user(u);
    }
}

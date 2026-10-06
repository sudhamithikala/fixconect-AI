package com.fixconnect.controller;

import com.fixconnect.common.MessageResponse;
import com.fixconnect.dto.AuthDtos.ChangePasswordRequest;
import com.fixconnect.dto.AuthDtos.UserSummary;
import com.fixconnect.security.CurrentUser;
import com.fixconnect.service.AuthService;
import com.fixconnect.service.ProfilePhotoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@Tag(name = "13. Account & Files")
public class UserController {

    private final AuthService auth;
    private final CurrentUser currentUser;
    private final ProfilePhotoService photos;

    public UserController(AuthService auth, CurrentUser currentUser, ProfilePhotoService photos) {
        this.auth = auth;
        this.currentUser = currentUser;
        this.photos = photos;
    }

    @GetMapping("/me")
    @Operation(summary = "Current logged-in user")
    public UserSummary me() {
        return auth.me(currentUser.id());
    }

    @PostMapping(value = "/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload / replace my profile picture (JPG, PNG, WEBP, GIF - max 5 MB)")
    public UserSummary uploadPhoto(@RequestPart("file") MultipartFile file) {
        return photos.upload(currentUser.id(), file);
    }

    @DeleteMapping("/me/photo")
    @Operation(summary = "Remove my profile picture (initials are shown instead)")
    public UserSummary removePhoto() {
        return photos.remove(currentUser.id());
    }

    @PutMapping("/me/password")
    @Operation(summary = "Change password (Security & Preferences)")
    public MessageResponse changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        return auth.changePassword(currentUser.id(), req);
    }
}

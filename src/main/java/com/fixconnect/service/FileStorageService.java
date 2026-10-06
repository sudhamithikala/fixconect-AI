package com.fixconnect.service;

import com.fixconnect.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

/** Stores uploads (problem photos, before/after photos, ID proofs) on local disk. */
@Service
public class FileStorageService {

    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp", "image/gif",
            "image/heic", "application/pdf");

    private final Path root;

    public FileStorageService(@Value("${fixconnect.upload-dir:./uploads}") String dir) throws IOException {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    /** @return the stored file name (served at /api/files/{name}) */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("File is empty");
        }
        String type = file.getContentType();
        if (type == null || !ALLOWED.contains(type.toLowerCase())) {
            throw ApiException.badRequest("Only JPG, PNG, WEBP, GIF, HEIC images or PDF files are allowed");
        }
        String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
        String name = UUID.randomUUID().toString().replace("-", "") + (ext != null ? "." + ext.toLowerCase() : "");
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, root.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Could not store file", e);
        }
        return name;
    }

    public Resource load(String name) {
        Path p = root.resolve(name).normalize();
        if (!p.startsWith(root) || !Files.exists(p)) {
            throw ApiException.notFound("File");
        }
        try {
            return new UrlResource(p.toUri());
        } catch (MalformedURLException e) {
            throw ApiException.notFound("File");
        }
    }

    public String contentType(String name) {
        try {
            String t = Files.probeContentType(root.resolve(name));
            return t != null ? t : "application/octet-stream";
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }

    /** Removes a stored file; missing files and IO problems are ignored. */
    public void deleteQuietly(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        try {
            Path p = root.resolve(name).normalize();
            if (p.startsWith(root)) {
                Files.deleteIfExists(p);
            }
        } catch (IOException e) {
            // ignore - an orphaned file is harmless
        }
    }

    public static String url(String fileName) {
        return fileName == null ? null : "/api/files/" + fileName;
    }
}

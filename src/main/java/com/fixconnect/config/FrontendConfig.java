package com.fixconnect.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The FixConnect website (HTML/CSS/JS) is bundled in {@code src/main/resources/static} and served by
 * Spring Boot automatically - it ships inside the jar, no extra folder needed.
 * <p>
 * Optional dev mode: set {@code fixconnect.frontend-dir} (env {@code FIXCONNECT_FRONTEND_DIR}) to a folder on disk to
 * serve the pages from there instead (edits show up on refresh). Files missing there fall back to the bundled copy.
 */
@Configuration
public class FrontendConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(FrontendConfig.class);

    private final Path overrideDir;

    public FrontendConfig(@Value("${fixconnect.frontend-dir:}") String dir) {
        Path p = dir == null || dir.isBlank() ? null : Path.of(dir).toAbsolutePath().normalize();
        if (p != null && !Files.isDirectory(p)) {
            log.warn("fixconnect.frontend-dir '{}' not found - using the bundled website instead.", p);
            p = null;
        }
        this.overrideDir = p;
        log.info("Website: http://localhost:8080/  (serving {})",
                overrideDir != null ? overrideDir : "bundled files from classpath:/static");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (overrideDir == null) {
            return; // Spring Boot already serves classpath:/static/**
        }
        registry.addResourceHandler("/*.html", "/*.css", "/*.js", "/images/**", "/favicon.ico")
                .addResourceLocations(overrideDir.toUri().toString(), "classpath:/static/")
                .setCachePeriod(0);
    }
}

package com.fimory.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class StorageConfig implements WebMvcConfigurer {

    private final String uploadDir;

    public StorageConfig(@Value("${app.storage.upload-dir:uploads}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path absolutePath = Path.of(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(absolutePath);
        } catch (Exception ignored) {
        }
        String location = absolutePath.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/storage/**")
                .addResourceLocations(location);
    }
}

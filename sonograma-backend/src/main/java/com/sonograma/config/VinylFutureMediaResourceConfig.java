package com.sonograma.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class VinylFutureMediaResourceConfig implements WebMvcConfigurer {

    private final String mediaLocation;

    public VinylFutureMediaResourceConfig(
            @Value("${sonograma.vinylfuture.media-directory:./data/vinylfuture-media}") String mediaDirectory) {
        Path normalized = Path.of(mediaDirectory).toAbsolutePath().normalize();
        this.mediaLocation = normalized.toUri().toString();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/importar/vinylfuture/media/**")
                .addResourceLocations(mediaLocation);
        registry.addResourceHandler("/importaciones/vinylfuture/media/**")
                .addResourceLocations(mediaLocation);
    }
}

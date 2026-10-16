package com.yingliu.media.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/** Serves local media URLs used by the local profile and the C端 dev proxy. */
@Configuration
@ConditionalOnProperty(name = "media.storage-type", havingValue = "local", matchIfMissing = true)
public class LocalMediaResourceConfiguration implements WebMvcConfigurer {
    private final String resourceLocation;

    public LocalMediaResourceConfiguration(@Value("${media.storage-root:document/media}") String root) {
        this.resourceLocation = Path.of(root).toAbsolutePath().normalize().toUri().toString();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/media/**")
                .addResourceLocations(resourceLocation)
                .setCachePeriod(3600);
    }
}

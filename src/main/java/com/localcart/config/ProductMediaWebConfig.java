package com.localcart.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Configuration
public class ProductMediaWebConfig implements WebMvcConfigurer {

    private final Path imageRoot;

    public ProductMediaWebConfig(
            @Value("${apniko.image-storage-dir:uploads/products}") String storageDirectory) {
        this.imageRoot = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/api/media/products/**")
                .addResourceLocations(imageRoot.toUri().toString())
                .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic());
    }
}

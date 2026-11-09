package com.platform.notification.config;

import com.platform.notification.security.InternalApiInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final String internalKey;

    public WebMvcConfig(@Value("${platform.security.internal-key}") String internalKey) {
        this.internalKey = internalKey;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new InternalApiInterceptor(internalKey))
                .addPathPatterns("/api/internal/**");
    }
}

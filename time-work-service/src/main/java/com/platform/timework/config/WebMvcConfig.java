package com.platform.timework.config;

import com.platform.timework.security.InternalApiInterceptor;
import com.platform.timework.security.RbacInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RbacInterceptor rbacInterceptor;
    private final String internalKey;

    public WebMvcConfig(
            RbacInterceptor rbacInterceptor,
            @Value("${platform.security.internal-key}") String internalKey) {
        this.rbacInterceptor = rbacInterceptor;
        this.internalKey = internalKey;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new InternalApiInterceptor(internalKey))
                .addPathPatterns("/api/internal/**");
        registry.addInterceptor(rbacInterceptor).addPathPatterns("/api/**");
    }
}

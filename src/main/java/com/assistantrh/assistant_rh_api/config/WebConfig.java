package com.assistantrh.assistant_rh_api.config;

import com.assistantrh.assistant_rh_api.observability.RequestPerformanceInterceptor;
import com.assistantrh.assistant_rh_api.security.ApiAuthenticationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final ApiAuthenticationInterceptor apiAuthenticationInterceptor;
    private final RequestPerformanceInterceptor requestPerformanceInterceptor;

    public WebConfig(
            ApiAuthenticationInterceptor apiAuthenticationInterceptor,
            RequestPerformanceInterceptor requestPerformanceInterceptor
    ) {
        this.apiAuthenticationInterceptor = apiAuthenticationInterceptor;
        this.requestPerformanceInterceptor = requestPerformanceInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestPerformanceInterceptor)
                .addPathPatterns("/api/**");
        registry.addInterceptor(apiAuthenticationInterceptor)
                .addPathPatterns("/api/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/employes/**")
                .addResourceLocations("file:uploads/employes/");
    }
}

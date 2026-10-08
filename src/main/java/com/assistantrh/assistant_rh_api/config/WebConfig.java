        package com.assistantrh.assistant_rh_api.config;

        import com.assistantrh.assistant_rh_api.security.ApiAuthenticationInterceptor;
        import org.springframework.context.annotation.Configuration;
        import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
        import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
        import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

        @Configuration
        public class WebConfig implements WebMvcConfigurer {

            private final ApiAuthenticationInterceptor apiAuthenticationInterceptor;

            public WebConfig(
                    ApiAuthenticationInterceptor apiAuthenticationInterceptor
            ) {
                this.apiAuthenticationInterceptor = apiAuthenticationInterceptor;
            }

            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(apiAuthenticationInterceptor)
                        .addPathPatterns("/api/**");
            }

            @Override
    public void addResourceHandlers(
            ResourceHandlerRegistry registry
    ) {

        registry
                .addResourceHandler("/uploads/employes/**")
                .addResourceLocations(
                        "file:uploads/employes/"
                );
    }
}

package com.example.smartcanteen.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SecurityWebConfiguration implements WebMvcConfigurer {

    private final AuthenticationInterceptor authentication;

    public SecurityWebConfiguration(AuthenticationInterceptor authentication) {
        this.authentication = authentication;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authentication);
    }
}

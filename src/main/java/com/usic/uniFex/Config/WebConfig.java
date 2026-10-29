package com.usic.uniFex.Config;

import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer{
    @Value("${app.upload-root:uploads}")
    private String uploadRoot;

    @Autowired
    private AutenticacionInterceptor autenticacionInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(autenticacionInterceptor);
    }

    @Bean
    public FilterRegistrationBean<ShallowEtagHeaderFilter> filtroEtagMapa() {
        FilterRegistrationBean<ShallowEtagHeaderFilter> reg =
                new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        reg.addUrlPatterns("/api/app/puestos", "/api/app/puestos/asignaciones");
        reg.setName("etagMapa");
        return reg;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadRoot).toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/files/**")
                .addResourceLocations(location)
                .setCachePeriod(365 * 24 * 3600);
    }
}

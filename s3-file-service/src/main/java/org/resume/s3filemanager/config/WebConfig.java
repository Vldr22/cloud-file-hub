package org.resume.s3filemanager.config;

import lombok.RequiredArgsConstructor;
import org.resume.s3filemanager.properties.PageableProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Пагинация: размер страницы из properties, параметр sort от клиента игнорируется.
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(PageableProperties.class)
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class WebConfig implements WebMvcConfigurer {

    private final PageableProperties pageableProperties;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        PageableHandlerMethodArgumentResolver resolver =
                new PageableHandlerMethodArgumentResolver(new UnsortedSortArgumentResolver());

        resolver.setFallbackPageable(PageRequest.of(0, pageableProperties.getDefaultPageSize()));
        resolver.setMaxPageSize(pageableProperties.getMaxPageSize());

        resolvers.add(resolver);
    }
}

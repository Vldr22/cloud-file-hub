package org.resume.s3filemanager.config;

import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.SortHandlerMethodArgumentResolver;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Игнорирует параметр sort от клиента: порядок выдачи задаёт сервер в запросах к БД.
 */
public class UnsortedSortArgumentResolver extends SortHandlerMethodArgumentResolver {

    @Override
    public Sort resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return Sort.unsorted();
    }
}

package org.resume.s3filemanager.service.file.strategy;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ErrorResponseStrategyResolver {

    private final List<ErrorResponseStrategy> strategies;

    public ErrorResponseStrategyResolver(List<ErrorResponseStrategy> strategies) {
        this.strategies = strategies;
    }

    public ErrorResponseStrategy getStrategy(Exception e) {
        return strategies.stream()
                .filter(s -> s.isSupport(e))
                .findFirst()
                .orElseGet(DefaultErrorStrategy::new);
    }

}
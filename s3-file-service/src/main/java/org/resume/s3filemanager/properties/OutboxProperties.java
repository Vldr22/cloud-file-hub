package org.resume.s3filemanager.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Validated
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "app.outbox")
public class OutboxProperties {

    @Positive
    @Max(value = 100)
    private final int batchSize;

    @Positive
    @Max(value = 10)
    private final int maxRetryCount;

    @Positive
    private final long baseBackoffMinutes;

    @Positive
    private final long schedulerIntervalMs;

    @NotNull
    private final Duration lockAtMostFor;

}

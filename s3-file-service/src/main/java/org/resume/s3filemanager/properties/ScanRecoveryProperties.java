package org.resume.s3filemanager.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Настройки планировщика восстановления сканирования зависших файлов.
 */
@Getter
@Validated
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "app.scan-recovery")
public class ScanRecoveryProperties {

    @NotNull
    private final Duration threshold;

    @Positive
    @Max(10)
    private final int maxAttempts;

    @Positive
    @Max(100)
    private final int batchSize;

    @Positive
    private final long schedulerIntervalMs;

    @NotNull
    private final Duration lockAtMostFor;

}

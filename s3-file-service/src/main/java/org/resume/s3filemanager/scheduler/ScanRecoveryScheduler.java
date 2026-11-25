package org.resume.s3filemanager.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.resume.s3filemanager.service.file.ScanRecoveryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler для восстановления сканирования зависших файлов.
 * <p>
 * Периодически переотправляет на сканирование файлы, застрявшие в PENDING_SCAN.
 * Интервал настраивается через app.scan-recovery.scheduler-interval-ms.
 * При нескольких экземплярах приложения работает только один - ShedLock держит блокировку в таблице shedlock.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScanRecoveryScheduler {

    private static final String SHEDLOCK_NAME = "scan-recovery-scheduler";

    private final ScanRecoveryService scanRecoveryService;

    @Scheduled(fixedDelayString = "${app.scan-recovery.scheduler-interval-ms}")
    @SchedulerLock(name = SHEDLOCK_NAME, lockAtMostFor = "${app.scan-recovery.lock-at-most-for}")
    public void recoverStuckFiles() {
        log.debug("Scan recovery scheduler triggered");
        scanRecoveryService.recoverStuckFiles();
    }
}

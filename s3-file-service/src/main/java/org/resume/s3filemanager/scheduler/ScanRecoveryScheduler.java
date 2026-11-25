package org.resume.s3filemanager.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.resume.s3filemanager.service.file.ScanRecoveryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler восстановления сканирования зависших файлов.
 * Интервал - app.scan-recovery.scheduler-interval-ms, в кластере работает один экземпляр (ShedLock).
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

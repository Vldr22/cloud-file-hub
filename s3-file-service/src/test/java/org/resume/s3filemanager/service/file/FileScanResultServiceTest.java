package org.resume.s3filemanager.service.file;

import com.github.javafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.resume.common.model.FileScanResult;
import org.resume.common.model.ScanStatus;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileScanResultService — обработка результатов антивирусного сканирования")
class FileScanResultServiceTest {

    private static final Faker FAKER = new Faker();

    @Mock
    private FileMetadataService fileMetadataService;

    @Mock
    private YandexStorageService yandexStorageService;

    @InjectMocks
    private FileScanResultService fileScanResultService;

    private FileScanResult scanResult;
    private String s3Key;

    @BeforeEach
    void setUp() {
        s3Key = FAKER.internet().uuid() + ".pdf";
        scanResult = new FileScanResult();
        scanResult.setS3Key(s3Key);
    }

    @Test
    @DisplayName("Обновляет статус чистого файла, S3 не трогает")
    void shouldUpdateStatus_whenFileIsClean() {
        //given
        scanResult.setStatus(ScanStatus.CLEAN);

        //when
        fileScanResultService.processScanResult(scanResult);

        //then
        verify(fileMetadataService).updateScanStatus(s3Key, ScanStatus.CLEAN);
        verifyNoInteractions(yandexStorageService);
    }

    @Test
    @DisplayName("Обновляет статус при ошибке сканирования, S3 не трогает")
    void shouldUpdateStatus_whenScanResultIsError() {
        //given
        scanResult.setStatus(ScanStatus.ERROR);

        //when
        fileScanResultService.processScanResult(scanResult);

        //then
        verify(fileMetadataService).updateScanStatus(s3Key, ScanStatus.ERROR);
        verifyNoInteractions(yandexStorageService);
    }

    @Test
    @DisplayName("Блокирует заражённый файл статусом INFECTED до удаления из S3")
    void shouldBlockThenDeleteFromS3_whenFileIsInfected() {
        //given
        scanResult.setStatus(ScanStatus.INFECTED);
        scanResult.setVirusName(FAKER.lorem().word());

        //when
        fileScanResultService.processScanResult(scanResult);

        //then
        InOrder inOrder = inOrder(fileMetadataService, yandexStorageService);
        inOrder.verify(fileMetadataService).updateScanStatus(s3Key, ScanStatus.INFECTED);
        inOrder.verify(yandexStorageService).deleteFileYandexS3(s3Key);
    }

    @Test
    @DisplayName("Сохраняет статус INFECTED и не пробрасывает исключение при сбое удаления из S3")
    void shouldKeepInfectedStatus_whenS3DeletionFails() {
        //given
        scanResult.setStatus(ScanStatus.INFECTED);
        scanResult.setVirusName(FAKER.lorem().word());
        doThrow(new RuntimeException()).when(yandexStorageService).deleteFileYandexS3(s3Key);

        //when
        fileScanResultService.processScanResult(scanResult);

        //then
        InOrder inOrder = inOrder(fileMetadataService, yandexStorageService);
        inOrder.verify(fileMetadataService).updateScanStatus(s3Key, ScanStatus.INFECTED);
        inOrder.verify(yandexStorageService).deleteFileYandexS3(s3Key);
    }
}

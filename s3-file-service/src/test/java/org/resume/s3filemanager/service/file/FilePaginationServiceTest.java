package org.resume.s3filemanager.service.file;

import com.github.javafaker.Faker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.resume.common.model.ScanStatus;
import org.resume.s3filemanager.dto.FileResponse;
import org.resume.s3filemanager.entity.FileMetadata;
import org.resume.s3filemanager.mapper.FileMapper;
import org.resume.s3filemanager.mapper.FileMapperImpl;
import org.resume.s3filemanager.repository.FileMetadataRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FilePaginationService — постраничный список файлов")
class FilePaginationServiceTest {

    private static final Faker FAKER = new Faker();
    private static final long SIZE_BYTES = 2_097_152L;

    @Mock
    private FileMetadataRepository fileMetadataRepository;

    @Spy
    private FileMapper fileMapper = new FileMapperImpl();

    @InjectMocks
    private FilePaginationService filePaginationService;

    /**
     * Страница с файлами: DTO содержит правильные поля и размер в байтах.
     */
    @Test
    void shouldReturnPageWithCorrectDto_whenFilesExist() {
        FileMetadata file = FileMetadata.builder()
                .originalName(FAKER.file().fileName())
                .uniqueName(FAKER.internet().uuid() + ".pdf")
                .size(SIZE_BYTES)
                .build();

        Pageable pageable = PageRequest.of(0, 10);
        when(fileMetadataRepository.findByScanStatus(ScanStatus.CLEAN, pageable))
                .thenReturn(new PageImpl<>(List.of(file)));

        Page<FileResponse> result = filePaginationService.paginate(pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().fileName()).isEqualTo(file.getOriginalName());
        assertThat(result.getContent().getFirst().uniqueName()).isEqualTo(file.getUniqueName());
        assertThat(result.getContent().getFirst().fileSize()).isEqualTo(SIZE_BYTES);

        verify(fileMetadataRepository).findByScanStatus(ScanStatus.CLEAN, pageable);
    }

    /**
     * Пустая страница: возвращаем пустой Page без ошибок.
     */
    @Test
    void shouldReturnEmptyPage_whenNoFilesExist() {
        Pageable pageable = PageRequest.of(0, 10);
        when(fileMetadataRepository.findByScanStatus(ScanStatus.CLEAN, pageable))
                .thenReturn(Page.empty());

        Page<FileResponse> result = filePaginationService.paginate(pageable);

        assertThat(result.getContent()).isEmpty();
    }

    /**
     * Файлы пользователя возвращаются с любым статусом проверки.
     */
    @Test
    void shouldReturnUserFiles_withAnyScanStatus() {
        String username = FAKER.name().username();
        FileMetadata file = FileMetadata.builder()
                .originalName(FAKER.file().fileName())
                .uniqueName(FAKER.internet().uuid() + ".pdf")
                .size(SIZE_BYTES)
                .scanStatus(ScanStatus.ERROR)
                .build();

        Pageable pageable = PageRequest.of(0, 10);
        when(fileMetadataRepository.findByUserUsername(username, pageable))
                .thenReturn(new PageImpl<>(List.of(file)));

        Page<FileResponse> result = filePaginationService.findUserFiles(username, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().scanStatus()).isEqualTo(ScanStatus.ERROR);
    }
}
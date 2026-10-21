package org.resume.s3filemanager.service.file;

import lombok.RequiredArgsConstructor;
import org.resume.common.model.ScanStatus;
import org.resume.s3filemanager.dto.FileResponse;
import org.resume.s3filemanager.mapper.FileMapper;
import org.resume.s3filemanager.repository.FileMetadataRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Сервис для постраничного получения списка проверенных файлов.
 */
@Service
@RequiredArgsConstructor
public class FilePaginationService {

    private final FileMetadataRepository fileMetadataRepository;
    private final FileMapper fileMapper;

    /**
     * Возвращает постраничный список файлов с метаданными.
     *
     * @param pageable параметры пагинации (номер страницы, размер, сортировка)
     * @return страница с информацией о файлах
     */
    public Page<FileResponse> paginate(Pageable pageable) {
        return fileMetadataRepository.findByScanStatus(ScanStatus.CLEAN, pageable)
                .map(fileMapper::toFileResponse);
    }
}

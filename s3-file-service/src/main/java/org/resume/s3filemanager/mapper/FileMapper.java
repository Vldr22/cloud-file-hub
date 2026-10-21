package org.resume.s3filemanager.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.resume.s3filemanager.constant.FileSizeConstants;
import org.resume.s3filemanager.dto.AdminFileResponse;
import org.resume.s3filemanager.dto.FileResponse;
import org.resume.s3filemanager.entity.FileMetadata;

import java.util.Locale;

/**
 * Маппинг метаданных файла в DTO.
 */
@Mapper(config = MapStructConfig.class)
public interface FileMapper {

    @Mapping(target = "fileName", source = "originalName")
    @Mapping(target = "fileSize", source = "size", qualifiedByName = FileSizeConstants.TO_MEGABYTES)
    FileResponse toFileResponse(FileMetadata file);

    @Mapping(target = "fileName", source = "originalName")
    @Mapping(target = "fileSize", source = "size", qualifiedByName = FileSizeConstants.TO_MEGABYTES)
    @Mapping(target = "uploadedUserName", source = "user.username")
    AdminFileResponse toAdminFileResponse(FileMetadata file);

    @Named(FileSizeConstants.TO_MEGABYTES)
    default String toMegabytes(long bytes) {
        return String.format(Locale.US, FileSizeConstants.MB_FORMAT, bytes / FileSizeConstants.BYTES_IN_MB);
    }
}

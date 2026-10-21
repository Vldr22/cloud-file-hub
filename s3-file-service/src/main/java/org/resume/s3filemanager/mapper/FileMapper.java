package org.resume.s3filemanager.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.resume.s3filemanager.dto.AdminFileResponse;
import org.resume.s3filemanager.dto.FileResponse;
import org.resume.s3filemanager.entity.FileMetadata;

/**
 * Маппинг метаданных файла в DTO.
 */
@Mapper(config = MapStructConfig.class)
public interface FileMapper {

    @Mapping(target = "fileName", source = "originalName")
    @Mapping(target = "fileSize", source = "size")
    FileResponse toFileResponse(FileMetadata file);

    @Mapping(target = "fileName", source = "originalName")
    @Mapping(target = "fileSize", source = "size")
    @Mapping(target = "uploadedUserName", source = "user.username")
    AdminFileResponse toAdminFileResponse(FileMetadata file);
}

package org.resume.s3filemanager.mapper;

import io.hypersistence.utils.hibernate.type.basic.Inet;
import org.mapstruct.Mapper;
import org.resume.s3filemanager.dto.AuditLogResponse;
import org.resume.s3filemanager.entity.AuditLog;

/**
 * Маппинг записи аудита в DTO.
 */
@Mapper(config = MapStructConfig.class)
public interface AuditLogMapper {

    AuditLogResponse toAuditLogResponse(AuditLog auditLog);

    default String toIpAddress(Inet inet) {
        if (inet == null) {
            return null;
        }
        return inet.getAddress();
    }
}

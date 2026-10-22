package org.resume.s3filemanager.mapper;

import org.mapstruct.Mapper;
import org.resume.s3filemanager.dto.UserDetailsResponse;
import org.resume.s3filemanager.entity.User;

/**
 * Маппинг пользователя в DTO.
 */
@Mapper(config = MapStructConfig.class)
public interface UserMapper {

    UserDetailsResponse toUserDetailsResponse(User user);
}

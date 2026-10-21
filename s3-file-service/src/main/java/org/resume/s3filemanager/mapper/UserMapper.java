package org.resume.s3filemanager.mapper;

import org.resume.s3filemanager.dto.UserDetailsResponse;
import org.resume.s3filemanager.entity.User;
import org.springframework.stereotype.Component;

/**
 * Маппинг пользователя в DTO.
 */
@Component
public class UserMapper {

    public UserDetailsResponse toUserDetailsResponse(User user) {
        return UserDetailsResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .status(user.getStatus())
                .uploadStatus(user.getUploadStatus())
                .build();
    }
}

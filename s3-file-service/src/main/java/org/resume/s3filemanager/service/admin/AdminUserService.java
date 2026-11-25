package org.resume.s3filemanager.service.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.resume.s3filemanager.dto.UserDetailsResponse;
import org.resume.s3filemanager.entity.User;
import org.resume.s3filemanager.enums.FileUploadStatus;
import org.resume.s3filemanager.enums.UserStatus;
import org.resume.s3filemanager.mapper.UserMapper;
import org.resume.s3filemanager.security.JwtWhitelistService;
import org.resume.s3filemanager.service.auth.UserService;
import org.resume.s3filemanager.service.file.YandexStorageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Сервис административных операций с пользователями.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserService userService;
    private final JwtWhitelistService jwtWhitelistService;
    private final YandexStorageService yandexStorageService;
    private final UserMapper userMapper;

    /**
     * Возвращает список всех пользователей.
     *
     * @param pageable параметры пагинации
     * @return страница с информацией о пользователях
     */
    public Page<UserDetailsResponse> getUsers(Pageable pageable) {
        return userService.findAll(pageable)
                .map(userMapper::toUserDetailsResponse);
    }

    /**
     * Изменяет статус пользователя (ACTIVE/BLOCKED).
     * При блокировке инвалидирует JWT токен.
     *
     * @param userId ID пользователя
     * @param status новый статус
     * @return обновлённая информация о пользователе
     */
    public UserDetailsResponse changeStatus(Long userId, UserStatus status) {
        User user = userService.findById(userId);
        userService.updateStatus(user, status);

        if (status == UserStatus.BLOCKED) {
            jwtWhitelistService.deleteToken(user.getUsername());
        }

        return userMapper.toUserDetailsResponse(user);
    }

    /**
     * Изменяет статус загрузки файлов пользователя.
     *
     * @param userId ID пользователя
     * @param uploadStatus новый статус (UNLIMITED, NOT_UPLOADED, FILE_UPLOADED)
     * @return обновлённая информация о пользователе
     */
    public UserDetailsResponse changeFileUploadStatus(Long userId, FileUploadStatus uploadStatus) {
        User user = userService.findById(userId);
        userService.overrideUploadStatus(user, uploadStatus);
        return userMapper.toUserDetailsResponse(user);
    }

    /**
     * Удаляет пользователя с его файлами: инвалидирует токен, БД - одной транзакцией,
     * затем S3. При сбое S3 объект остаётся в бакете, но файлы недоступны через API.
     */
    public void deleteUser(Long userId) {
        User user = userService.findById(userId);
        jwtWhitelistService.deleteToken(user.getUsername());

        List<String> s3Keys = userService.deleteWithFiles(user);
        s3Keys.forEach(this::deleteFromStorage);
    }

    private void deleteFromStorage(String uniqueName) {
        try {
            yandexStorageService.deleteFileYandexS3(uniqueName);
        } catch (Exception e) {
            log.error("User deleted but S3 cleanup failed, orphan object left: uniqueName={}", uniqueName, e);
        }
    }
}

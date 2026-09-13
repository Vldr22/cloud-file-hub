package org.resume.s3filemanager.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.resume.common.model.ScanStatus;
import org.resume.s3filemanager.dto.*;
import org.resume.s3filemanager.enums.FileUploadStatus;
import org.resume.s3filemanager.enums.UserStatus;
import org.resume.s3filemanager.service.admin.AdminAuditService;
import org.resume.s3filemanager.service.admin.AdminFileService;
import org.resume.s3filemanager.service.admin.AdminUserService;
import org.resume.s3filemanager.service.kafka.RetryDLTService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * REST контроллер для административных операций.
 * <p>
 * Предоставляет API для управления пользователями и просмотра журнала аудита.
 * Доступен только администраторам.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Администрирование пользователями, файлами и логами")
public class AdminController {

    private final AdminUserService adminUserService;
    private final AdminAuditService adminAuditService;
    private final AdminFileService adminFileService;
    private final RetryDLTService retryDLTService;

    @Operation(summary = "Журнал аудита", description = "Возвращает логи с фильтрацией по пользователю, операции, статусу и дате")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Успешно"),
            @ApiResponse(responseCode = "401", description = "Токен отсутствует или истёк"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав")
    })
    @Parameter(name = "page", description = "Номер страницы", example = "0")
    @Parameter(name = "size", description = "Размер страницы", example = "20")
    @PostMapping("/audit-logs")
    public Page<AuditLogResponse> getAuditLogs(
            @Valid @ParameterObject AuditLogFilter filter,
            @Parameter(hidden = true) Pageable pageable) {
        return adminAuditService.getAuditLogs(filter, pageable);
    }

    @Operation(summary = "Список пользователей", description = "Возвращает всех пользователей с пагинацией")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Успешно"),
            @ApiResponse(responseCode = "401", description = "Токен отсутствует или истёк"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав")
    })
    @Parameter(name = "page", description = "Номер страницы", example = "0")
    @Parameter(name = "size", description = "Размер страницы", example = "20")
    @GetMapping("/users")
    public Page<UserDetailsResponse> getUsers(@Parameter(hidden = true) Pageable pageable) {
        return adminUserService.getUsers(pageable);
    }

    @Operation(summary = "Изменить статус пользователя", description = "Меняет статус (ACTIVE/BLOCKED). При блокировке инвалидирует токен")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Статус изменён"),
            @ApiResponse(responseCode = "401", description = "Токен отсутствует или истёк"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден")
    })
    @PutMapping("/users/{userId}/status")
    public CommonResponse<UserDetailsResponse> changeUserStatus(
            @Parameter(description = "ID пользователя", example = "1") @PathVariable Long userId,
            @Parameter(description = "Новый статус", example = "BLOCKED") @RequestParam UserStatus status) {
        return CommonResponse.success(adminUserService.changeStatus(userId, status));
    }

    @Operation(summary = "Изменить статус загрузки", description = "Меняет лимит загрузки файлов (UNLIMITED/NOT_UPLOADED/FILE_UPLOADED)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Статус изменён"),
            @ApiResponse(responseCode = "401", description = "Токен отсутствует или истёк"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден")
    })
    @PutMapping("/users/{userId}/file-upload-status")
    public CommonResponse<UserDetailsResponse> changeUserFileUploadStatus(
            @Parameter(description = "ID пользователя", example = "1") @PathVariable Long userId,
            @Parameter(description = "Новый статус загрузки", example = "UNLIMITED") @RequestParam FileUploadStatus uploadStatus) {
        return CommonResponse.success(adminUserService.changeFileUploadStatus(userId, uploadStatus));
    }

    @Operation(summary = "Удалить пользователя", description = "Удаляет пользователя и инвалидирует его токен")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Пользователь удалён"),
            @ApiResponse(responseCode = "401", description = "Токен отсутствует или истёк"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден")
    })
    @DeleteMapping("/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(
            @Parameter(description = "ID пользователя", example = "1") @PathVariable Long userId) {
        adminUserService.deleteUser(userId);
    }

    @Operation(summary = "Файлы по статусу сканирования", description = "Возвращает файлы с указанным статусом (PENDING_SCAN/CLEAN/INFECTED/ERROR)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Успешно"),
            @ApiResponse(responseCode = "401", description = "Токен отсутствует или истёк"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав")
    })
    @Parameter(name = "page", description = "Номер страницы", example = "0")
    @Parameter(name = "size", description = "Размер страницы", example = "20")
    @GetMapping("/files/scan-status")
    public Page<AdminFileResponse> getFilesByScanStatus(
            @Parameter(description = "Статус сканирования", example = "CLEAN") @RequestParam ScanStatus scanStatus,
            @Parameter(hidden = true) Pageable pageable) {
        return adminFileService.findAllByScanStatus(scanStatus, pageable);
    }

    @Operation(summary = "Повторить обработку DLT", description = "Переотправляет все сообщения из Dead Letter Topic для файлов со статусом ERROR")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Сообщения переотправлены"),
            @ApiResponse(responseCode = "401", description = "Токен отсутствует или истёк"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав")
    })
    @PostMapping("/files/retry-dlt")
    public CommonResponse<Integer> retryDlt() {
        return CommonResponse.success(retryDLTService.retryFailedEvents());
    }

    @Operation(summary = "Повторить сканирование файла", description = "Отправляет файл на повторное сканирование. Только для статуса ERROR")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Файл отправлен на сканирование"),
            @ApiResponse(responseCode = "400", description = "Файл не в статусе ERROR"),
            @ApiResponse(responseCode = "401", description = "Токен отсутствует или истёк"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Файл не найден")
    })
    @PostMapping("/files/{fileId}/retry-scan")
    public CommonResponse<AdminFileResponse> retryScan(
            @Parameter(description = "ID файла", example = "1") @PathVariable Long fileId) {
        return CommonResponse.success(adminFileService.retryScan(fileId));
    }

    @Operation(summary = "Статистика файлов", description = "Количество файлов по каждому статусу сканирования")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Успешно"),
            @ApiResponse(responseCode = "401", description = "Токен отсутствует или истёк"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав")
    })
    @GetMapping("/files/stats")
    public CommonResponse<FileStatsResponse> getFileStats() {
        return CommonResponse.success(adminFileService.getFileStats());
    }

    @Operation(summary = "Удалить файлы по статусу", description = "Для INFECTED — только метаданные (S3 уже очищен). Для остальных — удаляет и из S3, и метаданные")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Файлы удалены"),
            @ApiResponse(responseCode = "401", description = "Токен отсутствует или истёк"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав")
    })
    @DeleteMapping("/files/scan-status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public CommonResponse<Integer> deleteFilesByScanStatus(
            @Parameter(description = "Статус сканирования", example = "INFECTED") @RequestParam ScanStatus scanStatus) {
        return CommonResponse.success(adminFileService.deleteAllByScanStatus(scanStatus));
    }
}

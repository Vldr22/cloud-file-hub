package org.resume.s3filemanager.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.resume.s3filemanager.constant.SuccessMessages;
import org.resume.s3filemanager.dto.CommonResponse;
import org.resume.s3filemanager.dto.FileDownloadResponse;
import org.resume.s3filemanager.dto.MultipleUploadResponse;
import org.resume.s3filemanager.service.file.FileFacadeService;
import org.resume.s3filemanager.validation.ValidBatchSize;
import org.resume.s3filemanager.validation.ValidFile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST контроллер для управления файлами.
 * <p>
 * Предоставляет API для загрузки, скачивания и удаления файлов.
 * Поддерживает одиночную загрузку (для всех пользователей) и
 * множественную загрузку (только для администраторов).
 *
 * @see FileFacadeService
 */
@Validated
@RestController
@RequestMapping("api/files")
@RequiredArgsConstructor
@Tag(name = "Files", description = "Загрузка, скачивание и удаление файлов")
public class FileController {

    private final FileFacadeService fileFacadeService;

    @Operation(summary = "Загрузить файл", description = "Загружает один файл. Обычные пользователи — только 1 файл, админы — без ограничений")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Файл загружен"),
            @ApiResponse(responseCode = "400", description = "Неверный тип или формат файла"),
            @ApiResponse(responseCode = "401", description = "Токен отсутствует или истёк"),
            @ApiResponse(responseCode = "403", description = "Лимит загрузки исчерпан или пользователь заблокирован"),
            @ApiResponse(responseCode = "409", description = "Файл уже существует"),
            @ApiResponse(responseCode = "413", description = "Файл превышает ${spring.servlet.multipart.max-file-size}")
    })  @PostMapping("/upload")
    @ResponseStatus(HttpStatus.CREATED)
    public CommonResponse<String> upload (
            @Parameter(description = "Файл для загрузки", required = true)
            @RequestParam("file") @ValidFile MultipartFile file) {
        fileFacadeService.uploadFile(file);
        return CommonResponse.success(SuccessMessages.FILE_UPLOAD_SUCCESS);
    }

    @Operation(summary = "Множественная загрузка",
            description = "Загружает до ${app.multiple-upload.max-batch-size} файлов. Только для администраторов")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Хотя бы один файл загружен успешно (частичный успех возможен)"),
            @ApiResponse(responseCode = "400", description = "Все файлы не прошли загрузку или превышен лимит файлов"),
            @ApiResponse(responseCode = "401", description = "Токен отсутствует или истёк"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав")
    })
    @PostMapping("/multiple-upload")
    @ResponseStatus(HttpStatus.CREATED)
    public CommonResponse<List<MultipleUploadResponse>> multipleUpload(
            @Parameter(description = "Файлы для загрузки (до ${app.multiple-upload.max-batch-size})", required = true)
            @RequestParam("files") @ValidBatchSize MultipartFile[] files) {
        List<MultipleUploadResponse> results = fileFacadeService.multipleUpload(files);
        return CommonResponse.success(results);
    }

    @Operation(summary = "Скачать файл", description = "Скачивает файл по уникальному имени")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Файл успешно скачан"),
            @ApiResponse(responseCode = "404", description = "Файл не найден"),
            @ApiResponse(responseCode = "500", description = "Ошибка S3")
    })
    @SecurityRequirements
    @GetMapping("/{uniqueName}")
    public ResponseEntity<ByteArrayResource> download(
            @Parameter(description = "Уникальное имя файла", example = "212d7ce9-8451-4c4d-881e-d198593aa518.png")
            @PathVariable String uniqueName) {
        FileDownloadResponse response = fileFacadeService.downloadFile(uniqueName);
        ByteArrayResource resource = new ByteArrayResource(response.getContent());
        return ResponseEntity
                .ok()
                .contentLength(response.getSize())
                .header("Content-Type", response.getContentType())
                .header("Content-Disposition", "attachment; filename*=UTF-8''" + response.getFileName())
                .body(resource);
    }

    @Operation(summary = "Удалить файл", description = "Удаляет файл по уникальному имени. Пользователи — только свои, админы — любые")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Файл удалён"),
            @ApiResponse(responseCode = "401", description = "Токен отсутствует или истёк"),
            @ApiResponse(responseCode = "403", description = "Нет прав на удаление чужого файла"),
            @ApiResponse(responseCode = "404", description = "Файл не найден")
    })
    @DeleteMapping("/{uniqueName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(description = "Уникальное имя файла", example = "212d7ce9-8451-4c4d-881e-d198593aa518.png")
            @PathVariable String uniqueName) {
        fileFacadeService.deleteFile(uniqueName);
    }
}

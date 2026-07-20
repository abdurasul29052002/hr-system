package uz.sonic.hr.common.storage;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.sonic.hr.common.dto.Dtos.PresignRequest;
import uz.sonic.hr.common.dto.Dtos.PresignResponse;
import uz.sonic.hr.common.security.CurrentUser;

/**
 * Hands out short-lived signed URLs so the browser can upload attachments straight to S3.
 *
 * <p>Nothing is created in the database here — an upload only becomes an attachment when its key is
 * claimed while posting a comment or adding a task attachment, and those endpoints re-check the object
 * against S3 rather than trusting anything the client says about it.
 */
@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final StorageService storage;
    private final CurrentUser currentUser;

    @PostMapping("/presign")
    public PresignResponse presign(@Valid @RequestBody PresignRequest request) {
        StorageService.PresignedUpload upload = storage.presignUpload(
                currentUser.get().getId(), request.fileName(), request.contentType());
        return new PresignResponse(upload.key(), upload.uploadUrl());
    }
}

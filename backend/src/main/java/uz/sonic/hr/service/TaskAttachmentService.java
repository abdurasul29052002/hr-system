package uz.sonic.hr.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import uz.sonic.hr.entity.Task;
import uz.sonic.hr.entity.TaskAttachment;
import uz.sonic.hr.entity.TeamMembership;
import uz.sonic.hr.repo.TaskAttachmentRepository;
import uz.sonic.hr.repo.TaskRepository;
import uz.sonic.hr.web.dto.Dtos.AttachmentDto;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskAttachmentService {

    private final TaskAttachmentRepository attachmentRepository;
    private final TaskRepository taskRepository;
    private final S3StorageService s3StorageService;

    @Transactional
    public AttachmentDto uploadAttachment(Long taskId, MultipartFile file, TeamMembership actor) {
        Task task = getTaskInTeam(taskId, actor);

        // Upload to S3
        String s3Key = s3StorageService.uploadFile(file, "tasks/" + taskId);

        // Create attachment record
        TaskAttachment attachment = TaskAttachment.builder()
                .task(task)
                .fileName(file.getOriginalFilename())
                .filePath(s3Key) // Store S3 key
                .fileSize(file.getSize())
                .mimeType(file.getContentType())
                .uploadedBy(actor.getEmployee())
                .build();

        attachment = attachmentRepository.save(attachment);

        // Generate public URL or presigned URL
        String downloadUrl = s3StorageService.getPublicUrl(s3Key);

        return AttachmentDto.from(attachment, downloadUrl);
    }

    @Transactional
    public void deleteAttachment(Long attachmentId, TeamMembership actor) {
        TaskAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found"));

        // Check permissions: only uploader or manager can delete
        boolean isUploader = attachment.getUploadedBy().getId().equals(actor.getEmployee().getId());
        boolean isManager = TeamService.isManagerOrLeader(actor);

        if (!isUploader && !isManager) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only delete your own attachments");
        }

        // Check attachment belongs to actor's team
        if (!attachment.getTask().getTeam().getId().equals(actor.getTeam().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Attachment not in your team");
        }

        // Delete from S3
        s3StorageService.deleteFile(attachment.getFilePath());

        // Delete record
        attachmentRepository.delete(attachment);
    }

    @Transactional(readOnly = true)
    public List<AttachmentDto> listAttachments(Long taskId, TeamMembership viewer) {
        Task task = getTaskInTeam(taskId, viewer);
        List<TaskAttachment> attachments = attachmentRepository.findAllByTaskIdOrderByUploadedAtDesc(task.getId());

        return attachments.stream()
                .map(a -> {
                    String downloadUrl = s3StorageService.getPublicUrl(a.getFilePath());
                    return AttachmentDto.from(a, downloadUrl);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskAttachment getAttachment(Long attachmentId, TeamMembership viewer) {
        TaskAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found"));

        // Check attachment belongs to viewer's team
        if (!attachment.getTask().getTeam().getId().equals(viewer.getTeam().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Attachment not in your team");
        }

        return attachment;
    }

    public String getAttachmentDownloadUrl(TaskAttachment attachment) {
        return s3StorageService.getPublicUrl(attachment.getFilePath());
    }

    private Task getTaskInTeam(Long taskId, TeamMembership viewer) {
        return taskRepository.findById(taskId)
                .filter(task -> task.getTeam().getId().equals(viewer.getTeam().getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }
}

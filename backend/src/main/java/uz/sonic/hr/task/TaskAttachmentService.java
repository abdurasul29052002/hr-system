package uz.sonic.hr.task;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import uz.sonic.hr.common.dto.Dtos.AttachmentDto;
import uz.sonic.hr.common.storage.StorageService;
import uz.sonic.hr.team.TeamMembership;
import uz.sonic.hr.team.TeamService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskAttachmentService {

    private final TaskAttachmentRepository attachmentRepository;
    private final TaskRepository taskRepository;
    private final StorageService storage;

    @Transactional
    public AttachmentDto uploadAttachment(Long taskId, MultipartFile file, TeamMembership actor) {
        Task task = getTaskInTeam(taskId, actor);

        String key = storage.upload(file, "tasks/" + taskId);

        TaskAttachment attachment = TaskAttachment.builder()
                .task(task)
                .fileName(file.getOriginalFilename())
                .filePath(key)
                .fileSize(file.getSize())
                .mimeType(file.getContentType())
                .uploadedBy(actor.getEmployee())
                .build();

        attachment = attachmentRepository.save(attachment);

        return AttachmentDto.from(attachment, storage.publicUrl(key));
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

        storage.delete(attachment.getFilePath());
        attachmentRepository.delete(attachment);
    }

    @Transactional(readOnly = true)
    public List<AttachmentDto> listAttachments(Long taskId, TeamMembership viewer) {
        Task task = getTaskInTeam(taskId, viewer);
        List<TaskAttachment> attachments = attachmentRepository.findAllByTaskIdOrderByUploadedAtDesc(task.getId());

        return attachments.stream()
                .map(a -> AttachmentDto.from(a, storage.publicUrl(a.getFilePath())))
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
        return storage.publicUrl(attachment.getFilePath());
    }

    private Task getTaskInTeam(Long taskId, TeamMembership viewer) {
        return taskRepository.findById(taskId)
                .filter(task -> task.getTeam().getId().equals(viewer.getTeam().getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }
}

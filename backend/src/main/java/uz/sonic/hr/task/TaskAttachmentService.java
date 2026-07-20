package uz.sonic.hr.task;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
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
    private final TransactionTemplate transactions;

    /**
     * Attaches a file the browser already uploaded straight to S3, identified by its key.
     *
     * <p>Not {@code @Transactional} as a whole: claiming the upload means verifying and copying it in S3, and
     * holding a database connection across that network round trip is what would make 100MB attachments
     * dangerous under concurrency. Authorisation and the insert each take their own short transaction.
     */
    public AttachmentDto attachUploaded(Long taskId, String key, String fileName, TeamMembership actor) {
        Long employeeId = actor.getEmployee().getId();

        transactions.executeWithoutResult(status -> getTaskInTeam(taskId, actor));

        // Copy the staging object to a permanent key nobody holds a signature for, so its bytes can no
        // longer be swapped after the fact, and read its verified size and type.
        storage.requireOwnedBy(key, employeeId);
        String name = fileName != null && !fileName.isBlank() ? fileName : fileNameOf(key);
        StorageService.FinalizedUpload stored = storage.finalizeUpload(key, "tasks/" + taskId, name);

        try {
            return transactions.execute(status -> {
                Task task = getTaskInTeam(taskId, actor);
                TaskAttachment attachment = TaskAttachment.builder()
                        .task(task)
                        .fileName(name)
                        .filePath(stored.key())
                        .fileSize(stored.size())
                        .mimeType(stored.contentType())
                        .uploadedBy(actor.getEmployee())
                        .build();
                attachment = attachmentRepository.save(attachment);
                return AttachmentDto.from(attachment, storage.publicUrl(stored.key()));
            });
        } catch (RuntimeException e) {
            storage.delete(stored.key());
            throw e;
        }
    }

    /** Keys look like {@code uploads/{employeeId}/{uuid}/{name}} — the display name is the last segment. */
    private static String fileNameOf(String key) {
        int slash = key.lastIndexOf('/');
        return slash >= 0 && slash < key.length() - 1 ? key.substring(slash + 1) : key;
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

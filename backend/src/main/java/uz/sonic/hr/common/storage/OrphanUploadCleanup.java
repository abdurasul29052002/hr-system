package uz.sonic.hr.common.storage;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.sonic.hr.task.CommentAttachmentRepository;
import uz.sonic.hr.task.TaskAttachmentRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deletes files that were uploaded straight to S3 but never attached to anything.
 *
 * <p>The browser uploads as soon as a file is picked, so closing the tab (or removing the attachment
 * before sending) leaves an object behind that nothing references. Without this they would accumulate
 * forever and quietly cost storage — which matters more now that a single one can be 100MB.
 */
@Service
@RequiredArgsConstructor
public class OrphanUploadCleanup {

    private static final Logger log = LoggerFactory.getLogger(OrphanUploadCleanup.class);

    /** Comfortably longer than the presign TTL, so an upload in progress is never swept. */
    private static final Duration GRACE = Duration.ofHours(24);

    private final StorageService storage;
    private final CommentAttachmentRepository commentAttachments;
    private final TaskAttachmentRepository taskAttachments;

    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Tashkent")
    public void deleteOrphans() {
        try {
            List<String> candidates = storage.listDirectUploadsBefore(Instant.now().minus(GRACE));
            if (candidates.isEmpty()) {
                return;
            }
            // Anything actually referenced by an attachment row is keeping a real file — never touch those.
            Set<String> referenced = new HashSet<>(commentAttachments.findAllFilePaths());
            referenced.addAll(taskAttachments.findAllFilePaths());

            int deleted = 0;
            for (String key : candidates) {
                if (!referenced.contains(key)) {
                    storage.delete(key);
                    deleted++;
                }
            }
            log.info("Orphan upload cleanup: {} of {} stale objects deleted", deleted, candidates.size());
        } catch (Exception e) {
            log.error("Orphan upload cleanup failed", e);
        }
    }
}

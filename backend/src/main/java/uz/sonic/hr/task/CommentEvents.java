package uz.sonic.hr.task;

import java.util.Set;

public final class CommentEvents {

    private CommentEvents() {
    }

    /**
     * A comment was added to a task.
     *
     * @param commentId      ID of the created comment
     * @param taskId         ID of the task
     * @param taskTitle      Title of the task
     * @param authorId       ID of the comment author
     * @param authorName     Name of the comment author
     * @param mentionedIds   Employee IDs explicitly @mentioned (excluding the author)
     * @param participantIds The people the task belongs to — its creator, assignee and reviewer — who
     *                       should hear about any comment even without a mention. Already de-duplicated
     *                       and stripped of the author and of anyone in {@code mentionedIds}, so a single
     *                       person can never be notified twice for the same comment.
     */
    public record CommentAdded(Long commentId, Long taskId, String taskTitle,
                               Long authorId, String authorName, String content,
                               Set<Long> mentionedIds, Set<Long> participantIds) {
    }
}

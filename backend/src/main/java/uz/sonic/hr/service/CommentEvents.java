package uz.sonic.hr.service;

import java.util.Set;

public final class CommentEvents {

    private CommentEvents() {
    }

    /**
     * A comment was added to a task, potentially mentioning team members.
     *
     * @param commentId      ID of the created comment
     * @param taskId         ID of the task
     * @param taskTitle      Title of the task
     * @param authorId       ID of the comment author
     * @param authorName     Name of the comment author
     * @param mentionedIds   Set of employee IDs that were mentioned (excluding author)
     */
    public record CommentAdded(Long commentId, Long taskId, String taskTitle,
                               Long authorId, String authorName, Set<Long> mentionedIds) {
    }
}

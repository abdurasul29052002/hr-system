package uz.sonic.hr.it;

import org.junit.jupiter.api.Test;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Threaded (Telegram-style, one level deep) comment replies against real PostgreSQL:
 * <ul>
 *   <li>a reply carries the parent's id, author name and a preview back to the client;</li>
 *   <li>a parent that belongs to a different task is rejected;</li>
 *   <li>deleting the parent leaves the reply standing (ON DELETE SET NULL), only dropping the quote.</li>
 * </ul>
 */
class CommentReplyIT extends AbstractPostgresIT {

    @Test
    void replyCarriesParentQuote_andAppearsInThread() throws Exception {
        String token = registerAndToken("reply_leader");
        long teamId = createTeam(token, "Reply Team");
        long taskId = createTask(token, teamId, "Task with a thread");

        long parentId = addComment(token, teamId, taskId, "the parent comment", null);

        // The reply echoes the parent's id, author and a short preview so the UI can render the quote.
        String reply = mockMvc.perform(post("/api/tasks/" + taskId + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Team-Id", teamId)
                        .contentType(APPLICATION_JSON)
                        .content("{\"content\":\"a reply\",\"parentCommentId\":" + parentId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentCommentId").value(parentId))
                .andExpect(jsonPath("$.parentAuthorName").value("reply_leader"))
                .andExpect(jsonPath("$.parentPreview").value("the parent comment"))
                .andReturn().getResponse().getContentAsString();
        long replyId = objectMapper.readTree(reply).get("id").asLong();

        // Both comments come back in the thread, and the reply still points at its parent.
        mockMvc.perform(get("/api/tasks/" + taskId + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Team-Id", teamId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id == " + replyId + ")].parentCommentId").value(parentId));
    }

    @Test
    void replyToParentInAnotherTask_isRejected() throws Exception {
        String token = registerAndToken("reply_crosstask");
        long teamId = createTeam(token, "Cross Task Team");
        long taskA = createTask(token, teamId, "Task A");
        long taskB = createTask(token, teamId, "Task B");

        long parentInA = addComment(token, teamId, taskA, "lives in task A", null);

        // Quoting task A's comment while commenting on task B mixes threads — must be rejected.
        mockMvc.perform(post("/api/tasks/" + taskB + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Team-Id", teamId)
                        .contentType(APPLICATION_JSON)
                        .content("{\"content\":\"wrong thread\",\"parentCommentId\":" + parentInA + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingParent_leavesReplyStanding_withoutTheQuote() throws Exception {
        String token = registerAndToken("reply_delparent");
        long teamId = createTeam(token, "Delete Parent Team");
        long taskId = createTask(token, teamId, "Task with a deleted parent");

        long parentId = addComment(token, teamId, taskId, "doomed parent", null);
        addComment(token, teamId, taskId, "surviving reply", parentId);

        // Delete the parent — ON DELETE SET NULL must NOT cascade to the reply.
        mockMvc.perform(delete("/api/comments/" + parentId)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Team-Id", teamId))
                .andExpect(status().isNoContent());

        // The reply is still there; its quote is gone (parent fields null).
        mockMvc.perform(get("/api/tasks/" + taskId + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Team-Id", teamId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].content").value("surviving reply"))
                .andExpect(jsonPath("$[0].parentCommentId").doesNotExist());
    }

    // ---------------------------------------------------------------- helpers

    private long addComment(String token, long teamId, long taskId, String content, Long parentCommentId)
            throws Exception {
        String parentJson = parentCommentId == null ? "null" : parentCommentId.toString();
        String response = mockMvc.perform(post("/api/tasks/" + taskId + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Team-Id", teamId)
                        .contentType(APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\",\"parentCommentId\":" + parentJson + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private long createTask(String token, long teamId, String title) throws Exception {
        String created = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Team-Id", teamId)
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }

    private String registerAndToken(String username) throws Exception {
        String body = """
                {"fullName":"%s","username":"%s","password":"password123","language":"EN"}
                """.formatted(username, username);
        String response = mockMvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private long createTeam(String token, String name) throws Exception {
        String response = mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("teamId").asLong();
    }
}

package uz.sonic.hr.common.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Turns an over-limit upload into a readable 413 instead of Spring's default 500.
 *
 * The frontend caps uploads before sending, but that check is trivially bypassed and says nothing about
 * a request that is under the per-file limit yet over the total request limit — so the server needs its
 * own clear answer.
 */
@RestControllerAdvice
public class UploadExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleTooLarge(MaxUploadSizeExceededException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.PAYLOAD_TOO_LARGE);
        problem.setDetail("File is too large. The limit is 100 MB per file and 220 MB per upload.");
        return problem;
    }
}

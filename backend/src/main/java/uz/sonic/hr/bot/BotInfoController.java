package uz.sonic.hr.bot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tells the web client whether the Telegram bot is configured and, if so, its
 * username — so the UI can build a {@code https://t.me/<username>?start=<code>}
 * deep link for one-tap account linking. The username is public information
 * (anyone can find the bot), so this endpoint carries nothing sensitive.
 */
@RestController
@RequestMapping("/api/bot-info")
public class BotInfoController {

    private final String botUsername;
    private final boolean enabled;

    public BotInfoController(@Value("${app.bot.token:}") String botToken,
                             @Value("${app.bot.username:}") String botUsername) {
        String trimmed = botUsername == null ? "" : botUsername.trim();
        this.enabled = botToken != null && !botToken.isBlank() && !trimmed.isBlank();
        this.botUsername = this.enabled ? stripAt(trimmed) : null;
    }

    @GetMapping
    public BotInfo get() {
        return new BotInfo(enabled, botUsername);
    }

    /** Accept "@my_bot" or "my_bot" in config; the deep link needs the bare username. */
    private static String stripAt(String username) {
        return username.startsWith("@") ? username.substring(1) : username;
    }

    public record BotInfo(boolean enabled, String username) {
    }
}

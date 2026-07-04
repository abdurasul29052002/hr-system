package uz.sonic.hr.bot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class BotConfig {

    private final HrTelegramBot bot;

    @EventListener(ApplicationReadyEvent.class)
    public void registerBot() {
        if (!bot.isEnabled()) {
            log.warn("Telegram bot is disabled: set HR_BOT_TOKEN and HR_BOT_USERNAME to enable it");
            return;
        }
        try {
            new TelegramBotsApi(DefaultBotSession.class).registerBot(bot);
            log.info("Telegram bot @{} registered", bot.getBotUsername());
        } catch (Exception e) {
            log.error("Failed to register telegram bot", e);
        }
    }
}

package uz.sonic.hr.task;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.sonic.hr.bot.HrTelegramBot;
import uz.sonic.hr.common.dto.Dtos.TeamDigestDto;

import java.util.List;

/**
 * Sends every team's leaders/managers a morning Telegram digest — who is working on what, and which tasks
 * are still open — a daily nudge to keep them engaged with the board. Telegram-only (a morning ping that
 * pulls people back to the app); the web bell is reserved for actionable per-event notifications.
 */
@Service
@RequiredArgsConstructor
public class DailyDigestService {

    private static final Logger log = LoggerFactory.getLogger(DailyDigestService.class);

    private final StatsService statsService;
    private final HrTelegramBot bot;

    /**
     * 08:00 every day, Uzbekistan time (fixed zone, matches the rest of the reporting). Cron fields are
     * second minute hour day-of-month month day-of-week. Guarded end-to-end so one team's failure — or a
     * disabled bot — never aborts the run.
     */
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Tashkent")
    public void sendMorningDigests() {
        List<TeamDigestDto> digests;
        try {
            digests = statsService.buildDailyDigests();
        } catch (Exception e) {
            log.error("Failed to build daily digests", e);
            return;
        }
        int sent = 0;
        for (TeamDigestDto digest : digests) {
            // Nothing happening in this team → don't send an empty "all quiet" message.
            if (digest.workers().isEmpty() && digest.openTasks().isEmpty()) {
                continue;
            }
            try {
                bot.sendDailyDigest(digest);
                sent++;
            } catch (Exception e) {
                log.error("Failed to send daily digest for team {}", digest.teamId(), e);
            }
        }
        log.info("Daily morning digest: dispatched for {} of {} team(s)", sent, digests.size());
    }
}

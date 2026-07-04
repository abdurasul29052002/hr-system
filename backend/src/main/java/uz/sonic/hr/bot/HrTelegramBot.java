package uz.sonic.hr.bot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import uz.sonic.hr.entity.*;
import uz.sonic.hr.repo.EmployeeRepository;
import uz.sonic.hr.repo.TagRepository;
import uz.sonic.hr.repo.TeamMembershipRepository;
import uz.sonic.hr.service.StatsService;
import uz.sonic.hr.service.TaskEvents;
import uz.sonic.hr.service.TaskService;
import uz.sonic.hr.service.TeamService;
import uz.sonic.hr.web.dto.Dtos.MonthlyStats;
import uz.sonic.hr.web.dto.Dtos.TagDto;
import uz.sonic.hr.web.dto.Dtos.TaskDto;
import uz.sonic.hr.web.dto.Dtos.TaskRequest;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@Slf4j
public class HrTelegramBot extends TelegramLongPollingBot {

    private enum State {NONE, AWAIT_CODE, T_TITLE, T_DESC, T_DEADLINE}

    private static class Session {
        Language language = Language.EN;
        State state = State.NONE;
        String draftTitle;
        String draftDescription;
        TaskPriority draftPriority;
        final Set<Long> draftTagIds = new LinkedHashSet<>();
    }

    private final String botUsername;
    private final boolean enabled;
    private final EmployeeRepository employeeRepository;
    private final TeamMembershipRepository membershipRepository;
    private final TagRepository tagRepository;
    private final TaskService taskService;
    private final StatsService statsService;
    private final TaskCommentService taskCommentService;
    private final Map<Long, Session> sessions = new ConcurrentHashMap<>();

    public HrTelegramBot(@Value("${app.bot.token}") String token,
                         @Value("${app.bot.username}") String botUsername,
                         EmployeeRepository employeeRepository,
                         TeamMembershipRepository membershipRepository,
                         TagRepository tagRepository,
                         TaskService taskService,
                         StatsService statsService,
                         TaskCommentService taskCommentService) {
        super(token == null || token.isBlank() ? "disabled" : token);
        this.botUsername = botUsername == null || botUsername.isBlank() ? "hr_bot" : botUsername;
        this.enabled = token != null && !token.isBlank();
        this.employeeRepository = employeeRepository;
        this.membershipRepository = membershipRepository;
        this.tagRepository = tagRepository;
        this.taskService = taskService;
        this.statsService = statsService;
        this.taskCommentService = taskCommentService;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                handleCallback(update.getCallbackQuery());
            } else if (update.hasMessage() && update.getMessage().hasText()) {
                org.telegram.telegrambots.meta.api.objects.Message replyTo =
                    update.getMessage().getReplyToMessage();
                handleText(update.getMessage().getChatId(), update.getMessage().getText().trim(), replyTo);
            }
        } catch (Exception e) {
            log.error("Bot update handling failed", e);
        }
    }

    // ---------------------------------------------------------------- team context

    /**
     * Resolves the member's currently selected team. When the user belongs to several
     * teams and none is selected yet, sends a team chooser and returns null.
     */
    private TeamMembership resolveMembership(Long chatId, Employee employee, Language lang) {
        List<TeamMembership> memberships =
                membershipRepository.findAllByEmployeeIdOrderByJoinedAtAsc(employee.getId());
        if (memberships.isEmpty()) {
            send(chatId, BotMessages.get(lang, "no_team"), null);
            return null;
        }
        if (employee.getBotTeamId() != null) {
            for (TeamMembership membership : memberships) {
                if (membership.getTeam().getId().equals(employee.getBotTeamId())) {
                    return membership;
                }
            }
        }
        if (memberships.size() == 1) {
            TeamMembership only = memberships.getFirst();
            employee.setBotTeamId(only.getTeam().getId());
            employeeRepository.save(employee);
            return only;
        }
        sendTeamChooser(chatId, lang, memberships);
        return null;
    }

    private void sendTeamChooser(Long chatId, Language lang, List<TeamMembership> memberships) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (TeamMembership membership : memberships) {
            rows.add(List.of(inlineBtn(membership.getTeam().getName()
                    + " (" + membership.getRole().name() + ")", "team:" + membership.getTeam().getId())));
        }
        executeSafe(SendMessage.builder()
                .chatId(chatId.toString())
                .text(BotMessages.get(lang, "choose_team"))
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build());
    }

    // ---------------------------------------------------------------- text

    private void handleText(Long chatId, String text, org.telegram.telegrambots.meta.api.objects.Message replyToMessage) {
        Session session = sessions.computeIfAbsent(chatId, id -> new Session());
        Employee employee = employeeRepository.findByTelegramChatId(chatId).orElse(null);
        Language lang = employee != null ? employee.getLanguage() : session.language;

        // Check if this is a reply to a task notification
        if (employee != null && replyToMessage != null) {
            Long taskId = replyMessageMap.get(replyToMessage.getMessageId());
            if (taskId != null) {
                // User is replying to a task notification - add as comment
                try {
                    taskCommentService.addCommentFromTelegram(taskId, employee, text, (long) replyToMessage.getMessageId());
                    send(chatId, "✅ " + BotMessages.get(lang, "comment_added_to_task", "#" + taskId),
                         menuKeyboard(employee, getCurrentMembership(employee)));
                } catch (Exception e) {
                    send(chatId, "❌ Failed: " + e.getMessage(), null);
                }
                return;
            }
        }

        if (text.equals("/start")) {
            session.state = State.NONE;
            if (employee == null) {
                sendLanguageChooser(chatId);
                return;
            }
            TeamMembership membership = resolveMembership(chatId, employee, lang);
            if (membership != null) {
                sendMenu(chatId, employee, membership);
            }
            return;
        }
        if (text.equals("/cancel")) {
            session.state = State.NONE;
            send(chatId, BotMessages.get(lang, "cancelled"), null);
            return;
        }

        if (employee == null) {
            if (session.state == State.AWAIT_CODE) {
                linkByCode(chatId, session, text);
            } else {
                sendLanguageChooser(chatId);
            }
            return;
        }

        TeamMembership membership = resolveMembership(chatId, employee, lang);
        if (membership == null) {
            return;
        }

        // task-creation wizard (LEADER/MANAGER)
        switch (session.state) {
            case T_TITLE -> {
                session.draftTitle = text;
                session.state = State.T_DESC;
                send(chatId, BotMessages.get(lang, "ask_desc"), null);
                return;
            }
            case T_DESC -> {
                session.draftDescription = text.equals("-") ? null : text;
                session.state = State.NONE;
                sendPriorityChooser(chatId, lang);
                return;
            }
            case T_DEADLINE -> {
                LocalDate deadline = null;
                if (!text.equals("-")) {
                    deadline = parseDate(text);
                    if (deadline == null) {
                        send(chatId, BotMessages.get(lang, "bad_date"), null);
                        return;
                    }
                }
                session.state = State.NONE;
                TaskDto task = taskService.create(
                        new TaskRequest(session.draftTitle, session.draftDescription, session.draftPriority,
                                deadline, new ArrayList<>(session.draftTagIds), null),
                        membership);
                session.draftTagIds.clear();
                send(chatId, BotMessages.get(lang, "created", "#" + task.id() + " " + task.title()),
                        menuKeyboard(employee, membership));
                return;
            }
            default -> {
            }
        }

        // menu buttons
        if (BotMessages.isButton(text, "btn_language")) {
            sendLanguageChooser(chatId);
        } else if (BotMessages.isButton(text, "btn_team")) {
            sendTeamChooser(chatId, lang,
                    membershipRepository.findAllByEmployeeIdOrderByJoinedAtAsc(employee.getId()));
        } else if (BotMessages.isButton(text, "btn_open_tasks")) {
            sendTaskList(chatId, lang, taskService.list(TaskStatus.OPEN, null, membership), "btn_take", "take");
        } else if (BotMessages.isButton(text, "btn_my_tasks")) {
            sendTaskList(chatId, lang,
                    taskService.list(TaskStatus.IN_PROGRESS, employee.getId(), membership), "btn_done", "done");
        } else if (BotMessages.isButton(text, "btn_done_tasks")) {
            sendTaskList(chatId, lang,
                    limit(taskService.list(TaskStatus.DONE, employee.getId(), membership)), null, null);
        } else if (TeamService.isManagerOrLeader(membership) && BotMessages.isButton(text, "btn_new_task")) {
            session.state = State.T_TITLE;
            session.draftTagIds.clear();
            send(chatId, BotMessages.get(lang, "ask_title"), null);
        } else if (TeamService.isManagerOrLeader(membership) && BotMessages.isButton(text, "btn_testing")) {
            sendReviewList(chatId, lang, taskService.list(TaskStatus.TESTING, null, membership));
        } else if (TeamService.isManagerOrLeader(membership) && BotMessages.isButton(text, "btn_all_tasks")) {
            sendTaskList(chatId, lang, limit(taskService.list(null, null, membership)), null, null);
        } else if (TeamService.isManagerOrLeader(membership) && BotMessages.isButton(text, "btn_stats")) {
            sendStats(chatId, lang, membership);
        } else {
            sendMenu(chatId, employee, membership);
        }
    }

    private void linkByCode(Long chatId, Session session, String text) {
        Employee found = employeeRepository.findByTelegramLinkCode(text.toUpperCase()).orElse(null);
        if (found == null || !found.isActive()) {
            send(chatId, BotMessages.get(session.language, "invalid_code"), null);
            return;
        }
        found.setTelegramChatId(chatId);
        found.setLanguage(session.language);
        employeeRepository.save(found);
        session.state = State.NONE;
        send(chatId, BotMessages.get(session.language, "linked", found.getFullName()), null);
        TeamMembership membership = resolveMembership(chatId, found, session.language);
        if (membership != null) {
            sendMenu(chatId, found, membership);
        }
    }

    // ---------------------------------------------------------------- callbacks

    private void handleCallback(CallbackQuery cq) {
        Long chatId = cq.getMessage().getChatId();
        String data = cq.getData();
        Session session = sessions.computeIfAbsent(chatId, id -> new Session());
        Employee employee = employeeRepository.findByTelegramChatId(chatId).orElse(null);
        Language lang = employee != null ? employee.getLanguage() : session.language;

        try {
            if (data.startsWith("lang:")) {
                Language chosen = Language.valueOf(data.substring(5));
                session.language = chosen;
                if (employee != null) {
                    employee.setLanguage(chosen);
                    employeeRepository.save(employee);
                    send(chatId, BotMessages.get(chosen, "language_set"), null);
                    TeamMembership membership = resolveMembership(chatId, employee, chosen);
                    if (membership != null) {
                        sendMenu(chatId, employee, membership);
                    }
                } else {
                    session.state = State.AWAIT_CODE;
                    send(chatId, BotMessages.get(chosen, "language_set") + "\n\n"
                            + BotMessages.get(chosen, "enter_code"), null);
                }
            } else if (data.startsWith("team:") && employee != null) {
                long teamId = Long.parseLong(data.substring(5));
                membershipRepository.findByEmployeeIdAndTeamId(employee.getId(), teamId)
                        .ifPresent(membership -> {
                            employee.setBotTeamId(teamId);
                            employeeRepository.save(employee);
                            send(chatId, BotMessages.get(lang, "team_set", membership.getTeam().getName()), null);
                            sendMenu(chatId, employee, membership);
                        });
            } else if (employee != null) {
                TeamMembership membership = resolveMembership(chatId, employee, lang);
                if (membership == null) {
                    return;
                }
                if (data.startsWith("prio:")) {
                    session.draftPriority = TaskPriority.valueOf(data.substring(5));
                    startTagStepOrDeadline(chatId, session, lang, membership);
                } else if (data.startsWith("tg:")) {
                    long tagId = Long.parseLong(data.substring(3));
                    if (!session.draftTagIds.remove(tagId)) {
                        session.draftTagIds.add(tagId);
                    }
                    updateTagKeyboard(chatId, cq.getMessage().getMessageId(), session, lang, membership);
                } else if (data.equals("tgdone")) {
                    session.state = State.T_DEADLINE;
                    send(chatId, BotMessages.get(lang, "ask_deadline"), null);
                } else if (data.startsWith("take:")) {
                    try {
                        TaskDto task = taskService.take(Long.parseLong(data.substring(5)), membership);
                        send(chatId, BotMessages.get(lang, "take_ok", "#" + task.id() + " " + task.title()), null);
                    } catch (Exception e) {
                        send(chatId, BotMessages.get(lang, "take_fail"), null);
                    }
                } else if (data.startsWith("done:")) {
                    try {
                        TaskDto task = taskService.complete(Long.parseLong(data.substring(5)), membership);
                        String key = task.status() == TaskStatus.TESTING ? "test_ok" : "done_ok";
                        send(chatId, BotMessages.get(lang, key, "#" + task.id() + " " + task.title()), null);
                    } catch (Exception e) {
                        send(chatId, BotMessages.get(lang, "done_fail"), null);
                    }
                } else if (data.startsWith("appr:")) {
                    try {
                        TaskDto task = taskService.approve(Long.parseLong(data.substring(5)), membership);
                        send(chatId, BotMessages.get(lang, "approve_ok", "#" + task.id() + " " + task.title()), null);
                    } catch (Exception e) {
                        send(chatId, BotMessages.get(lang, "review_fail"), null);
                    }
                } else if (data.startsWith("rej:")) {
                    try {
                        TaskDto task = taskService.reject(Long.parseLong(data.substring(4)), membership);
                        send(chatId, BotMessages.get(lang, "reject_ok", "#" + task.id() + " " + task.title()), null);
                    } catch (Exception e) {
                        send(chatId, BotMessages.get(lang, "review_fail"), null);
                    }
                }
            }
        } finally {
            try {
                execute(AnswerCallbackQuery.builder().callbackQueryId(cq.getId()).build());
            } catch (Exception e) {
                log.debug("answerCallbackQuery failed", e);
            }
        }
    }

    // ---------------------------------------------------------------- tag step

    private void startTagStepOrDeadline(Long chatId, Session session, Language lang, TeamMembership membership) {
        List<Tag> tags = tagRepository.findAllByTeamIdOrderByNameAsc(membership.getTeam().getId());
        if (tags.isEmpty()) {
            session.state = State.T_DEADLINE;
            send(chatId, BotMessages.get(lang, "ask_deadline"), null);
            return;
        }
        executeSafe(SendMessage.builder()
                .chatId(chatId.toString())
                .text(BotMessages.get(lang, "ask_tags"))
                .replyMarkup(tagKeyboard(tags, session.draftTagIds, lang))
                .build());
    }

    private void updateTagKeyboard(Long chatId, Integer messageId, Session session, Language lang,
                                   TeamMembership membership) {
        List<Tag> tags = tagRepository.findAllByTeamIdOrderByNameAsc(membership.getTeam().getId());
        try {
            execute(EditMessageReplyMarkup.builder()
                    .chatId(chatId.toString())
                    .messageId(messageId)
                    .replyMarkup(tagKeyboard(tags, session.draftTagIds, lang))
                    .build());
        } catch (Exception e) {
            log.debug("Failed to update tag keyboard", e);
        }
    }

    private InlineKeyboardMarkup tagKeyboard(List<Tag> tags, Set<Long> selected, Language lang) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        for (Tag tag : tags) {
            String label = (selected.contains(tag.getId()) ? "✅ " : "") + tag.getName();
            row.add(inlineBtn(label, "tg:" + tag.getId()));
            if (row.size() == 2) {
                rows.add(row);
                row = new ArrayList<>();
            }
        }
        if (!row.isEmpty()) {
            rows.add(row);
        }
        rows.add(List.of(inlineBtn(BotMessages.get(lang, "btn_tags_continue"), "tgdone")));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    // ---------------------------------------------------------------- senders

    private void sendLanguageChooser(Long chatId) {
        executeSafe(SendMessage.builder()
                .chatId(chatId.toString())
                .text("🌐 Choose a language / Выберите язык / Tilni tanlang:")
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboard(List.of(List.of(
                                inlineBtn("English 🇬🇧", "lang:EN"),
                                inlineBtn("Русский 🇷🇺", "lang:RU"),
                                inlineBtn("O'zbekcha 🇺🇿", "lang:UZ"))))
                        .build())
                .build());
    }

    private void sendPriorityChooser(Long chatId, Language lang) {
        executeSafe(SendMessage.builder()
                .chatId(chatId.toString())
                .text(BotMessages.get(lang, "ask_priority"))
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboard(List.of(List.of(
                                inlineBtn(BotMessages.get(lang, "prio_LOW"), "prio:LOW"),
                                inlineBtn(BotMessages.get(lang, "prio_MEDIUM"), "prio:MEDIUM"),
                                inlineBtn(BotMessages.get(lang, "prio_HIGH"), "prio:HIGH"))))
                        .build())
                .build());
    }

    private void sendMenu(Long chatId, Employee employee, TeamMembership membership) {
        send(chatId, membership.getTeam().getName() + " — " + BotMessages.get(employee.getLanguage(), "menu"),
                menuKeyboard(employee, membership));
    }

    private ReplyKeyboardMarkup menuKeyboard(Employee employee, TeamMembership membership) {
        Language lang = employee.getLanguage();
        boolean multiTeam = membershipRepository.findAllByEmployeeIdOrderByJoinedAtAsc(employee.getId()).size() > 1;
        List<KeyboardRow> rows = new ArrayList<>();
        if (TeamService.isManagerOrLeader(membership)) {
            KeyboardRow r1 = new KeyboardRow();
            r1.add(BotMessages.get(lang, "btn_new_task"));
            r1.add(BotMessages.get(lang, "btn_all_tasks"));
            KeyboardRow r2 = new KeyboardRow();
            r2.add(BotMessages.get(lang, "btn_open_tasks"));
            r2.add(BotMessages.get(lang, "btn_testing"));
            r2.add(BotMessages.get(lang, "btn_stats"));
            rows.add(r1);
            rows.add(r2);
        } else {
            KeyboardRow r1 = new KeyboardRow();
            r1.add(BotMessages.get(lang, "btn_open_tasks"));
            r1.add(BotMessages.get(lang, "btn_my_tasks"));
            KeyboardRow r2 = new KeyboardRow();
            r2.add(BotMessages.get(lang, "btn_done_tasks"));
            rows.add(r1);
            rows.add(r2);
        }
        KeyboardRow last = new KeyboardRow();
        last.add(BotMessages.get(lang, "btn_language"));
        if (multiTeam) {
            last.add(BotMessages.get(lang, "btn_team"));
        }
        rows.add(last);
        return ReplyKeyboardMarkup.builder()
                .keyboard(rows)
                .resizeKeyboard(true)
                .build();
    }

    /** Tasks in review, each with Approve / Return buttons. */
    private void sendReviewList(Long chatId, Language lang, List<TaskDto> tasks) {
        if (tasks.isEmpty()) {
            send(chatId, BotMessages.get(lang, "no_tasks"), null);
            return;
        }
        for (TaskDto task : tasks.subList(0, Math.min(tasks.size(), 10))) {
            executeSafe(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(taskLine(lang, task))
                    .replyMarkup(InlineKeyboardMarkup.builder()
                            .keyboard(List.of(List.of(
                                    inlineBtn(BotMessages.get(lang, "btn_approve"), "appr:" + task.id()),
                                    inlineBtn(BotMessages.get(lang, "btn_reject"), "rej:" + task.id()))))
                            .build())
                    .build());
        }
    }

    private void sendTaskList(Long chatId, Language lang, List<TaskDto> tasks, String buttonKey, String action) {
        if (tasks.isEmpty()) {
            send(chatId, BotMessages.get(lang, "no_tasks"), null);
            return;
        }
        for (TaskDto task : tasks.subList(0, Math.min(tasks.size(), 10))) {
            SendMessage.SendMessageBuilder builder = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(taskLine(lang, task));
            if (buttonKey != null) {
                builder.replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboard(List.of(List.of(
                                inlineBtn(BotMessages.get(lang, buttonKey), action + ":" + task.id()))))
                        .build());
            }
            executeSafe(builder.build());
        }
    }

    private String taskLine(Language lang, TaskDto task) {
        StringBuilder sb = new StringBuilder();
        sb.append("#").append(task.id()).append(" ")
                .append(BotMessages.priority(lang, task.priority())).append("\n")
                .append(task.title());
        if (task.description() != null && !task.description().isBlank()) {
            sb.append("\n\n").append(task.description());
        }
        if (!task.tags().isEmpty()) {
            sb.append("\n🏷 ").append(BotMessages.get(lang, "tags")).append(": ")
                    .append(task.tags().stream().map(TagDto::name).collect(Collectors.joining(", ")));
        }
        if (task.deadline() != null) {
            sb.append("\n📅 ").append(BotMessages.get(lang, "deadline")).append(": ")
                    .append(task.deadline().format(DateTimeFormatter.ISO_LOCAL_DATE));
        }
        sb.append("\n").append(BotMessages.get(lang, "status")).append(": ")
                .append(BotMessages.get(lang, "st_" + task.status().name()));
        if (task.assigneeName() != null) {
            sb.append(" — ").append(task.assigneeName());
        }
        return sb.toString();
    }

    private void sendStats(Long chatId, Language lang, TeamMembership membership) {
        YearMonth now = YearMonth.now();
        MonthlyStats stats = statsService.monthly(membership, now.getYear(), now.getMonthValue());
        StringBuilder sb = new StringBuilder();
        sb.append(BotMessages.get(lang, "stats_header", stats.year(), stats.month())).append("\n\n")
                .append(BotMessages.get(lang, "stats_totals",
                        stats.totalCreated(), stats.totalCompleted(), stats.totalInProgress(), stats.totalOpen()));
        if (!stats.perEmployee().isEmpty()) {
            sb.append("\n");
            stats.perEmployee().forEach(row -> sb.append("\n")
                    .append(BotMessages.get(lang, "stats_row", row.fullName(), row.taken(), row.completed())));
        }
        send(chatId, sb.toString(), null);
    }

    // ---------------------------------------------------------------- notifications

    @Async
    @EventListener
    public void onTaskCreated(TaskEvents.TaskCreated event) {
        if (!enabled) {
            return;
        }
        String ref = "#" + event.taskId() + " " + event.title();
        if (event.assigneeId() == null) {
            // open-pool task: broadcast to members so they can take it
            for (TeamMembership membership : membershipRepository.findLinkedByTeamIdAndRoleIn(
                    event.teamId(), List.of(Role.MEMBER))) {
                Employee member = membership.getEmployee();
                Language lang = member.getLanguage();
                StringBuilder body = new StringBuilder("#" + event.taskId() + " "
                        + BotMessages.priority(lang, event.priority()) + "\n" + event.title());
                if (event.deadline() != null) {
                    body.append("\n📅 ").append(BotMessages.get(lang, "deadline")).append(": ")
                            .append(event.deadline());
                }
                send(member.getTelegramChatId(), BotMessages.get(lang, "notif_new", body.toString()), null);
            }
        } else {
            notifyAssignee(event.assigneeId(), ref);
        }
        notifyManagement(event.teamId(), event.actorId(), "notif_created", event.creatorName(), ref);
    }

    @Async
    @EventListener
    public void onTaskAssigned(TaskEvents.TaskAssigned event) {
        if (!enabled) {
            return;
        }
        String ref = "#" + event.taskId() + " " + event.title();
        notifyAssignee(event.assigneeId(), ref);
        String assigneeName = employeeRepository.findById(event.assigneeId())
                .map(Employee::getFullName).orElse("?");
        notifyManagement(event.teamId(), event.actorId(), "notif_assigned_mgr",
                event.assignerName(), assigneeName, ref);
    }

    @Async
    @EventListener
    public void onTaskTaken(TaskEvents.TaskTaken event) {
        notifyManagement(event.teamId(), event.actorId(), "notif_taken", event.workerName(),
                "#" + event.taskId() + " " + event.title());
    }

    @Async
    @EventListener
    public void onTaskSubmitted(TaskEvents.TaskSubmitted event) {
        notifyManagement(event.teamId(), event.actorId(), "notif_testing", event.workerName(),
                "#" + event.taskId() + " " + event.title());
    }

    @Async
    @EventListener
    public void onTaskApproved(TaskEvents.TaskApproved event) {
        if (!enabled) {
            return;
        }
        String ref = "#" + event.taskId() + " " + event.title();
        if (event.assigneeId() != null) {
            employeeRepository.findById(event.assigneeId())
                    .filter(e -> e.getTelegramChatId() != null)
                    .ifPresent(assignee -> send(assignee.getTelegramChatId(),
                            BotMessages.get(assignee.getLanguage(), "notif_approved", ref), null));
        }
        String workerName = event.assigneeId() != null
                ? employeeRepository.findById(event.assigneeId()).map(Employee::getFullName).orElse("?")
                : "?";
        notifyManagement(event.teamId(), event.actorId(), "notif_done", workerName, ref);
    }

    @Async
    @EventListener
    public void onTaskRejected(TaskEvents.TaskRejected event) {
        if (!enabled || event.assigneeId() == null) {
            return;
        }
        String ref = "#" + event.taskId() + " " + event.title();
        employeeRepository.findById(event.assigneeId())
                .filter(e -> e.getTelegramChatId() != null)
                .ifPresent(assignee -> send(assignee.getTelegramChatId(),
                        BotMessages.get(assignee.getLanguage(), "notif_rejected", ref), null));
    }

    @Async
    @EventListener
    public void onTaskCompleted(TaskEvents.TaskCompleted event) {
        notifyManagement(event.teamId(), event.actorId(), "notif_done", event.workerName(),
                "#" + event.taskId() + " " + event.title());
    }

    @Async
    @EventListener
    public void onCommentAdded(CommentEvents.CommentAdded event) {
        if (!enabled) {
            return;
        }
        // Notify mentioned users via Telegram
        String taskRef = "#" + event.taskId() + " " + event.taskTitle();
        String message = BotMessages.get(Language.EN, "notif_mentioned", event.authorName(), taskRef);

        for (Long mentionedId : event.mentionedIds()) {
            employeeRepository.findById(mentionedId)
                    .filter(e -> e.getTelegramChatId() != null)
                    .ifPresent(mentioned -> {
                        // Send with reply markup - user can reply directly to this message
                        SendMessage msg = SendMessage.builder()
                                .chatId(mentioned.getTelegramChatId().toString())
                                .text(BotMessages.get(mentioned.getLanguage(), "notif_mentioned",
                                        event.authorName(), taskRef))
                                .replyMarkup(org.telegram.telegrambots.meta.api.objects.replykeyboard.ForceReplyKeyboard.builder()
                                        .forceReply(true)
                                        .selective(true)
                                        .inputFieldPlaceholder("Reply to add comment...")
                                        .build())
                                .build();

                        try {
                            org.telegram.telegrambots.meta.api.objects.Message sentMsg = execute(msg);
                            // Store message mapping: telegram_message_id -> task_id
                            // We'll use this to handle replies
                            replyMessageMap.put(sentMsg.getMessageId(), event.taskId());
                        } catch (Exception e) {
                            log.error("Failed to send mention notification", e);
                        }
                    });
        }
    }

    // Map to track which Telegram messages are about which tasks
    private final java.util.Map<Integer, Long> replyMessageMap = new java.util.concurrent.ConcurrentHashMap<>();

    private void notifyAssignee(Long assigneeId, String taskRef) {
        employeeRepository.findById(assigneeId)
                .filter(e -> e.getTelegramChatId() != null)
                .ifPresent(assignee -> send(assignee.getTelegramChatId(),
                        BotMessages.get(assignee.getLanguage(), "notif_assigned", taskRef), null));
    }

    /** Notifies every linked LEADER and MANAGER of the team, except the actor themselves. */
    private void notifyManagement(Long teamId, Long actorId, String key, Object... args) {
        if (!enabled) {
            return;
        }
        for (TeamMembership membership : membershipRepository.findLinkedByTeamIdAndRoleIn(
                teamId, List.of(Role.LEADER, Role.MANAGER))) {
            Employee manager = membership.getEmployee();
            if (manager.getId().equals(actorId)) {
                continue;
            }
            send(manager.getTelegramChatId(), BotMessages.get(manager.getLanguage(), key, args), null);
        }
    }

    // ---------------------------------------------------------------- helpers

    private static InlineKeyboardButton inlineBtn(String text, String data) {
        return InlineKeyboardButton.builder().text(text).callbackData(data).build();
    }

    private static LocalDate parseDate(String text) {
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd.MM.yyyy"))) {
            try {
                return LocalDate.parse(text, formatter);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static <T> List<T> limit(List<T> list) {
        return list.subList(0, Math.min(list.size(), 10));
    }

    private void send(Long chatId, String text, ReplyKeyboard keyboard) {
        SendMessage.SendMessageBuilder builder = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text);
        if (keyboard != null) {
            builder.replyMarkup(keyboard);
        }
        executeSafe(builder.build());
    }

    private void executeSafe(SendMessage message) {
        if (!enabled) {
            return;
        }
        try {
            execute(message);
        } catch (Exception e) {
            log.error("Failed to send telegram message to chat {}", message.getChatId(), e);
        }
    }
}

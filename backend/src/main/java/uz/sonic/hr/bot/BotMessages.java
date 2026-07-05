package uz.sonic.hr.bot;

import uz.sonic.hr.common.enums.Language;
import uz.sonic.hr.common.enums.TaskPriority;

import java.util.EnumMap;
import java.util.Map;

/** All bot texts in EN / RU / UZ. */
public final class BotMessages {

    private static final Map<Language, Map<String, String>> M = new EnumMap<>(Language.class);

    static {
        M.put(Language.EN, Map.<String, String>ofEntries(
                Map.entry("language_set", "✅ Language set: English"),
                Map.entry("enter_code", "🔑 Enter the connection code you received from your team leader (visible in your web profile):"),
                Map.entry("invalid_code", "❌ Invalid code. Please try again or contact your team leader."),
                Map.entry("linked", "✅ Welcome, %s! Your Telegram is now connected."),
                Map.entry("no_team", "⚠️ You are not in a team yet. Open the web app and create your team first."),
                Map.entry("menu", "Choose an action:"),
                Map.entry("btn_open_tasks", "📋 Open tasks"),
                Map.entry("btn_my_tasks", "🧰 My tasks"),
                Map.entry("btn_done_tasks", "✅ Completed"),
                Map.entry("btn_language", "🌐 Language"),
                Map.entry("btn_team", "🔀 Team"),
                Map.entry("choose_team", "Choose a team:"),
                Map.entry("team_set", "✅ Team: %s"),
                Map.entry("btn_new_task", "➕ New task"),
                Map.entry("btn_all_tasks", "📋 All tasks"),
                Map.entry("btn_stats", "📊 Statistics"),
                Map.entry("no_tasks", "There are no tasks here yet."),
                Map.entry("btn_take", "✋ Take"),
                Map.entry("btn_done", "✅ Done"),
                Map.entry("take_ok", "✅ You took the task: %s"),
                Map.entry("take_fail", "❌ This task is no longer available."),
                Map.entry("done_ok", "🎉 Task completed: %s"),
                Map.entry("done_fail", "❌ This task cannot be completed."),
                Map.entry("test_ok", "🧪 Task sent for review: %s"),
                Map.entry("btn_testing", "🧪 In review"),
                Map.entry("btn_approve", "✅ Approve"),
                Map.entry("btn_reject", "↩️ Return"),
                Map.entry("approve_ok", "✅ Approved: %s"),
                Map.entry("reject_ok", "↩️ Returned for rework: %s"),
                Map.entry("review_fail", "❌ This task is not in review."),
                Map.entry("notif_testing", "🧪 %s submitted a task for review: %s"),
                Map.entry("notif_approved", "🎉 Your task was approved: %s"),
                Map.entry("notif_rejected", "↩️ Your task was returned for rework: %s"),
                Map.entry("ask_title", "📝 Send the task title:"),
                Map.entry("ask_desc", "📄 Send the description (or \"-\" to skip):"),
                Map.entry("ask_priority", "⚡ Choose the priority:"),
                Map.entry("ask_tags", "🏷 Choose tags (optional), then press Continue:"),
                Map.entry("btn_tags_continue", "➡️ Continue"),
                Map.entry("ask_deadline", "📅 Send the deadline as YYYY-MM-DD (or \"-\" for none):"),
                Map.entry("bad_date", "❌ Invalid date. Use the format YYYY-MM-DD, e.g. 2026-07-15."),
                Map.entry("created", "✅ Task created: %s"),
                Map.entry("cancelled", "Operation cancelled."),
                Map.entry("prio_LOW", "🟢 Low"),
                Map.entry("prio_MEDIUM", "🟡 Medium"),
                Map.entry("prio_HIGH", "🔴 High"),
                Map.entry("notif_new", "🆕 New task available!\n\n%s"),
                Map.entry("notif_created", "🆕 %s created a task: %s"),
                Map.entry("notif_assigned", "📌 A task was assigned to you:\n\n%s"),
                Map.entry("notif_assigned_mgr", "📌 %s assigned the task to %s: %s"),
                Map.entry("notif_taken", "👷 %s took the task: %s"),
                Map.entry("notif_done", "✅ %s completed the task: %s"),
                Map.entry("notif_mentioned", "💬 %s mentioned you in: %s"),
                Map.entry("comment_added_to_task", "Comment added to task %s"),
                Map.entry("stats_header", "📊 Statistics for %d-%02d"),
                Map.entry("stats_totals", "Created: %d\nCompleted: %d\nIn progress: %d\nOpen: %d"),
                Map.entry("stats_row", "• %s — taken %d, done %d"),
                Map.entry("not_linked", "Please send /start to begin."),
                Map.entry("deadline", "Deadline"),
                Map.entry("tags", "Tags"),
                Map.entry("status", "Status"),
                Map.entry("st_OPEN", "Open"),
                Map.entry("st_IN_PROGRESS", "In progress"),
                Map.entry("st_TESTING", "In review"),
                Map.entry("st_DONE", "Done"),
                Map.entry("st_CANCELLED", "Cancelled")));

        M.put(Language.RU, Map.<String, String>ofEntries(
                Map.entry("language_set", "✅ Язык установлен: Русский"),
                Map.entry("enter_code", "🔑 Введите код подключения, полученный от лидера команды (виден в вашем веб-профиле):"),
                Map.entry("invalid_code", "❌ Неверный код. Попробуйте снова или обратитесь к лидеру команды."),
                Map.entry("linked", "✅ Добро пожаловать, %s! Telegram подключён."),
                Map.entry("no_team", "⚠️ Вы ещё не состоите в команде. Сначала создайте команду в веб-приложении."),
                Map.entry("menu", "Выберите действие:"),
                Map.entry("btn_open_tasks", "📋 Открытые задачи"),
                Map.entry("btn_my_tasks", "🧰 Мои задачи"),
                Map.entry("btn_done_tasks", "✅ Выполненные"),
                Map.entry("btn_language", "🌐 Язык"),
                Map.entry("btn_team", "🔀 Команда"),
                Map.entry("choose_team", "Выберите команду:"),
                Map.entry("team_set", "✅ Команда: %s"),
                Map.entry("btn_new_task", "➕ Новая задача"),
                Map.entry("btn_all_tasks", "📋 Все задачи"),
                Map.entry("btn_stats", "📊 Статистика"),
                Map.entry("no_tasks", "Здесь пока нет задач."),
                Map.entry("btn_take", "✋ Взять"),
                Map.entry("btn_done", "✅ Готово"),
                Map.entry("take_ok", "✅ Вы взяли задачу: %s"),
                Map.entry("take_fail", "❌ Эта задача уже недоступна."),
                Map.entry("done_ok", "🎉 Задача выполнена: %s"),
                Map.entry("done_fail", "❌ Эту задачу нельзя завершить."),
                Map.entry("test_ok", "🧪 Задача отправлена на проверку: %s"),
                Map.entry("btn_testing", "🧪 На проверке"),
                Map.entry("btn_approve", "✅ Принять"),
                Map.entry("btn_reject", "↩️ Вернуть"),
                Map.entry("approve_ok", "✅ Принята: %s"),
                Map.entry("reject_ok", "↩️ Возвращена на доработку: %s"),
                Map.entry("review_fail", "❌ Эта задача не на проверке."),
                Map.entry("notif_testing", "🧪 %s отправил(а) задачу на проверку: %s"),
                Map.entry("notif_approved", "🎉 Ваша задача принята: %s"),
                Map.entry("notif_rejected", "↩️ Ваша задача возвращена на доработку: %s"),
                Map.entry("ask_title", "📝 Отправьте название задачи:"),
                Map.entry("ask_desc", "📄 Отправьте описание (или \"-\", чтобы пропустить):"),
                Map.entry("ask_priority", "⚡ Выберите приоритет:"),
                Map.entry("ask_tags", "🏷 Выберите теги (необязательно) и нажмите «Продолжить»:"),
                Map.entry("btn_tags_continue", "➡️ Продолжить"),
                Map.entry("ask_deadline", "📅 Отправьте срок в формате ГГГГ-ММ-ДД (или \"-\", если без срока):"),
                Map.entry("bad_date", "❌ Неверная дата. Используйте формат ГГГГ-ММ-ДД, например 2026-07-15."),
                Map.entry("created", "✅ Задача создана: %s"),
                Map.entry("cancelled", "Операция отменена."),
                Map.entry("prio_LOW", "🟢 Низкий"),
                Map.entry("prio_MEDIUM", "🟡 Средний"),
                Map.entry("prio_HIGH", "🔴 Высокий"),
                Map.entry("notif_new", "🆕 Доступна новая задача!\n\n%s"),
                Map.entry("notif_created", "🆕 %s создал(а) задачу: %s"),
                Map.entry("notif_assigned", "📌 Вам назначена задача:\n\n%s"),
                Map.entry("notif_assigned_mgr", "📌 %s назначил(а) задачу %s: %s"),
                Map.entry("notif_taken", "👷 %s взял(а) задачу: %s"),
                Map.entry("notif_done", "✅ %s выполнил(а) задачу: %s"),
                Map.entry("notif_mentioned", "💬 %s упомянул(а) вас в: %s"),
                Map.entry("comment_added_to_task", "Комментарий добавлен к задаче %s"),
                Map.entry("stats_header", "📊 Статистика за %d-%02d"),
                Map.entry("stats_totals", "Создано: %d\nВыполнено: %d\nВ работе: %d\nОткрыто: %d"),
                Map.entry("stats_row", "• %s — взято %d, выполнено %d"),
                Map.entry("not_linked", "Отправьте /start, чтобы начать."),
                Map.entry("deadline", "Срок"),
                Map.entry("tags", "Теги"),
                Map.entry("status", "Статус"),
                Map.entry("st_OPEN", "Открыта"),
                Map.entry("st_IN_PROGRESS", "В работе"),
                Map.entry("st_TESTING", "На проверке"),
                Map.entry("st_DONE", "Выполнена"),
                Map.entry("st_CANCELLED", "Отменена")));

        M.put(Language.UZ, Map.<String, String>ofEntries(
                Map.entry("language_set", "✅ Til tanlandi: O'zbekcha"),
                Map.entry("enter_code", "🔑 Team leaderdan olgan ulanish kodini kiriting (web-profilingizda ko'rinadi):"),
                Map.entry("invalid_code", "❌ Kod noto'g'ri. Qayta urinib ko'ring yoki team leaderga murojaat qiling."),
                Map.entry("linked", "✅ Xush kelibsiz, %s! Telegram ulandi."),
                Map.entry("no_team", "⚠️ Siz hali jamoada emassiz. Avval web-ilovada jamoangizni yarating."),
                Map.entry("menu", "Amalni tanlang:"),
                Map.entry("btn_open_tasks", "📋 Ochiq tasklar"),
                Map.entry("btn_my_tasks", "🧰 Mening tasklarim"),
                Map.entry("btn_done_tasks", "✅ Bajarilganlar"),
                Map.entry("btn_language", "🌐 Til"),
                Map.entry("btn_team", "🔀 Jamoa"),
                Map.entry("choose_team", "Jamoani tanlang:"),
                Map.entry("team_set", "✅ Jamoa: %s"),
                Map.entry("btn_new_task", "➕ Yangi task"),
                Map.entry("btn_all_tasks", "📋 Barcha tasklar"),
                Map.entry("btn_stats", "📊 Statistika"),
                Map.entry("no_tasks", "Hozircha bu yerda task yo'q."),
                Map.entry("btn_take", "✋ Olish"),
                Map.entry("btn_done", "✅ Bajarildi"),
                Map.entry("take_ok", "✅ Taskni oldingiz: %s"),
                Map.entry("take_fail", "❌ Bu task endi mavjud emas."),
                Map.entry("done_ok", "🎉 Task bajarildi: %s"),
                Map.entry("done_fail", "❌ Bu taskni yakunlab bo'lmaydi."),
                Map.entry("test_ok", "🧪 Task tekshiruvga yuborildi: %s"),
                Map.entry("btn_testing", "🧪 Testda"),
                Map.entry("btn_approve", "✅ Tasdiqlash"),
                Map.entry("btn_reject", "↩️ Qaytarish"),
                Map.entry("approve_ok", "✅ Tasdiqlandi: %s"),
                Map.entry("reject_ok", "↩️ Qayta ishlashga qaytarildi: %s"),
                Map.entry("review_fail", "❌ Bu task tekshiruvda emas."),
                Map.entry("notif_testing", "🧪 %s taskni tekshiruvga yubordi: %s"),
                Map.entry("notif_approved", "🎉 Taskingiz tasdiqlandi: %s"),
                Map.entry("notif_rejected", "↩️ Taskingiz qayta ishlashga qaytarildi: %s"),
                Map.entry("notif_mentioned", "💬 %s sizni eslatdi: %s"),
                Map.entry("comment_added_to_task", "Task %s ga izoh qo'shildi"),
                Map.entry("ask_title", "📝 Task nomini yuboring:"),
                Map.entry("ask_desc", "📄 Tavsifni yuboring (yoki o'tkazib yuborish uchun \"-\"):"),
                Map.entry("ask_priority", "⚡ Muhimlik darajasini tanlang:"),
                Map.entry("ask_tags", "🏷 Teglarni tanlang (ixtiyoriy), so'ng «Davom»ni bosing:"),
                Map.entry("btn_tags_continue", "➡️ Davom"),
                Map.entry("ask_deadline", "📅 Muddatni YYYY-MM-DD ko'rinishida yuboring (yoki \"-\" — muddatsiz):"),
                Map.entry("bad_date", "❌ Sana noto'g'ri. YYYY-MM-DD formatida yozing, masalan 2026-07-15."),
                Map.entry("created", "✅ Task yaratildi: %s"),
                Map.entry("cancelled", "Amal bekor qilindi."),
                Map.entry("prio_LOW", "🟢 Past"),
                Map.entry("prio_MEDIUM", "🟡 O'rta"),
                Map.entry("prio_HIGH", "🔴 Yuqori"),
                Map.entry("notif_new", "🆕 Yangi task mavjud!\n\n%s"),
                Map.entry("notif_created", "🆕 %s yangi task yaratdi: %s"),
                Map.entry("notif_assigned", "📌 Sizga task biriktirildi:\n\n%s"),
                Map.entry("notif_assigned_mgr", "📌 %s taskni %s ga biriktirdi: %s"),
                Map.entry("notif_taken", "👷 %s taskni oldi: %s"),
                Map.entry("notif_done", "✅ %s taskni bajardi: %s"),
                Map.entry("stats_header", "📊 %d-%02d oyi statistikasi"),
                Map.entry("stats_totals", "Yaratildi: %d\nBajarildi: %d\nJarayonda: %d\nOchiq: %d"),
                Map.entry("stats_row", "• %s — olindi %d, bajarildi %d"),
                Map.entry("not_linked", "Boshlash uchun /start yuboring."),
                Map.entry("deadline", "Muddat"),
                Map.entry("tags", "Teglar"),
                Map.entry("status", "Holat"),
                Map.entry("st_OPEN", "Ochiq"),
                Map.entry("st_IN_PROGRESS", "Jarayonda"),
                Map.entry("st_TESTING", "Testda"),
                Map.entry("st_DONE", "Bajarilgan"),
                Map.entry("st_CANCELLED", "Bekor qilingan")));
    }

    private BotMessages() {
    }

    public static String get(Language language, String key) {
        return M.getOrDefault(language, M.get(Language.EN)).getOrDefault(key, key);
    }

    public static String get(Language language, String key, Object... args) {
        return String.format(get(language, key), args);
    }

    public static String priority(Language language, TaskPriority priority) {
        return get(language, "prio_" + priority.name());
    }

    /** True if the given text equals this button's label in ANY language. */
    public static boolean isButton(String text, String key) {
        for (Language language : Language.values()) {
            if (get(language, key).equals(text)) {
                return true;
            }
        }
        return false;
    }
}

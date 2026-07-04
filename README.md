# HR System — xodimlar va task boshqaruvi

Web sayt (React) + Telegram bot orqali xodimlarning ish jarayonini boshqarish tizimi (multi-tenant, jamoalarga bo'lingan).

**Oqim:** yangi user ro'yxatdan o'tadi → jamoa yaratadi (avtomatik LEADER) → a'zolar qo'shadi → leader/manager task yuklaydi (xohlasa darhol biriktiradi) → a'zolar ochiq tasklarni oladi va bajaradi → oy oxirida statistika. Task yaratilganda/olinganda/bajarilganda jamoaning barcha LEADER va MANAGER'lariga Telegram xabari boradi.

**Test (review) oqimi:** member taskni yakunlaganda u **Test** ustuniga (`TESTING`) tushadi; leader/manager **Approve** qilsa DONE bo'ladi, **Return** qilsa qayta ishlashga qaytadi (bajaruvchiga bot orqali xabar boradi). Leader/manager o'zi complete qilsa to'g'ridan-to'g'ri DONE.

**Taklif havolasi:** leader/manager Members sahifasida "🔗 Invite link" orqali havola yaratadi (7 kun amal qiladi, bekor qilsa bo'ladi; MEMBER'dan boshqa rol bilan havolani faqat leader yaratadi). Havolani olgan odam `/join/<token>` sahifasida jamoaga o'zi qo'shiladi — akkaunt bo'lmasa avval ro'yxatdan o'tadi va avtomatik davom etadi.

**Multi-team (DigitalOcean uslubi):** bitta user istalgancha jamoa yaratishi va istalgancha jamoaga a'zo bo'lishi mumkin — har jamoada alohida rol (masalan, birida LEADER, boshqasida MEMBER). Rol va lavozim `team_memberships` jadvalida saqlanadi. Web'da headerdagi team switcher orqali, botda "🔀 Jamoa" tugmasi bilan almashtiriladi. API'da tanlangan jamoa **`X-Team-Id` headeri** bilan yuboriladi (bitta jamoa bo'lsa header shart emas). Mavjud user jamoaga `username` orqali qo'shiladi (Members sahifasida "Add existing").

## Tarkib

- `backend/` — Spring Boot 4 (Java 25), REST API + Telegram bot (bitta deploy)
- `frontend/` — React + Vite + TypeScript, 3 tilli (EN default / RU / UZ)

## Ishga tushirish

### Backend (port 8080)

```powershell
cd backend
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25"   # JAVA_HOME jdk-21 ga qaragan bo'lsa
.\mvnw.cmd spring-boot:run
```

Dev rejimda H2 fayl-bazasi ishlatiladi (`backend/data/hrdb`). Prod uchun:

```powershell
$env:DB_URL="jdbc:postgresql://host:5432/hrdb"; $env:DB_USERNAME="..."; $env:DB_PASSWORD="..."
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=prod"
```

Birinchi ishga tushishda loyiha egasi (ADMIN) yaratiladi: **admin / admin123** (env: `APP_ADMIN_USERNAME` / `APP_ADMIN_PASSWORD`) — darhol parolni o'zgartiring. Qolgan userlar o'zi ro'yxatdan o'tadi.

### Frontend (port 5173)

```powershell
cd frontend
npm install
npm run dev
```

> Node PATH'da bo'lmasa: `C:\Users\abdur\.tools\node` papkasida Node 22 bor
> (`$env:PATH = "C:\Users\abdur\.tools\node;$env:PATH"`).

Vite dev-server `/api` so'rovlarini `localhost:8080` ga proxy qiladi.

### Telegram bot

1. [@BotFather](https://t.me/BotFather) dan bot yarating, token oling.
2. Backend'ni token bilan ishga tushiring:

```powershell
$env:HR_BOT_TOKEN = "123456:ABC-..."; $env:HR_BOT_USERNAME = "my_hr_bot"
.\mvnw.cmd spring-boot:run
```

Token bo'lmasa bot o'chirilgan holda backend baribir ishlaydi.

**Bot oqimi:**
- `/start` → til tanlash (EN/RU/UZ) → HR bergan ulanish kodi kiritiladi (kod web'dagi Xodimlar jadvalida ko'rinadi)
- **Ishchi:** ochiq tasklar / mening tasklarim / bajarilganlar; task olish va yakunlash inline tugmalar bilan
- **HR:** yangi task yaratish (wizard), barcha tasklar, oylik statistika
- **Bildirishnomalar:** yangi task → barcha ulangan ishchilarga; task olindi/bajarildi → barcha HR'larga

## Rollar

| Rol | Huquqlar |
|---|---|
| ADMIN | Loyiha egasi (bitta, seed qilinadi, teamsiz). Barcha jamoalar va userlar ro'yxatini ko'radi (`/admin`) |
| LEADER | Jamoa yaratgan odam. To'liq boshqaruv: a'zo qo'shish, rol o'zgartirish (MANAGER tayinlash), task/teg, statistika |
| MANAGER | A'zolarni boshqaradi: MEMBER qo'shadi/tahrirlaydi, task yaratadi va **biriktiradi**, teg boshqaradi, statistika. Rol o'zgartira olmaydi |
| MEMBER | Ochiq tasklarni oladi, bajaradi, qaytaradi. Botda ham shu |

**Bildirishnomalar (bot):** task yaratildi/olindi/bajarildi → jamoa LEADER + MANAGER'lariga (amalni qilganning o'ziga bormaydi); task biriktirilganda → biriktirilgan a'zoga; ochiq (pool) task yaratilganda → barcha a'zolarga.

## API qisqacha

- `POST /api/auth/register`, `POST /api/auth/login` → `{token, employee}` (JWT, keyin `Authorization: Bearer ...`)
- `POST /api/teams` — jamoa yaratish (yaratuvchi LEADER bo'ladi)
- `GET/POST/PUT /api/employees` (LEADER/MANAGER), `POST /api/employees/{id}/reset-telegram`
- `GET/POST/PUT/DELETE /api/tags` — jamoa teglari
- `GET /api/tasks?status=&mine=`, `POST /api/tasks` (tagIds, assigneeId bilan), `POST /api/tasks/{id}/assign|take|complete|approve|reject|release|cancel`
- `POST/GET /api/invites`, `DELETE /api/invites/{id}`, `GET /api/invites/token/{t}`, `POST /api/invites/token/{t}/accept`
- `GET /api/stats/monthly?year=&month=`
- `GET /api/admin/teams`, `GET /api/admin/employees` (faqat ADMIN)

## Muhim sozlamalar (env)

| O'zgaruvchi | Tavsif |
|---|---|
| `APP_JWT_SECRET` | JWT imzo kaliti (prod'da majburiy almashtiring) |
| `HR_BOT_TOKEN` / `HR_BOT_USERNAME` | Telegram bot |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | PostgreSQL (prod profil) |

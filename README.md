# HR System — xodimlar va task boshqaruvi

Web sayt (React) + Telegram bot orqali xodimlarning ish jarayonini boshqarish tizimi (multi-tenant, jamoalarga bo'lingan).

**Oqim:** yangi user ro'yxatdan o'tadi → jamoa yaratadi (avtomatik LEADER) → a'zolar qo'shadi → leader/manager task yuklaydi (xohlasa darhol biriktiradi) → a'zolar ochiq tasklarni oladi va bajaradi → oy oxirida statistika. Task yaratilganda/olinganda/bajarilganda jamoaning barcha LEADER va MANAGER'lariga Telegram xabari boradi.

**Test (review) oqimi:** member taskni yakunlaganda u **Test** ustuniga (`TESTING`) tushadi; leader/manager **Approve** qilsa DONE bo'ladi, **Return** qilsa qayta ishlashga qaytadi (bajaruvchiga bot orqali xabar boradi). Leader/manager o'zi complete qilsa to'g'ridan-to'g'ri DONE.

**Taklif havolasi:** leader/manager Members sahifasida "🔗 Invite link" orqali havola yaratadi (7 kun amal qiladi, bekor qilsa bo'ladi; MEMBER'dan boshqa rol bilan havolani faqat leader yaratadi). Havolani olgan odam `/join/<token>` sahifasida jamoaga o'zi qo'shiladi — akkaunt bo'lmasa avval ro'yxatdan o'tadi va avtomatik davom etadi.

**Multi-team (DigitalOcean uslubi):** bitta user istalgancha jamoa yaratishi va istalgancha jamoaga a'zo bo'lishi mumkin — har jamoada alohida rol (masalan, birida LEADER, boshqasida MEMBER). Rol va lavozim `team_memberships` jadvalida saqlanadi. Web'da headerdagi team switcher orqali, botda "🔀 Jamoa" tugmasi bilan almashtiriladi. API'da tanlangan jamoa **`X-Team-Id` headeri** bilan yuboriladi (bitta jamoa bo'lsa header shart emas). Mavjud user jamoaga `username` orqali qo'shiladi (Members sahifasida "Add existing").

## Tarkib

- `backend/` — Spring Boot 4 (Java 25), REST API + Telegram bot (bitta deploy)
- `frontend/` — Next.js 16 (App Router + SSR) + TypeScript, 3 tilli (EN default / RU / UZ)

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

### Frontend (port 3000)

```powershell
cd frontend
npm install
npm run dev
```

> Node PATH'da bo'lmasa: `C:\Users\abdur\.tools\node` papkasida Node 22 bor
> (`$env:PATH = "C:\Users\abdur\.tools\node;$env:PATH"`).

Next.js dev-server `/api` so'rovlarini `localhost:8080` ga proxy qiladi (rewrites orqali).

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

## Docker Deploy

### Backend Deploy (Server)

```bash
# Backend'ni ishga tushirish
docker compose up -d

# Loglarni ko'rish
docker compose logs -f hr_system_backend

# To'xtatish
docker compose down
```

### Environment Variables (.env fayli)

```env
# Database
DB_URL=jdbc:postgresql://postgres:5432/hrdb
DB_USERNAME=hruser
DB_PASSWORD=your_password

# Security
APP_JWT_SECRET=your-very-long-secret-key-change-in-production
APP_ADMIN_USERNAME=admin
APP_ADMIN_PASSWORD=admin123

# Telegram Bot
HR_BOT_TOKEN=123456:ABC-your-bot-token
HR_BOT_USERNAME=your_hr_bot

# CORS (Vercel frontend URL)
CORS_ALLOWED_ORIGINS=https://your-app.vercel.app,http://localhost:3000
```

## Frontend Deploy (Vercel)

### 1. Vercel'ga Deploy qilish:

```bash
cd frontend

# Vercel CLI o'rnatish (birinchi marta)
npm i -g vercel

# Deploy
vercel
```

### 2. Environment Variables (Vercel Dashboard):

Vercel Dashboard → Settings → Environment Variables:

| Variable | Value |
|---|---|
| `NEXT_PUBLIC_BACKEND_URL` | `https://your-backend-domain.com` |

### 3. Git bilan avtomatik deploy:

1. Vercel'da GitHub repository'ni ulang
2. Frontend papkasini **Root Directory** sifatida belgilang
3. Har safar push qilganingizda avtomatik deploy bo'ladi

## CI/CD (GitHub Actions)

Loyihada 1 ta deploy workflow mavjud:

### Backend Deploy
`.github/workflows/deploy-backend.yml` — backend o'zgarganda avtomatik deploy

### GitHub Secrets (repository settings'da sozlang):

| Secret | Tavsif |
|---|---|
| `SSH_PRIVATE_KEY` | Server'ga SSH access uchun private key |
| `SERVER_USER` | SSH user (masalan: `root` yoki `ubuntu`) |
| `SERVER_IP` | Server IP manzili |
| `WORKING_DIR` | Deploy papkasi (masalan: `/opt/hr-system`) |

### Deploy oqimi:

1. **Backend:** `master`/`main` branchga push → GitHub Actions → Server deploy
2. **Frontend:** `master`/`main` branchga push → Vercel avtomatik deploy

### Manual Backend Deploy:

GitHub repository → Actions → "Backend Deploy" → "Run workflow"

### Server tayyorlash:

```bash
# Docker va Docker Compose o'rnating
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# Deploy papkasini yarating
sudo mkdir -p /opt/hr-system
sudo chown $USER:$USER /opt/hr-system

# .env faylini yarating
cd /opt/hr-system
nano .env  # environment variables'ni kiriting (yuqoridagi .env example'ga qarang)

# CORS_ALLOWED_ORIGINS'ga Vercel URL'ingizni qo'shing
# Masalan: CORS_ALLOWED_ORIGINS=https://hr-system.vercel.app,http://localhost:3000
```

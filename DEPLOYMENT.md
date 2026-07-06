# HR System Deployment Guide

## Architecture

- **Backend:** Self-hosted server — Docker Compose (`postgres` + backend) + GitHub Actions CI/CD
- **Database:** PostgreSQL 16 (runs in Compose, `hr-postgres-data` volume)
- **File storage:** S3-compatible bucket (attachments & comment images)
- **Frontend:** Vercel (free tier, auto-deploy from Git)

## 🚀 Quick Start

### 1. Backend Deploy (Server)

#### Prerequisites:
- Ubuntu/Debian server with Docker installed
- SSH access to server
- Domain/IP for backend API

#### Steps:

1. **Server'ni tayyorlang:**

```bash
# Docker o'rnatish
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# Deploy papkasi
sudo mkdir -p /opt/hr-system
sudo chown $USER:$USER /opt/hr-system
cd /opt/hr-system
```

2. **.env faylini yarating** (`docker-compose.yml` yonida). `SPRING_PROFILES_ACTIVE=prod` va `DB_URL` compose tomonidan avtomatik o'rnatiladi — bu yerda faqat quyidagilar:

```bash
cat > .env << 'EOF'
# PostgreSQL (backend + postgres service uchun umumiy).
# DB_HOST=db va DB_PORT=5432 ni compose avtomatik beradi — bu yerda kerak emas.
DB_NAME=hrdb
DB_USER=hruser
DB_PASSWORD=CHANGE_THIS_PASSWORD

# Security
APP_JWT_SECRET=CHANGE_THIS_TO_VERY_LONG_RANDOM_STRING
APP_ADMIN_USERNAME=admin
APP_ADMIN_PASSWORD=CHANGE_THIS_PASSWORD

# S3 file storage (attachments) — MAJBURIY, hammasi to'ldirilishi shart (default yo'q).
# AWS uchun S3_ENDPOINT ni bo'sh qoldiring; DigitalOcean Spaces / MinIO uchun URL bering.
S3_ENABLED=true
S3_REGION=us-east-1
S3_BUCKET_NAME=your-bucket-name
S3_ACCESS_KEY=your-access-key
S3_SECRET_KEY=your-secret-key
S3_ENDPOINT=

# CORS - Vercel frontend URL'ini qo'shing
CORS_ALLOWED_ORIGINS=https://your-app.vercel.app,http://localhost:3000

# Telegram Bot (optional)
HR_BOT_TOKEN=
HR_BOT_USERNAME=
EOF
```

> `docker-compose.yml` PostgreSQL (`postgres:16`) service'ini o'z ichiga oladi — alohida DB o'rnatish shart emas. Ma'lumot `hr-postgres-data` volume'ida saqlanadi. Backend **9090** portда ishlaydi.
>
> ⚠️ **Schema:** `application.yml` da `ddl-auto: validate` — Hibernate jadvallarni **yaratmaydi**, faqat tekshiradi. Bo'sh (yangi) PostgreSQL bilan birinchi ishga tushirishda jadvallar bo'lmagani uchun app ishga tushmaydi. Birinchi marta schema yaratish uchun vaqtincha `ddl-auto: update` qiling (yoki Flyway/Liquibase qo'shing), keyin `validate` ga qaytaring.

3. **GitHub Secrets sozlang:**

GitHub Repository → Settings → Secrets and variables → Actions:

- `SSH_PRIVATE_KEY`: Server SSH private key
- `SERVER_USER`: SSH username (ubuntu/root)
- `SERVER_IP`: Server IP address
- `WORKING_DIR`: `/opt/hr-system`

> **`.env` haqida:** Server bir marta qo'lda sozlanadi — yuqoridagi 2-qadamda `.env` faylni serverda o'zingiz yaratasiz. CI/CD `.env`ga tegmaydi, uni GitHub secret'dan yozmaydi; har deploy'da faqat serverda `.env` borligini tekshiradi. Agar `.env` yo'q bo'lsa, deploy aniq xato bilan to'xtaydi (`.env is missing`). Shunday qilib maxfiy qiymatlar faqat serverda turadi, GitHub'da emas.

4. **Deploy qilish:**

```bash
# Manual deploy
git push origin master

# Yoki GitHub Actions'da "Run workflow"
```

Backend: `http://your-server-ip:9090`

---

### 2. Frontend Deploy (Vercel)

#### Steps:

1. **Vercel'ga kiring:** https://vercel.com (GitHub bilan)

2. **Import Project:**
   - "New Project" → GitHub repository tanlang
   - **Root Directory:** `frontend` ga o'zgartiring
   - **Build Settings:** (auto-detect qiladi)

3. **Environment Variables:**

Vercel Dashboard → Settings → Environment Variables → Add:

| Name | Value |
|------|-------|
| `NEXT_PUBLIC_BACKEND_URL` | `http://your-server-ip:9090` yoki `https://api.yourdomain.com` |

4. **Deploy:**

Vercel avtomatik deploy qiladi. URL: `https://your-project.vercel.app`

5. **Backend .env'ni yangilang:**

Server'da `.env` fayliga Vercel URL'ini qo'shing:

```bash
CORS_ALLOWED_ORIGINS=https://your-project.vercel.app,http://localhost:3000
```

Backend'ni qayta ishga tushiring:

```bash
docker compose down
docker compose up -d
```

---

## 🔄 Deployment Workflow

### Backend:
- `backend/` papkasiga o'zgarish → push to `master` → GitHub Actions → Server deploy

### Frontend:
- `frontend/` papkasiga o'zgarish → push to `master` → Vercel auto-deploy

---

## 🧪 Testing Deployment

```bash
# Backend health check
curl http://your-server-ip:9090/actuator/health

# Frontend
curl https://your-project.vercel.app

# API through frontend
curl https://your-project.vercel.app/api/auth/login
```

---

## 📝 Production Checklist

### Backend:
- [ ] Change `APP_JWT_SECRET` to strong random string (`openssl rand -base64 64`)
- [ ] Change `APP_ADMIN_PASSWORD`
- [ ] Change `DB_PASSWORD`
- [ ] Create the S3 bucket and set `S3_*` values (required for file uploads)
- [ ] Add Vercel URL to `CORS_ALLOWED_ORIGINS`
- [ ] Setup Telegram bot (optional)
- [ ] Configure domain/SSL (optional, use nginx + certbot)

### Frontend:
- [ ] Set `NEXT_PUBLIC_BACKEND_URL` in Vercel
- [ ] Configure custom domain (optional)

---

## 🔧 Troubleshooting

### CORS Error:
Backend `.env` faylida `CORS_ALLOWED_ORIGINS`'ga frontend URL'ini qo'shing.

### Backend not accessible:
- Server firewall'da port 8080 ochilganini tekshiring
- Docker container ishlab turganini tekshiring: `docker ps`

### Vercel build fails:
- Environment variables to'g'ri sozlanganini tekshiring
- Build logs'ni ko'ring

---

## 💰 Estimated Costs

- **Frontend (Vercel):** FREE (hobby plan)
- **Backend Server:** $5-10/month (VPS providers: DigitalOcean, Hetzner, Vultr)
- **Domain (optional):** $10-15/year
- **SSL (optional):** FREE (Let's Encrypt)

**Total:** ~$5-10/month

# Environment Variables Setup Guide

## 📋 Overview

HR System uses environment variables for configuration. This guide explains how to set them up for different environments.

---

## 🛠️ Local Development

### Backend

1. **Copy the example file:**
   ```bash
   cd backend
   cp .env.example .env
   ```

2. **Edit `.env` (minimal for dev):**
   ```env
   # H2 database (default, no setup needed)
   DB_URL=jdbc:h2:file:./data/hrdb
   DB_USERNAME=sa
   DB_PASSWORD=
   
   # JWT secret (dev)
   APP_JWT_SECRET=dev-secret-key-change-in-production
   
   # Admin credentials
   APP_ADMIN_USERNAME=admin
   APP_ADMIN_PASSWORD=admin123
   
   # CORS for local frontend
   CORS_ALLOWED_ORIGINS=http://localhost:3000
   
   # Telegram bot (optional, leave empty)
   HR_BOT_TOKEN=
   HR_BOT_USERNAME=
   ```

3. **Run backend:**
   ```powershell
   cd backend
   $env:JAVA_HOME = "C:\Program Files\Java\jdk-25"
   .\mvnw.cmd spring-boot:run
   ```

### Frontend

1. **Copy the example file:**
   ```bash
   cd frontend
   cp .env.example .env.local
   ```

2. **Edit `.env.local`:**
   ```env
   NEXT_PUBLIC_BACKEND_URL=http://localhost:8080
   ```

3. **Run frontend:**
   ```bash
   cd frontend
   npm install
   npm run dev
   ```

---

## 🚀 Production Deployment

### Backend (Server with Docker)

1. **Create `.env` on server:**
   ```bash
   cd /opt/hr-system
   nano .env
   ```

2. **Production configuration:**
   ```env
   # PostgreSQL Database
   DB_URL=jdbc:postgresql://localhost:5432/hrdb
   DB_USERNAME=hruser
   DB_PASSWORD=your_strong_password_here
   
   # Security (IMPORTANT!)
   APP_JWT_SECRET=generate_with_openssl_rand_base64_64
   APP_ADMIN_USERNAME=admin
   APP_ADMIN_PASSWORD=change_immediately_after_first_login
   
   # CORS - Add your Vercel URL
   CORS_ALLOWED_ORIGINS=https://your-app.vercel.app,http://localhost:3000
   
   # Telegram Bot
   HR_BOT_TOKEN=1234567890:YourBotTokenFromBotFather
   HR_BOT_USERNAME=your_hr_bot
   ```

3. **Generate secure secrets:**
   ```bash
   # JWT Secret
   openssl rand -base64 64
   
   # Admin Password
   openssl rand -base64 32
   ```

4. **Deploy:**
   ```bash
   docker compose up -d
   ```

### Frontend (Vercel)

1. **Vercel Dashboard:**
   - Go to: https://vercel.com
   - Select your project
   - Settings → Environment Variables

2. **Add variable:**
   | Name | Value |
   |------|-------|
   | `NEXT_PUBLIC_BACKEND_URL` | `http://your-server-ip:8080` or `https://api.yourdomain.com` |

3. **Redeploy:**
   - Vercel will automatically redeploy with new environment variables

---

## 🔐 Security Best Practices

### ✅ DO:
- Use strong, random secrets (min 32 characters)
- Change default passwords immediately
- Use PostgreSQL in production
- Enable HTTPS with SSL certificate
- Regularly rotate secrets
- Keep `.env` files in `.gitignore`
- Use different secrets for dev/prod

### ❌ DON'T:
- Commit `.env` files to Git
- Use default passwords in production
- Share secrets in plain text
- Use same secrets across environments
- Leave H2 database in production (use PostgreSQL)

---

## 📝 Environment Variables Reference

### Backend

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `DB_URL` | No | H2 file DB | Database connection URL |
| `DB_USERNAME` | No | `sa` | Database username |
| `DB_PASSWORD` | No | `` | Database password |
| `APP_JWT_SECRET` | **YES** | - | JWT signing key (min 32 chars) |
| `APP_ADMIN_USERNAME` | No | `admin` | Initial admin username |
| `APP_ADMIN_PASSWORD` | **YES** | `admin123` | Initial admin password |
| `CORS_ALLOWED_ORIGINS` | **YES** | `http://localhost:3000` | Allowed CORS origins (comma-separated) |
| `HR_BOT_TOKEN` | No | - | Telegram bot token |
| `HR_BOT_USERNAME` | No | - | Telegram bot username |
| `SPRING_PROFILES_ACTIVE` | No | `default` | Spring profile (`prod` for PostgreSQL) |

### Frontend

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `NEXT_PUBLIC_BACKEND_URL` | **YES** | `http://localhost:8080` | Backend API URL |

---

## 🧪 Testing Configuration

### Verify Backend:
```bash
# Check environment is loaded
curl http://localhost:8080/actuator/health

# Test login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

### Verify Frontend:
```bash
# Check backend connection
curl http://localhost:3000/api/auth/login

# Should proxy to backend
```

---

## 🆘 Troubleshooting

### CORS Error:
**Problem:** Frontend can't connect to backend
**Solution:** Add Vercel URL to `CORS_ALLOWED_ORIGINS`:
```env
CORS_ALLOWED_ORIGINS=https://your-app.vercel.app,http://localhost:3000
```

### JWT Token Invalid:
**Problem:** Login fails with "Invalid token"
**Solution:** Make sure `APP_JWT_SECRET` is at least 32 characters

### Database Connection Error:
**Problem:** Backend can't connect to PostgreSQL
**Solution:** Check `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` are correct

### Telegram Bot Not Working:
**Problem:** Bot doesn't respond
**Solution:** Verify `HR_BOT_TOKEN` and `HR_BOT_USERNAME` are correct from @BotFather

---

## 📚 Related Files

- `backend/.env.example` - Backend environment template
- `frontend/.env.example` - Frontend environment template
- `.env.example` - Docker Compose environment template
- `.gitignore` - Ignores `.env` files from Git
- `DEPLOYMENT.md` - Full deployment guide

# Optional self-host image. Primary frontend deploy is Vercel (see DEPLOYMENT.md).
# Requires `output: 'standalone'` in next.config.ts.

# ---------- Build stage ----------
FROM node:22-alpine AS builder

WORKDIR /app

# Install ALL deps (devDependencies are needed to build: typescript, tailwind, etc.)
COPY package*.json ./
RUN npm ci

COPY . .

# Bake the backend URL into the build (the /api rewrite is resolved at build time).
ARG NEXT_PUBLIC_BACKEND_URL=http://localhost:8080
ENV NEXT_PUBLIC_BACKEND_URL=$NEXT_PUBLIC_BACKEND_URL

RUN npm run build

# ---------- Production stage ----------
FROM node:22-alpine AS runner

WORKDIR /app

ENV NODE_ENV=production
ENV PORT=3000
ENV HOSTNAME="0.0.0.0"

# Standalone output: server.js + minimal node_modules, plus static assets and public/.
COPY --from=builder /app/public ./public
COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static

EXPOSE 3000

CMD ["node", "server.js"]

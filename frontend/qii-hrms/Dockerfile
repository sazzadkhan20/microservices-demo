# Qii HRMS frontend — separately deployable Next.js image (guide §9: non-root, least privilege)
FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

FROM node:22-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
ENV NEXT_TELEMETRY_DISABLED=1
# Git does not keep empty folders: make sure public/ exists in a clean checkout (CI), and never ship
# the placeholder that keeps it tracked.
RUN mkdir -p public && rm -f public/.gitkeep
RUN npm run build

FROM node:22-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0
# Runtime files are owned by root and only readable by the unprivileged "node" user.
COPY --from=build --chown=root:root /app/public ./public
COPY --from=build --chown=root:root /app/.next/standalone ./
COPY --from=build --chown=root:root /app/.next/static ./.next/static
# Next writes its image/fetch cache at runtime — the only writable path.
RUN mkdir -p .next/cache && chown -R node:node .next/cache
USER node
EXPOSE 3000
HEALTHCHECK --interval=15s --timeout=3s --start-period=20s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:3000/login || exit 1
CMD ["node", "server.js"]

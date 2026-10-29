# Qii HRMS frontend

Next.js App Router + TypeScript + Zod + TanStack Query.

## Auth

1. Account picker loads `GET /api/auth/accounts` (BFF → Gateway `/hrms/api/auth/accounts`).
2. Login `POST /api/auth/login` issues Registry JWT and stores it in an **httpOnly** cookie.
3. Browser calls only same-origin `/api/*`; BFF attaches `Authorization` to the Gateway.

## Run locally

```bash
# infra + gateway must be up (see repo scripts/up-local.sh)
cp .env.example .env.local
npm install
npm run dev
```

Open http://127.0.0.1:3000 → pick **Asman Rahman**.

## Docker

Built via Compose profile `apps` as service `qii-frontend`, or:

```bash
docker build -t qii-hrms-frontend .
docker run --rm -p 3000:3000 -e GATEWAY_URL=http://host.docker.internal:8090 -e TENANT_ID=qii_creative qii-hrms-frontend
```

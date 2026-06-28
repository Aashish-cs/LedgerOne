# Deployment Guide

This repo is ready for a Render backend and Vercel frontend split.

## 1. Deploy Backend On Render

Use the root `render.yaml` Blueprint.

1. Open Render and create a new Blueprint.
2. Connect `Aashish-cs/LedgerOne`.
3. Render will create:
   - `ledgerone-api`
   - `ledgerone-db`
4. When prompted for `ALLOWED_ORIGINS`, enter your Vercel URL after the frontend is created.

Until Vercel gives you the final URL, you can temporarily use:

```text
http://localhost:5173
```

After backend deploy, copy the Render API base URL:

```text
https://YOUR-RENDER-SERVICE.onrender.com/api
```

Useful backend URLs:

```text
https://YOUR-RENDER-SERVICE.onrender.com/actuator/health
https://YOUR-RENDER-SERVICE.onrender.com/api/system/status
https://YOUR-RENDER-SERVICE.onrender.com/swagger-ui.html
```

## 2. Deploy Frontend On Vercel

Create a Vercel project from the same GitHub repo.

Project settings:

```text
Root Directory: frontend
Framework Preset: Vite
Install Command: npm ci
Build Command: npm run build
Output Directory: dist
```

Set this Vercel environment variable:

```text
VITE_API_BASE_URL=https://YOUR-RENDER-SERVICE.onrender.com/api
```

## 3. Update CORS

After Vercel deploys, copy the Vercel URL and set Render `ALLOWED_ORIGINS`:

```text
https://YOUR-VERCEL-PROJECT.vercel.app
```

For multiple allowed origins, use comma-separated values:

```text
https://YOUR-VERCEL-PROJECT.vercel.app,http://localhost:5173
```

Redeploy the Render backend after changing this value.

## 4. Final Smoke Test

1. Open the Vercel URL.
2. Confirm the login page shows `Live API`.
3. Login with:

```text
user@ledgerone.com / User123!
admin@ledgerone.com / Admin123!
```

4. Place a small market BUY order.
5. Open Admin Portal and verify orders, risk alerts, and audit logs.

## Notes

- Render free services can sleep after inactivity, so the first API request may be slow.
- The app uses a self-hosted API and simulated market data. No paid market-data API is required.
- The backend accepts Render's managed Postgres `DATABASE_URL` and converts it to Spring's JDBC format at startup.

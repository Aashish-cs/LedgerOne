# Changelog

## 2026-06-30

- Added `AUDIT.md` with a route, endpoint, button/action, auth, RBAC, trading, and stock-price audit of the deployed app.
- Integrated live equity quotes through the backend using Yahoo Finance's chart quote endpoint with a 60-second server-side cache.
- Updated order placement so market and eligible limit orders refresh the backend stock price at submit/fill time instead of trusting a frontend-cached value.
- Updated the Trading UI to wait for live portfolios/stocks, show the selected stock price, quote refresh time, and estimated order value before submit.
- Removed demo placeholder data from live Portfolio, Trading, and Watchlist action surfaces so users cannot click seeded demo IDs against the production API.
- Improved auth/register feedback in the previous fix: create-account loading state, success handoff, and backend duplicate-email errors now show clearly.
- Increased backend tests from 7 to 8 by adding quote parsing/cache coverage.

Follow-up still recommended:
- Add reset-password request/confirm flow with token expiry and email delivery.
- Add frontend refresh-token retry handling for expired access tokens.
- Add a dedicated transaction-history page using the existing transactions endpoint.
- Consider access-token blocklisting or token-version checks for immediate server-side logout invalidation.

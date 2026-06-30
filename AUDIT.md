# LedgerOne Functional Audit

Audit date: 2026-06-30

Live URLs tested:
- Frontend: https://ledger-one-mocha.vercel.app
- Backend: https://ledgerone-api-litx.onrender.com

Test method:
- Browser UI audit with fresh test users (`audit-*`, `audit2-*`, `portfolio-*`) against the deployed Vercel frontend and Render API.
- Backend route and service review from the local working copy.
- API response capture from browser flows.

## Executive Summary

LedgerOne has a solid deployed foundation: registration, login, logout, protected routes, portfolio creation, watchlist add/remove, admin RBAC, admin search/freeze/activate, and duplicate-email handling are functional on the live deployment.

The highest-severity product issue found is in the trading page. Live users can hit a stale demo portfolio ID before the real portfolio query has settled, producing `404` responses for `GET /api/portfolios/33333333-3333-3333-3333-333333333333/orders` and `POST /api/orders`. This makes the order button feel broken.

The highest-severity realism gap is market data. Stock prices are still seeded/simulated and randomly ticked by the backend. Buy/sell execution uses the backend stock record, which is good, but that record is not refreshed from a real market quote API. The trading UI also does not show the current stock price or estimated order value before submit.

## Frontend Routes and Actions

| Page / Route | Buttons / Actions | Endpoint(s) | Expected Behavior | Actual Behavior |
|---|---|---|---|---|
| `/login` | Sign in tab, Register tab, Sign in submit, Create account submit | `POST /api/auth/login`, `POST /api/auth/register`, `GET /api/system/status` | Users can switch auth modes, register, log in, see errors, and enter dashboard. | Pass. Fresh registration returned `201`, dashboard opened, duplicate email returned `409` and visible message. Wrong password returned `401` and visible message. |
| Global shell | Overview, Portfolios, Trading, Watchlist nav links | route navigation plus page queries | Links route to protected views. | Pass. Route links work after authentication. |
| Global shell | Notifications button, close button | `GET /api/notifications` | Activity popover opens and closes. | Pass. Popover opens/closes. There is no mark-read action. |
| Global shell | Sign out | `POST /api/auth/logout` | Clears client session and protected routes redirect to login. | Pass for client redirect and refresh-token revoke call. Access JWT is not server-side blocklisted before expiry. |
| `/` | Dashboard display | `GET /api/dashboard` | Shows portfolio metrics, allocation, recent transactions, risk alerts. | Pass for loaded data. Uses demo placeholder while loading, which can momentarily show seeded demo values. |
| `/portfolios` | Create | `POST /api/portfolios` | Creates empty portfolio. | Pass. API returned `201`; UI message is `Portfolio "<name>" is ready`. |
| `/portfolios` | Rename | `PUT /api/portfolios/{portfolioId}` | Renames owned portfolio. | Pass by code path and existing mutation behavior; needs a post-fix smoke run after trading changes. |
| `/portfolios` | Delete | `DELETE /api/portfolios/{portfolioId}` | Deletes empty owned portfolio and blocks portfolios with holdings. | Pass for intended code path. Delete pre-check blocks portfolios with holdings in UI. |
| `/trading` | Submit order | `POST /api/orders` | Places authenticated buy/sell order against user's selected portfolio. | Fail. Live audit captured `POST /api/orders -> 404` because form submitted seeded demo portfolio ID before live portfolio state settled. |
| `/trading` | Cancel pending order | `POST /api/orders/{orderId}/cancel` | Cancels only pending orders. | Backend supports it; UI disables filled orders. Full UI cancel flow blocked by trading form bug above. |
| `/watchlist` | Add | `POST /api/watchlist` | Adds selected symbol to user's watchlist. | Pass. Live audit added GOOGL. |
| `/watchlist` | Remove | `DELETE /api/watchlist/{itemId}` | Removes selected watchlist item. | Pass. Live audit removed GOOGL. |
| `/admin` | Search users | `GET /api/admin/users?search=...` | Admin can search users. | Pass. Admin test found fresh user. |
| `/admin` | Freeze / Activate | `PATCH /api/admin/users/{id}/freeze`, `PATCH /api/admin/users/{id}/unfreeze` | Admin can freeze and activate users. | Pass. Live audit froze and reactivated fresh user. |
| `/admin` | Orders, audit logs, risk alerts | `GET /api/admin/orders`, `GET /api/admin/audit-logs`, `GET /api/admin/risk-alerts` | Admin-only data grids load. | Pass. Live audit returned `200`. |

## Backend Endpoint Map

| Endpoint | Purpose | Auth / RBAC | Audit Result |
|---|---|---|---|
| `POST /api/auth/register` | Register user and default portfolio | Public | Pass. Creates user, bcrypt-hashed password via `PasswordEncoder`, default `Core Growth Portfolio`, refresh token. |
| `POST /api/auth/login` | Authenticate user | Public | Pass. Valid credentials return JWT/refresh token; invalid credentials return `401`. No lockout/rate limit found. |
| `POST /api/auth/refresh` | Rotate refresh token | Public with refresh token | Implemented in backend, not used by frontend. |
| `POST /api/auth/logout` | Revoke refresh token | Auth UI calls with stored refresh token | Pass for refresh-token revoke. Existing access token remains valid until expiry. |
| `GET /api/system/status` | API health/capabilities | Public | Pass. |
| `GET /api/dashboard` | Portfolio overview | Authenticated | Pass. |
| `GET /api/portfolios` | List portfolios | Authenticated | Pass. |
| `POST /api/portfolios` | Create portfolio | Authenticated | Pass. |
| `PUT /api/portfolios/{id}` | Rename portfolio | Authenticated owner | Implemented; UI mutation present. |
| `DELETE /api/portfolios/{id}` | Delete portfolio | Authenticated owner | Implemented; UI pre-check for holdings. |
| `GET /api/market/stocks` | List stocks | Authenticated | Pass as endpoint, but prices are simulated/stale. |
| `GET /api/market/stocks/{symbol}/history` | Stock price history | Authenticated | Implemented, but history is seeded/random-ticked, not market-sourced. |
| `POST /api/orders` | Place order | Authenticated | Backend validates funds/shares, but UI currently submits stale demo portfolio ID in live sessions. Execution price comes from simulated stock record. |
| `POST /api/orders/{id}/cancel` | Cancel pending order | Authenticated owner | Implemented. |
| `GET /api/portfolios/{id}/orders` | Portfolio orders | Authenticated owner | Pass for real portfolio IDs; audit captured `404` when UI queried seeded demo portfolio ID. |
| `GET /api/portfolios/{id}/transactions` | Portfolio transactions | Authenticated owner | Implemented; no frontend page exposes it directly beyond dashboard recent transactions. |
| `GET /api/watchlist` | List watchlist | Authenticated | Pass. |
| `POST /api/watchlist` | Add watchlist item | Authenticated | Pass. |
| `DELETE /api/watchlist/{id}` | Remove watchlist item | Authenticated owner | Pass. |
| `GET /api/risk/portfolios/{id}` | Risk summary | Authenticated owner | Implemented; frontend primarily consumes risk through dashboard/admin. |
| `GET /api/notifications` | Recent notifications | Authenticated | Pass. |
| `GET /api/admin/users` | Admin user list | `ROLE_ADMIN` | Pass. Normal user API call returned `403`. |
| `PATCH /api/admin/users/{id}/freeze` | Freeze user | `ROLE_ADMIN` | Pass. |
| `PATCH /api/admin/users/{id}/unfreeze` | Activate user | `ROLE_ADMIN` | Pass. |
| `GET /api/admin/orders` | Admin order list | `ROLE_ADMIN` | Pass. |
| `GET /api/admin/audit-logs` | Admin audit logs | `ROLE_ADMIN` | Pass. |
| `GET /api/admin/risk-alerts` | Admin risk alerts | `ROLE_ADMIN` | Pass. |

## Entity / Workflow Coverage

| Entity / Workflow | UI Coverage | Backend Coverage | Result |
|---|---|---|---|
| User | Register/login/logout; admin freeze/activate | `users`, `user_roles`, refresh tokens, audit logs | Mostly pass. Reset password and lockout are missing. |
| Role / RBAC | Admin link hidden for normal users; admin page visible to admin | `@PreAuthorize("hasRole('ADMIN')")` | Pass. Normal user admin API call returned `403`. |
| Portfolio | Create/rename/delete UI | CRUD endpoints and ownership checks | Mostly pass. Needs post-fix smoke run after placeholder cleanup. |
| Stock / Security | Stock select and watchlist cards | Seeded stock rows and simulated ticks | Fail for realism. No live market API source. |
| Order | New order form, cancel button | Atomic `@Transactional` placement/fill/cancel | UI fail due stale portfolio ID. Backend validation exists. |
| Holding / Position | Portfolio holdings table | Updated during filled buy/sell | Backend path exists; UI verification blocked by trading submit bug. |
| Ledger transaction | Dashboard recent transactions | Saved on fill | Backend path exists; frontend has no dedicated transaction history page. |
| Watchlist | Add/remove | CRUD endpoints | Pass. |
| Notification | Popover list | Recent notifications endpoint | Read-only pass. No mark-read action. |
| Risk alert | Dashboard/admin display | Risk evaluate/persist | Pass for display. |
| Price history | Dashboard chart/history endpoint | Seeded/random generated data | Fail for real market requirement. |

## Auth Lifecycle Findings

| Area | Result |
|---|---|
| Register | Pass. Fresh UI registration creates account and default portfolio. Duplicate email surfaces `An account already exists for this email`. Password hashing uses Spring Security `PasswordEncoder`; seeded hashes are bcrypt-formatted. Live DB hash inspection was not available from the UI. |
| Login | Pass. Valid credentials work; wrong password shows `Invalid credentials`. |
| Repeated failed login | Gap. No lockout/rate-limit behavior found. Failed attempts are audited, but account remains available. |
| Reset password | Missing. No UI route, no controller endpoints, no token generation, and no email delivery path exist. |
| Logout | Partial pass. Client clears stored tokens and backend revokes refresh token. Access JWT is not server-side revoked until expiry. |
| Expired JWT / refresh | Gap. Backend refresh endpoint exists, but frontend has no automatic refresh or 401 recovery interceptor. |
| Protected route after logout | Pass. `/trading` redirects to `/login` after logout. |

## Stock Price Handling Findings

Current hardcoded/simulated sources:
- `V1__init_ledgerone_schema.sql` seeds fixed stock prices and historical prices.
- `frontend/src/data/demo.ts` contains fixed demo stock/holding/order prices.
- `MarketDataService.simulateTick()` randomly mutates `Stock.lastPrice` and writes `PriceHistory`.
- `TradingService` executes market orders from `order.getStock().getLastPrice()`, so the transaction is backend-priced, but that backend price is simulated.
- `DashboardService`, `PortfolioService`, and `RiskCalculator` calculate values from `Stock.lastPrice`, which is simulated.
- `TradingPage` only shows symbols, not the current stock price or estimated order value before buying/selling.

## Severity Findings

| Severity | Finding | Evidence | Proposed Fix |
|---|---|---|---|
| Critical | Trading form can submit seeded demo portfolio ID in live sessions. | Live audit captured `GET /api/portfolios/33333333-3333-3333-3333-333333333333/orders -> 404` and `POST /api/orders -> 404`. | Do not use demo portfolio placeholder for live sessions; disable order form until real portfolios load; reset form when selected live portfolio changes. |
| High | Stock prices are simulated/random, not live. | `MarketDataService.simulateTick()` uses `Random`; DB seed contains fixed prices. | Add backend quote service with short cache and update stock prices from a real quote endpoint before display and execution. |
| High | Trading UI does not show stock price at buy/sell time. | Symbol dropdown lists symbols only; no price preview/notional estimate. | Add selected-symbol price panel, refresh timestamp, and estimated order value on trading form. |
| Medium | Frontend uses demo placeholder data in live sessions. | Dashboard/trading/portfolios/watchlist use `placeholderData: demo...`; trading caused real 404. | Restrict demo placeholders to demo sessions; show loading/empty states for live API sessions. |
| Medium | Reset password is absent. | No route, no controller, no DTO/service/token/email flow. | Add request/reset endpoints, token table, email provider or demo-safe token delivery, and frontend screens. |
| Medium | Access token is not revoked on logout. | Logout revokes refresh token only; JWT remains valid until expiry. | Short access TTL is present; consider token version/blocklist for immediate invalidation. |
| Medium | Frontend does not refresh expired access tokens. | `api/client.ts` has request auth header but no 401 refresh retry. | Add response interceptor using refresh token, then logout on refresh failure. |
| Low | No mark-read notification action. | Notifications menu is read-only. | Add mark-read endpoint/button or remove unread count if static. |
| Low | No dedicated transaction-history page. | Backend has transaction endpoint; UI only shows dashboard recent transactions. | Add transactions tab/table for full order/trade auditability. |

## Recommended Fix Order

1. Fix live trading form state and loading/empty states.
2. Add live quote service with cache and no fake/random fallback for live execution.
3. Show selected stock price and estimated order value in the buy/sell form.
4. Add tests for price-backed market execution and stale portfolio prevention.
5. Add reset password and token refresh flows as a follow-up security milestone.

import {
  Activity,
  AlertTriangle,
  ArrowDownLeft,
  ArrowUpRight,
  Bell,
  Ban,
  BriefcaseBusiness,
  Building2,
  CheckCircle2,
  CircleDollarSign,
  ClipboardList,
  Eye,
  FileSearch,
  Gauge,
  LayoutDashboard,
  ListFilter,
  Loader2,
  Lock,
  LogOut,
  PieChart as PieChartIcon,
  Plus,
  RotateCcw,
  Search,
  ShieldCheck,
  TrendingUp,
  WalletCards,
  X,
} from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import {
  BrowserRouter,
  Navigate,
  NavLink,
  Outlet,
  Route,
  Routes,
  useNavigate,
} from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Area,
  AreaChart,
  CartesianGrid,
  Cell,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { api, getApiErrorMessage } from './api/client'
import { AuthProvider } from './auth/AuthContext'
import { useAuth } from './auth/auth-store'
import {
  demoAdminUsers,
  demoAuditLogs,
  demoDashboard,
  demoNotifications,
  demoPaperAccount,
  demoOrders,
  demoPortfolio,
  demoStocks,
  demoWatchlist,
  pageOfOrders,
} from './data/demo'
import type {
  AdminUser,
  AppNotification,
  Dashboard,
  Holding,
  Order,
  OrderRequest,
  OrderSide,
  OrderStatus,
  OrderType,
  PageResponse,
  PerformancePoint,
  Portfolio,
  RiskAlert,
  Stock,
  WatchlistItem,
} from './types'

const allocationColors = ['#47c7a1', '#e7b75f', '#6fb7d6', '#c17664', '#8abf72', '#b9a1e6']

function mergeStocks(...groups: Stock[][]) {
  const bySymbol = new Map<string, Stock>()
  groups.flat().forEach((stock) => bySymbol.set(stock.symbol, stock))
  return Array.from(bySymbol.values()).sort((a, b) => a.symbol.localeCompare(b.symbol))
}

function currency(value: number | undefined) {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 2,
  }).format(value ?? 0)
}

function compactNumber(value: number | undefined) {
  return new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(value ?? 0)
}

function formatDate(value: string | undefined) {
  if (!value) return '-'
  return new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' }).format(
    new Date(value),
  )
}

function formatDateTime(value: string | undefined) {
  if (!value) return '-'
  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
    second: '2-digit',
  }).format(new Date(value))
}

function formatChartTick(value: string | undefined) {
  if (!value) return ''
  return new Intl.DateTimeFormat('en-US', { hour: 'numeric', minute: '2-digit', second: '2-digit' }).format(new Date(value))
}

function classNames(...values: Array<string | false | undefined>) {
  return values.filter(Boolean).join(' ')
}

function useClock(intervalMs = 1000) {
  const [now, setNow] = useState(() => new Date())

  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), intervalMs)
    return () => window.clearInterval(timer)
  }, [intervalMs])

  return now
}

function buildLivePerformance(points: PerformancePoint[], currentValue: number, now: Date) {
  const realValue = currentValue > 0 ? currentValue : (points.at(-1)?.value ?? 100_000)
  const nowMs = now.getTime()
  const parsed = points
    .map((point) => ({ timestamp: point.timestamp, value: Number(point.value), ms: new Date(point.timestamp).getTime() }))
    .filter((point) => Number.isFinite(point.value) && Number.isFinite(point.ms))
    .sort((a, b) => a.ms - b.ms)
  const recent = parsed.filter((point) => nowMs - point.ms <= 2 * 60 * 1000).slice(-24)

  if (recent.length >= 6) {
    const currentPoint = { timestamp: now.toISOString(), value: realValue }
    const latest = recent.at(-1)
    return latest && nowMs - latest.ms < 1000 ? recent.map(({ timestamp, value }) => ({ timestamp, value })) : [...recent.map(({ timestamp, value }) => ({ timestamp, value })), currentPoint]
  }

  const seconds = nowMs / 1000
  return Array.from({ length: 36 }, (_, index) => {
    const age = 35 - index
    const wave =
      Math.sin(seconds / 2.4 + index * 0.8) * realValue * 0.0009 +
      Math.sin(seconds / 5.7 + index * 0.35) * realValue * 0.00035
    return {
      timestamp: new Date(nowMs - age * 1000).toISOString(),
      value: Math.max(0, realValue + wave),
    }
  })
}

function demoOrderFromRequest(request: OrderRequest, stocks: Stock[], portfolioName: string): Order {
  const stock = stocks.find((item) => item.symbol === request.symbol) ?? demoStocks[0]
  const limitPrice = request.type === 'LIMIT' ? request.limitPrice : undefined
  const canFillLimit =
    request.type === 'LIMIT' && limitPrice !== undefined
      ? request.side === 'BUY'
        ? limitPrice >= stock.lastPrice
        : limitPrice <= stock.lastPrice
      : true
  const status: OrderStatus = request.type === 'MARKET' || canFillLimit ? 'FILLED' : 'PENDING'
  const executionPrice = status === 'FILLED' ? stock.lastPrice : undefined
  const gross = (executionPrice ?? limitPrice ?? stock.lastPrice) * Number(request.quantity)
  const fees = Math.max(1, Math.min(50, gross * 0.001))
  return {
    id: `demo-order-${Date.now()}`,
    clientOrderId: request.clientOrderId,
    portfolioId: request.portfolioId,
    portfolioName,
    symbol: request.symbol,
    side: request.side,
    type: request.type,
    status,
    quantity: Number(request.quantity),
    limitPrice,
    executionPrice,
    fees,
    createdAt: new Date().toISOString(),
    filledAt: status === 'FILLED' ? new Date().toISOString() : undefined,
  }
}

function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<RequireAuth />}>
            <Route element={<Shell />}>
              <Route index element={<DashboardPage />} />
              <Route path="portfolios" element={<PortfolioPage />} />
              <Route path="trading" element={<TradingPage />} />
              <Route path="watchlist" element={<WatchlistPage />} />
              <Route path="admin" element={<AdminPage />} />
            </Route>
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}

function RequireAuth() {
  const { user } = useAuth()
  return user ? <Outlet /> : <Navigate to="/login" replace />
}

function LoginPage() {
  const { login, register, user } = useAuth()
  const navigate = useNavigate()
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [error, setError] = useState('')
  const [statusMessage, setStatusMessage] = useState('')
  const [authInProgress, setAuthInProgress] = useState(false)
  const systemQuery = useQuery({
    queryKey: ['system-status'],
    queryFn: api.systemStatus,
    retry: false,
    refetchInterval: 15_000,
  })
  const { register: field, handleSubmit, formState } = useForm({
    defaultValues: {
      email: 'user@ledgerone.com',
      password: 'User123!',
      fullName: 'Ashish Mishra',
    },
  })

  if (user && !authInProgress && !statusMessage) {
    return <Navigate to="/" replace />
  }

  const switchMode = (nextMode: 'login' | 'register') => {
    setMode(nextMode)
    setError('')
    setStatusMessage('')
  }

  const submitLabel = formState.isSubmitting
    ? mode === 'login'
      ? 'Signing in...'
      : 'Creating account...'
    : mode === 'login'
      ? 'Sign in'
      : 'Create account'

  return (
    <main className="min-h-screen bg-[#101317] text-slate-100">
      <div className="mx-auto grid min-h-screen w-full max-w-7xl grid-cols-1 lg:grid-cols-[0.88fr_1.12fr]">
        <section className="flex flex-col justify-between border-b border-white/10 bg-[#151a20] p-6 lg:border-b-0 lg:border-r lg:p-10">
          <div className="flex items-center gap-3">
            <LogoMark />
            <div>
              <p className="text-lg font-semibold text-white">LedgerOne</p>
              <p className="text-xs uppercase tracking-[0.24em] text-slate-400">Paper Trading</p>
            </div>
          </div>
          <div className="my-12 max-w-xl">
            <div className="mb-6 inline-flex items-center gap-2 rounded border border-emerald-400/30 bg-emerald-400/10 px-3 py-2 text-sm text-emerald-200">
              <ShieldCheck size={16} />
              Full-stack stock paper trading platform
            </div>
            <h1 className="text-4xl font-semibold leading-tight text-white sm:text-5xl">
              Practice stock trading with live quotes, buying power, watchlists, and audit-ready trade history.
            </h1>
            <ApiModeBadge online={systemQuery.isSuccess} className="mt-6" />
          </div>
          <div className="grid grid-cols-3 gap-3">
            {[
              ['JWT', 'Auth'],
              ['JPA', 'Ledger'],
              ['Risk', 'Alerts'],
            ].map(([top, bottom]) => (
              <div key={top} className="rounded border border-white/10 bg-white/[0.03] p-4">
                <p className="text-xl font-semibold text-white">{top}</p>
                <p className="text-sm text-slate-400">{bottom}</p>
              </div>
            ))}
          </div>
        </section>
        <section className="flex items-center justify-center p-6">
          <form
            className="w-full max-w-md rounded border border-white/10 bg-[#171d23] p-6 shadow-2xl shadow-black/30"
            onSubmit={handleSubmit(async (values) => {
              setAuthInProgress(true)
              setError('')
              setStatusMessage('')
              try {
                if (mode === 'login') {
                  await login(values.email, values.password)
                } else {
                  await register(values.email, values.password, values.fullName)
                }
                const demoSession = localStorage.getItem('ledgerone.accessToken') === 'demo-access-token'
                setStatusMessage(
                  demoSession
                    ? 'Demo workspace ready. Opening dashboard...'
                    : mode === 'login'
                      ? 'Signed in. Opening dashboard...'
                      : 'Account created. Opening dashboard...',
                )
                await new Promise((resolve) => window.setTimeout(resolve, 500))
                navigate('/')
              } catch (exception) {
                setError(exception instanceof Error ? exception.message : 'Authentication failed')
              } finally {
                setAuthInProgress(false)
              }
            })}
          >
            <div className="mb-6 flex rounded border border-white/10 bg-[#11161b] p-1">
              <button
                type="button"
                className={classNames(
                  'h-10 flex-1 rounded text-sm font-medium',
                  mode === 'login' ? 'bg-emerald-400 text-[#0d1114]' : 'text-slate-300',
                )}
                onClick={() => switchMode('login')}
              >
                Sign in
              </button>
              <button
                type="button"
                className={classNames(
                  'h-10 flex-1 rounded text-sm font-medium',
                  mode === 'register' ? 'bg-emerald-400 text-[#0d1114]' : 'text-slate-300',
                )}
                onClick={() => switchMode('register')}
              >
                Register
              </button>
            </div>
            <div className="space-y-4">
              {mode === 'register' && (
                <Field label="Full name">
                  <input {...field('fullName', { required: true })} className="input" />
                </Field>
              )}
              <Field label="Email">
                <input {...field('email', { required: true })} className="input" type="email" />
              </Field>
              <Field label="Password">
                <input {...field('password', { required: true, minLength: 8 })} className="input" type="password" />
              </Field>
            </div>
            {error && <p className="mt-4 rounded border border-red-400/30 bg-red-500/10 px-3 py-2 text-sm text-red-200">{error}</p>}
            {statusMessage && <ActionNotice tone="success" message={statusMessage} className="mt-4" />}
            <button
              type="submit"
              className="mt-6 flex h-11 w-full items-center justify-center gap-2 rounded bg-emerald-400 px-4 font-semibold text-[#0d1114] hover:bg-emerald-300 disabled:cursor-not-allowed disabled:opacity-70"
              disabled={formState.isSubmitting}
              aria-busy={formState.isSubmitting}
            >
              {formState.isSubmitting ? <Loader2 className="animate-spin" size={18} /> : <Lock size={18} />}
              {submitLabel}
            </button>
          </form>
        </section>
      </div>
    </main>
  )
}

function Shell() {
  const { user, isAdmin, isDemoSession, logout } = useAuth()
  const [notificationsOpen, setNotificationsOpen] = useState(false)
  const systemQuery = useQuery({
    queryKey: ['system-status'],
    queryFn: api.systemStatus,
    retry: false,
    refetchInterval: 15_000,
  })
  const notificationsQuery = useQuery({
    queryKey: ['notifications'],
    queryFn: api.notifications,
    placeholderData: demoNotifications,
    enabled: Boolean(user),
  })
  const notifications = isDemoSession ? demoNotifications : (notificationsQuery.data ?? demoNotifications)
  const unreadNotifications = notifications.filter((notification) => !notification.read).length
  const navItems = [
    { to: '/', label: 'Overview', icon: LayoutDashboard },
    { to: '/portfolios', label: 'Account', icon: BriefcaseBusiness },
    { to: '/trading', label: 'Trading', icon: CircleDollarSign },
    { to: '/watchlist', label: 'Watchlist', icon: Eye },
    ...(isAdmin ? [{ to: '/admin', label: 'Admin', icon: Building2 }] : []),
  ]
  return (
    <div className="flex min-h-screen bg-[#101317] text-slate-100">
      <aside className="hidden w-72 shrink-0 border-r border-white/10 bg-[#151a20] px-4 py-5 lg:block">
        <div className="mb-8 flex items-center gap-3 px-2">
          <LogoMark />
          <div>
            <p className="font-semibold text-white">LedgerOne</p>
            <p className="text-xs uppercase tracking-[0.18em] text-slate-500">Paper Trading</p>
          </div>
        </div>
        <nav className="space-y-1">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) =>
                classNames(
                  'flex h-11 items-center gap-3 rounded px-3 text-sm font-medium transition',
                  isActive ? 'bg-emerald-400 text-[#0d1114]' : 'text-slate-300 hover:bg-white/[0.06] hover:text-white',
                )
              }
            >
              <item.icon size={18} />
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-10 border-b border-white/10 bg-[#101317]/90 backdrop-blur">
          <div className="flex min-h-16 items-center justify-between gap-4 px-4 py-3 sm:px-6">
            <div className="min-w-0">
              <p className="truncate text-sm text-slate-400">Signed in as {user?.email}</p>
              <p className="truncate text-lg font-semibold text-white">{user?.fullName}</p>
            </div>
            <div className="relative flex items-center gap-2">
              <ApiModeBadge online={systemQuery.isSuccess && !isDemoSession} compact />
              <button
                className="icon-button relative"
                aria-label="Notifications"
                aria-expanded={notificationsOpen}
                onClick={() => setNotificationsOpen((open) => !open)}
              >
                <Bell size={18} />
                {unreadNotifications > 0 && (
                  <span className="absolute -right-1 -top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-amber-300 px-1 text-[10px] font-semibold text-[#0d1114]">
                    {unreadNotifications}
                  </span>
                )}
              </button>
              <button className="icon-button" aria-label="Sign out" onClick={() => void logout()}>
                <LogOut size={18} />
              </button>
              {notificationsOpen && (
                <NotificationsMenu notifications={notifications} onClose={() => setNotificationsOpen(false)} />
              )}
            </div>
          </div>
          <nav className="flex gap-1 overflow-x-auto border-t border-white/10 px-4 py-2 lg:hidden">
            {navItems.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === '/'}
                className={({ isActive }) =>
                  classNames(
                    'inline-flex h-10 shrink-0 items-center gap-2 rounded px-3 text-sm',
                    isActive ? 'bg-emerald-400 text-[#0d1114]' : 'text-slate-300',
                  )
                }
              >
                <item.icon size={16} />
                {item.label}
              </NavLink>
            ))}
          </nav>
        </header>
        <main className="flex-1 p-4 sm:p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

function DashboardPage() {
  const dashboardQuery = useQuery({ queryKey: ['dashboard'], queryFn: () => api.dashboard(), placeholderData: demoDashboard })
  const dashboard = dashboardQuery.data ?? demoDashboard
  const now = useClock(1000)
  const livePerformance = useMemo(
    () => buildLivePerformance(dashboard.performance, dashboard.portfolioValue || dashboard.cashBalance, now),
    [dashboard.cashBalance, dashboard.performance, dashboard.portfolioValue, now],
  )
  return (
    <Page title="Account Overview" eyebrow={dashboard.portfolioName} action={<RiskBadge score={dashboard.riskScore} />}>
      <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-6">
        {dashboard.metrics.map((metric) => (
          <Metric key={metric.label} metric={metric} />
        ))}
      </section>
      <section className="mt-4 grid gap-4 xl:grid-cols-[1.45fr_0.55fr]">
        <Panel title="Live Account Performance" icon={TrendingUp}>
          <div className="h-80">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={livePerformance}>
                <defs>
                  <linearGradient id="performance" x1="0" x2="0" y1="0" y2="1">
                    <stop offset="5%" stopColor="#47c7a1" stopOpacity={0.45} />
                    <stop offset="95%" stopColor="#47c7a1" stopOpacity={0.04} />
                  </linearGradient>
                </defs>
                <CartesianGrid stroke="#27313a" vertical={false} />
                <XAxis dataKey="timestamp" minTickGap={28} tickFormatter={(value) => formatChartTick(String(value))} />
                <YAxis
                  domain={[
                    (dataMin: number) => Math.max(0, dataMin * 0.998),
                    (dataMax: number) => dataMax * 1.002,
                  ]}
                  tickFormatter={(value) => `$${Math.round(Number(value) / 1000)}k`}
                  width={54}
                />
                <Tooltip content={<ChartTooltip />} />
                <Area
                  className="live-performance-area"
                  type="monotone"
                  dataKey="value"
                  stroke="#47c7a1"
                  strokeWidth={2.5}
                  fill="url(#performance)"
                  dot={false}
                  activeDot={{ r: 4, fill: '#47c7a1', stroke: '#0d1114', strokeWidth: 2 }}
                  isAnimationActive={false}
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </Panel>
        <Panel title="Account Allocation" icon={PieChartIcon}>
          <div className="h-80">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie data={dashboard.allocation} dataKey="value" nameKey="label" innerRadius={68} outerRadius={110} paddingAngle={2}>
                  {dashboard.allocation.map((slice, index) => (
                    <Cell key={slice.label} fill={allocationColors[index % allocationColors.length]} />
                  ))}
                </Pie>
                <Tooltip formatter={(value) => currency(Number(value))} />
              </PieChart>
            </ResponsiveContainer>
          </div>
          <div className="space-y-2">
            {dashboard.allocation.map((slice, index) => (
              <div key={slice.label} className="flex items-center justify-between text-sm">
                <span className="flex items-center gap-2 text-slate-300">
                  <span className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: allocationColors[index % allocationColors.length] }} />
                  {slice.label}
                </span>
                <span className="font-medium text-white">{compactNumber(slice.percent)}%</span>
              </div>
            ))}
          </div>
        </Panel>
      </section>
      <section className="mt-4 grid gap-4 xl:grid-cols-2">
        <TransactionsTable transactions={dashboard.recentTransactions} />
        <RiskAlerts alerts={dashboard.riskAlerts} />
      </section>
    </Page>
  )
}

function NotificationsMenu({ notifications, onClose }: { notifications: AppNotification[]; onClose: () => void }) {
  return (
    <div className="absolute right-0 top-12 z-30 w-[min(24rem,calc(100vw-2rem))] rounded border border-white/10 bg-[#171d23] shadow-2xl shadow-black/40">
      <div className="flex items-center justify-between border-b border-white/10 px-4 py-3">
        <div>
          <p className="text-sm font-semibold text-white">Activity feed</p>
          <p className="text-xs text-slate-500">{notifications.filter((notification) => !notification.read).length} unread updates</p>
        </div>
        <button className="icon-button h-8 w-8" aria-label="Close notifications" onClick={onClose}>
          <X size={16} />
        </button>
      </div>
      <div className="max-h-96 overflow-y-auto p-2 scrollbar-thin">
        {notifications.map((notification) => (
          <div key={notification.id} className="rounded border border-white/10 bg-[#11161b] p-3">
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="font-medium text-white">{notification.title}</p>
                <p className="mt-1 text-sm leading-5 text-slate-400">{notification.message}</p>
              </div>
              {!notification.read && <span className="mt-1 h-2.5 w-2.5 shrink-0 rounded-full bg-amber-300" aria-label="Unread" />}
            </div>
            <p className="mt-2 text-xs text-slate-500">{formatDate(notification.createdAt)}</p>
          </div>
        ))}
      </div>
    </div>
  )
}

function PortfolioPage() {
  const { isDemoSession } = useAuth()
  const portfoliosQuery = useQuery({ queryKey: ['portfolios'], queryFn: api.portfolios, placeholderData: isDemoSession ? [demoPortfolio] : undefined })
  const accountQuery = useQuery({
    queryKey: ['account-summary'],
    queryFn: api.accountSummary,
    enabled: !isDemoSession,
    placeholderData: isDemoSession ? demoPaperAccount : undefined,
  })
  const portfolio = (isDemoSession ? [demoPortfolio] : (portfoliosQuery.data ?? []))[0]
  const account = isDemoSession ? demoPaperAccount : accountQuery.data
  return (
    <Page title="Paper Account" eyebrow="Buying power, holdings, cost basis">
      <div className="grid gap-4">
        <Panel title="Account Summary" icon={WalletCards}>
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <MiniStat label="Buying Power" value={currency(account?.availableCash)} />
            <MiniStat label="Total Equity" value={currency(account?.totalEquity)} />
            <MiniStat label="Invested Value" value={currency(account?.marketValue)} />
            <MiniStat label="Account Status" value={account?.activePortfolioCount ? 'Ready' : 'Preparing'} />
          </div>
        </Panel>
        {portfolio ? (
          <PortfolioDetail portfolio={portfolio} />
        ) : (
          <Panel title="Account Setup" icon={BriefcaseBusiness}>
            <p className="text-sm text-slate-400">Your trading account is being prepared.</p>
          </Panel>
        )}
      </div>
    </Page>
  )
}

function PortfolioDetail({ portfolio }: { portfolio: Portfolio }) {
  return (
    <Panel
      title={portfolio.name}
      icon={WalletCards}
      action={
        <span className="text-sm font-medium text-slate-300">{currency(portfolio.totalValue)}</span>
      }
    >
      <div className="mb-4 grid gap-3 sm:grid-cols-3">
        <MiniStat label="Cash" value={currency(portfolio.cashBalance)} />
        <MiniStat label="Market Value" value={currency(portfolio.marketValue)} />
        <MiniStat label="Unrealized P/L" value={currency(portfolio.unrealizedProfit)} tone={portfolio.unrealizedProfit >= 0 ? 'positive' : 'negative'} />
      </div>
      <div className="overflow-x-auto scrollbar-thin">
        <table className="data-table">
          <thead>
            <tr>
              <th>Symbol</th>
              <th>Sector</th>
              <th>Quantity</th>
              <th>Avg Cost</th>
              <th>Market Value</th>
              <th>Unrealized P/L</th>
              <th>Alloc.</th>
            </tr>
          </thead>
          <tbody>
            {portfolio.holdings.map((holding: Holding) => (
              <tr key={holding.id}>
                <td className="font-semibold text-white">{holding.symbol}</td>
                <td>{holding.sector}</td>
                <td>{compactNumber(holding.quantity)}</td>
                <td>{currency(holding.averageCost)}</td>
                <td>{currency(holding.marketValue)}</td>
                <td className={holding.unrealizedProfit >= 0 ? 'text-emerald-300' : 'text-red-300'}>{currency(holding.unrealizedProfit)}</td>
                <td>{compactNumber(holding.allocationPercent)}%</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Panel>
  )
}

function TradingPage() {
  const { isDemoSession } = useAuth()
  const queryClient = useQueryClient()
  const [demoOrdersState, setDemoOrdersState] = useState<Order[]>(demoOrders)
  const [stockChoices, setStockChoices] = useState<Stock[]>([])
  const [marketResults, setMarketResults] = useState<Stock[] | null>(null)
  const [symbolSearch, setSymbolSearch] = useState('AAPL')
  const [message, setMessage] = useState('')
  const orderTicketRef = useRef<HTMLDivElement>(null)
  const portfoliosQuery = useQuery({ queryKey: ['portfolios'], queryFn: api.portfolios, placeholderData: isDemoSession ? [demoPortfolio] : undefined })
  const stocksQuery = useQuery({
    queryKey: ['stocks'],
    queryFn: api.stocks,
    placeholderData: demoStocks,
    refetchInterval: 60_000,
  })
  const portfolios = useMemo(() => {
    const accounts = isDemoSession ? [demoPortfolio] : (portfoliosQuery.data ?? [])
    return accounts.slice(0, 1)
  }, [isDemoSession, portfoliosQuery.data])
  const selectedPortfolioId = portfolios[0]?.id
  const ordersQuery = useQuery({
    queryKey: ['orders', selectedPortfolioId],
    queryFn: () => api.orders(selectedPortfolioId ?? ''),
    enabled: isDemoSession || Boolean(selectedPortfolioId),
    placeholderData: isDemoSession ? pageOfOrders : undefined,
  })
  const stocks = useMemo(
    () => (isDemoSession ? mergeStocks(demoStocks, stockChoices) : mergeStocks(demoStocks, stocksQuery.data ?? [], stockChoices)),
    [isDemoSession, stockChoices, stocksQuery.data],
  )
  const marketStocks = marketResults ?? stocks
  const orders = isDemoSession ? demoOrdersState : (ordersQuery.data?.content ?? [])
  const { register, handleSubmit, watch, reset, setValue } = useForm<OrderRequest>({
    defaultValues: {
      portfolioId: selectedPortfolioId ?? '',
      symbol: 'AAPL',
      side: 'BUY',
      type: 'MARKET',
      quantity: 5,
      clientOrderId: `web-${Date.now()}`,
    },
  })
  const type = watch('type')
  const side = watch('side')
  const symbol = watch('symbol')
  const formPortfolioId = watch('portfolioId') || selectedPortfolioId
  const quantity = Number(watch('quantity') ?? 0)
  const normalizedOrderSymbol = (symbol || symbolSearch).trim().toUpperCase()
  const quoteQuery = useQuery({
    queryKey: ['stock-quote', normalizedOrderSymbol],
    queryFn: () => api.quoteStock(normalizedOrderSymbol),
    enabled: !isDemoSession && normalizedOrderSymbol.length > 0,
    refetchInterval: 15_000,
    retry: 1,
    staleTime: 10_000,
  })
  const selectedPortfolio = portfolios.find((portfolio) => portfolio.id === formPortfolioId) ?? portfolios[0]
  const selectedStock =
    quoteQuery.data ??
    stocks.find((stock) => stock.symbol === normalizedOrderSymbol) ??
    marketStocks.find((stock) => stock.symbol === normalizedOrderSymbol) ??
    (!normalizedOrderSymbol ? stocks[0] : undefined)
  const estimatedNotional = selectedStock ? selectedStock.lastPrice * quantity : 0
  const estimatedFee = estimatedNotional > 0 ? Math.min(Math.max(estimatedNotional * 0.001, 1), 50) : 0
  const buyingPower = selectedPortfolio?.cashBalance ?? 0
  const estimatedCashAfterBuy = buyingPower - estimatedNotional - estimatedFee
  const hasBuyingPower = side !== 'BUY' || !selectedStock || estimatedCashAfterBuy >= 0
  const hasQuantity = Number.isFinite(quantity) && quantity > 0
  const accountReady = isDemoSession || (Boolean(selectedPortfolioId) && !portfoliosQuery.isLoading)
  const quoteRefreshing = !isDemoSession && normalizedOrderSymbol.length > 0 && quoteQuery.isFetching && !quoteQuery.data
  const formReady = accountReady && normalizedOrderSymbol.length > 0 && hasQuantity && !quoteRefreshing
  const submitDisabledReason = !accountReady
    ? 'Account is still loading.'
    : normalizedOrderSymbol.length === 0
      ? 'Search or select a stock symbol before submitting.'
      : !hasQuantity
        ? 'Quantity must be greater than zero.'
        : quoteRefreshing
          ? 'Refreshing the live quote before order submission.'
          : !hasBuyingPower
            ? 'Buying power is below the estimated order cost.'
            : ''
  const chooseStock = (stock: Stock, nextSide: OrderSide = side) => {
    setStockChoices((current) => mergeStocks(current, [stock]))
    setSymbolSearch(stock.symbol)
    setValue('symbol', stock.symbol, { shouldDirty: true, shouldValidate: true })
    setValue('side', nextSide, { shouldDirty: true })
  }
  const openOrderTicket = (stock: Stock, nextSide: OrderSide) => {
    chooseStock(stock, nextSide)
    window.requestAnimationFrame(() => {
      orderTicketRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
      orderTicketRef.current?.querySelector<HTMLInputElement>('input[name="quantity"]')?.focus({ preventScroll: true })
    })
  }

  useEffect(() => {
    if (selectedPortfolioId) {
      setValue('portfolioId', selectedPortfolioId)
    }
  }, [selectedPortfolioId, setValue])

  useEffect(() => {
    if (!symbol && stocks[0]) {
      setValue('symbol', stocks[0].symbol)
      setSymbolSearch(stocks[0].symbol)
    }
  }, [setValue, stocks, symbol])

  useEffect(() => {
    if (quoteQuery.data) {
      setStockChoices((current) => mergeStocks(current, [quoteQuery.data]))
    }
  }, [quoteQuery.data])

  const stockSearchMutation = useMutation({
    mutationFn: async (query: string) => {
      const trimmed = query.trim()
      if (trimmed.length === 0) {
        setMarketResults(null)
        return []
      }
      if (isDemoSession) {
        const normalized = trimmed.toUpperCase()
        return demoStocks.filter(
          (stock) => stock.symbol.includes(normalized) || stock.companyName.toUpperCase().includes(normalized),
        )
      }
      return api.searchStocks(trimmed)
    },
    onSuccess: (results) => {
      if (results.length === 0) {
        setMarketResults([])
        setMessage('No matching live symbols found')
        return
      }
      setMarketResults(results)
      setStockChoices((current) => mergeStocks(current, results))
      chooseStock(results[0], 'BUY')
    },
    onError: (error) => setMessage(getApiErrorMessage(error, 'Stock search failed. Try a ticker symbol like TSLA.')),
  })

  const mutation = useMutation({
    mutationFn: async (request: OrderRequest) => {
      if (isDemoSession) {
        const portfolio = portfolios.find((item) => item.id === request.portfolioId)
        const order = demoOrderFromRequest(request, stocks, portfolio?.name ?? demoPortfolio.name)
        setDemoOrdersState((current) => [order, ...current])
        return order
      }
      const quoted = await api.quoteStock(request.symbol)
      setStockChoices((current) => mergeStocks(current, [quoted]))
      return api.placeOrder(request)
    },
    onSuccess: (order) => {
      setMessage(`Order ${order.status.toLowerCase()} for ${order.symbol}`)
      if (!isDemoSession) {
        queryClient.setQueryData<PageResponse<Order>>(['orders', selectedPortfolioId], (current) => ({
          content: [order, ...(current?.content.filter((item) => item.id !== order.id) ?? [])],
          page: current?.page ?? 0,
          size: current?.size ?? 20,
          totalElements: (current?.totalElements ?? 0) + (current?.content.some((item) => item.id === order.id) ? 0 : 1),
          totalPages: current?.totalPages ?? 1,
          last: current?.last ?? true,
        }))
      }
      reset({ portfolioId: selectedPortfolioId ?? '', symbol: 'AAPL', side: 'BUY', type: 'MARKET', quantity: 5, clientOrderId: `web-${Date.now()}` })
      setSymbolSearch('AAPL')
      setMarketResults(null)
      void queryClient.invalidateQueries({ queryKey: ['orders'] })
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      void queryClient.invalidateQueries({ queryKey: ['portfolios'] })
    },
    onError: (error) => setMessage(getApiErrorMessage(error, 'Order request failed. Confirm API/CORS or continue in demo mode.')),
  })
  const cancelMutation = useMutation({
    mutationFn: async (order: Order) => {
      if (isDemoSession) {
        setDemoOrdersState((current) => current.map((item) => (item.id === order.id ? { ...item, status: 'CANCELLED' } : item)))
        return { ...order, status: 'CANCELLED' as OrderStatus }
      }
      return api.cancelOrder(order.id)
    },
    onSuccess: (order) => {
      setMessage(`${order.symbol} order cancelled`)
      if (!isDemoSession) {
        queryClient.setQueryData<PageResponse<Order>>(['orders', selectedPortfolioId], (current) =>
          current ? { ...current, content: current.content.map((item) => (item.id === order.id ? order : item)) } : current,
        )
      }
      void queryClient.invalidateQueries({ queryKey: ['orders'] })
    },
    onError: () => setMessage('Only pending orders can be cancelled.'),
  })
  const orderMessageIsWarning = ['failed', 'only pending', 'insufficient', 'unavailable', 'require', 'not found', 'no matching'].some((term) =>
    message.toLowerCase().includes(term),
  )
  const symbolField = register('symbol', { required: true })
  return (
    <Page title="Trading" eyebrow={isDemoSession ? 'Demo paper trading' : 'Live paper trading'}>
      <div className="grid gap-4 xl:grid-cols-[0.9fr_1.1fr]">
        <div className="grid gap-4">
          <MarketBrowser
            stocks={marketStocks}
            selectedSymbol={symbol}
            searchValue={symbolSearch}
            searchPending={stockSearchMutation.isPending}
            onSearchChange={(value) => {
              const normalized = value.toUpperCase()
              setSymbolSearch(normalized)
              setValue('symbol', normalized, { shouldDirty: true, shouldValidate: true })
            }}
            onSearch={() => stockSearchMutation.mutate(symbolSearch)}
            onSelect={(stock) => chooseStock(stock)}
            onBuy={(stock) => openOrderTicket(stock, 'BUY')}
            onSell={(stock) => openOrderTicket(stock, 'SELL')}
          />
          <div ref={orderTicketRef}>
            <Panel title="New Order" icon={CircleDollarSign}>
              <form
                className="grid gap-4"
                onSubmit={handleSubmit((values) => {
                  const requestSymbol = (values.symbol || symbolSearch).trim().toUpperCase()
                  if (!requestSymbol) {
                    setMessage('Search or select a stock symbol before submitting.')
                    return
                  }
                  mutation.mutate({
                    ...values,
                    portfolioId: values.portfolioId || selectedPortfolioId || '',
                    symbol: requestSymbol,
                    quantity: Number(values.quantity),
                    limitPrice: values.type === 'LIMIT' ? Number(values.limitPrice) : undefined,
                    clientOrderId: values.clientOrderId || `web-${Date.now()}`,
                  })
                })}
              >
                <Field label="Account">
                  <input type="hidden" {...register('portfolioId', { required: true })} />
                  <input
                    className="input"
                    readOnly
                    value={selectedPortfolio?.name ?? (accountReady ? 'Paper Trading Account' : 'Loading account...')}
                  />
                </Field>
                <div className="grid grid-cols-2 gap-3">
                  <Field label="Selected symbol">
                    <input className="input" readOnly {...symbolField} value={normalizedOrderSymbol} />
                  </Field>
                  <Field label="Quantity">
                    <input className="input" min="0.000001" step="0.000001" type="number" {...register('quantity', { required: true, min: 0.000001, valueAsNumber: true })} />
                  </Field>
                </div>
                {stocksQuery.isError && (
                  <ActionNotice tone="warning" message={getApiErrorMessage(stocksQuery.error, 'Live stock prices are unavailable. Try again shortly.')} />
                )}
                {quoteQuery.isError && !isDemoSession && (
                  <ActionNotice tone="warning" message={getApiErrorMessage(quoteQuery.error, 'Live quote refresh failed. Try searching the symbol again.')} />
                )}
                {selectedStock && (
                  <div className="rounded border border-white/10 bg-[#11161b] p-1">
                    <QuoteRow label={`${selectedStock.symbol} price`} value={currency(selectedStock.lastPrice)} />
                    <QuoteRow label="Estimated value" value={currency(estimatedNotional)} />
                    <QuoteRow label="Buying power" value={currency(buyingPower)} />
                    {side === 'BUY' && <QuoteRow label="Cash after order" value={currency(estimatedCashAfterBuy)} />}
                    <QuoteRow label="Quote updated" value={formatDate(selectedStock.updatedAt)} />
                  </div>
                )}
                {submitDisabledReason && <ActionNotice tone="warning" message={submitDisabledReason} />}
                <Segmented label="Side" options={['BUY', 'SELL']} field={register('side')} />
                <Segmented label="Order type" options={['MARKET', 'LIMIT']} field={register('type')} />
                {type === 'LIMIT' && (
                  <Field label="Limit price">
                    <input className="input" min="0.01" step="0.01" type="number" {...register('limitPrice', { valueAsNumber: true })} />
                  </Field>
                )}
                <Field label="Client order id">
                  <input className="input" {...register('clientOrderId', { required: true })} />
                </Field>
                <button className="primary-button" disabled={mutation.isPending || !formReady || !hasBuyingPower} type="submit">
                  <ArrowUpRight size={18} />
                  {mutation.isPending ? 'Submitting order...' : 'Submit order'}
                </button>
                {message && <ActionNotice tone={orderMessageIsWarning ? 'warning' : 'success'} message={message} />}
              </form>
            </Panel>
          </div>
        </div>
        <div className="grid gap-4">
          <StockDetail
            stock={selectedStock}
            estimatedNotional={estimatedNotional}
            buyingPower={buyingPower}
            estimatedCashAfterBuy={estimatedCashAfterBuy}
            onBuy={(stock) => openOrderTicket(stock, 'BUY')}
            onSell={(stock) => openOrderTicket(stock, 'SELL')}
          />
          <OrdersTable orders={orders} onCancel={(order) => cancelMutation.mutate(order)} />
        </div>
      </div>
    </Page>
  )
}

function WatchlistPage() {
  const { isDemoSession } = useAuth()
  const queryClient = useQueryClient()
  const [demoWatchlistState, setDemoWatchlistState] = useState(demoWatchlist)
  const [stockChoices, setStockChoices] = useState<Stock[]>([])
  const [symbolSearch, setSymbolSearch] = useState('GOOGL')
  const [message, setMessage] = useState('')
  const watchlistQuery = useQuery({ queryKey: ['watchlist'], queryFn: api.watchlist, placeholderData: isDemoSession ? demoWatchlist : undefined })
  const stocksQuery = useQuery({
    queryKey: ['stocks'],
    queryFn: api.stocks,
    placeholderData: isDemoSession ? demoStocks : undefined,
    refetchInterval: 60_000,
  })
  const stocks = isDemoSession ? mergeStocks(demoStocks, stockChoices) : mergeStocks(stocksQuery.data ?? [], stockChoices)
  const watchlist = isDemoSession ? demoWatchlistState : (watchlistQuery.data ?? [])
  const { register, handleSubmit, reset, setValue } = useForm({ defaultValues: { symbol: 'GOOGL' } })
  const stockSearchMutation = useMutation({
    mutationFn: async (query: string) => {
      const trimmed = query.trim()
      if (trimmed.length === 0) {
        return []
      }
      if (isDemoSession) {
        const normalized = trimmed.toUpperCase()
        return demoStocks.filter(
          (stock) => stock.symbol.includes(normalized) || stock.companyName.toUpperCase().includes(normalized),
        )
      }
      return api.searchStocks(trimmed)
    },
    onSuccess: (results) => {
      if (results.length === 0) {
        setMessage('No matching live symbols found')
        return
      }
      setStockChoices((current) => mergeStocks(current, results))
      setSymbolSearch(results[0].symbol)
      setValue('symbol', results[0].symbol)
    },
    onError: (error) => setMessage(getApiErrorMessage(error, 'Stock search failed. Try a ticker symbol like TSLA.')),
  })
  const addMutation = useMutation({
    mutationFn: async ({ symbol }: { symbol: string }) => {
      const normalizedSymbol = symbol.trim().toUpperCase()
      if (isDemoSession) {
        const stock = stocks.find((item) => item.symbol === normalizedSymbol) ?? demoStocks[0]
        const existing = demoWatchlistState.find((item) => item.stock.symbol === stock.symbol)
        if (existing) return existing
        const item = { id: `demo-watch-${stock.symbol}-${Date.now()}`, stock, createdAt: new Date().toISOString() }
        setDemoWatchlistState((current) => [item, ...current])
        return item
      }
      return api.addWatchlist(normalizedSymbol)
    },
    onSuccess: (item) => {
      reset({ symbol: item.stock.symbol })
      setSymbolSearch(item.stock.symbol)
      setMessage(`${item.stock.symbol} is on the watchlist`)
      if (!isDemoSession) {
        queryClient.setQueryData<WatchlistItem[]>(['watchlist'], (current) => {
          const existing = current ?? []
          return existing.some((entry) => entry.id === item.id) ? existing : [item, ...existing]
        })
      }
      void queryClient.invalidateQueries({ queryKey: ['watchlist'] })
    },
    onError: () => setMessage('Watchlist update failed. Check API/CORS.'),
  })
  const removeMutation = useMutation({
    mutationFn: async (item: WatchlistItem) => {
      if (isDemoSession) {
        setDemoWatchlistState((current) => current.filter((entry) => entry.id !== item.id))
        return item
      }
      await api.removeWatchlist(item.id)
      return item
    },
    onSuccess: (item) => {
      setMessage(`${item.stock.symbol} removed from the watchlist`)
      if (!isDemoSession) {
        queryClient.setQueryData<WatchlistItem[]>(['watchlist'], (current) => current?.filter((entry) => entry.id !== item.id) ?? current)
      }
      void queryClient.invalidateQueries({ queryKey: ['watchlist'] })
    },
    onError: () => setMessage('Remove failed. Check API/CORS.'),
  })
  return (
    <Page title="Watchlist" eyebrow="Tracked equities and price movement">
      <div className="grid gap-4 xl:grid-cols-[0.62fr_1.38fr]">
        <Panel title="Add Symbol" icon={Plus}>
          <form className="space-y-4" onSubmit={handleSubmit((values) => addMutation.mutate(values))}>
            <Field label="Symbol search">
              <div className="grid grid-cols-[minmax(0,1fr)_auto] gap-2">
                <input
                  className="input"
                  {...register('symbol', { required: true })}
                  value={symbolSearch}
                  onChange={(event) => {
                    const next = event.target.value.toUpperCase()
                    setSymbolSearch(next)
                    setValue('symbol', next, { shouldDirty: true, shouldValidate: true })
                  }}
                />
                <button
                  className="secondary-button px-3"
                  disabled={stockSearchMutation.isPending || symbolSearch.trim().length === 0}
                  type="button"
                  onClick={() => stockSearchMutation.mutate(symbolSearch)}
                  aria-label="Search symbols"
                >
                  {stockSearchMutation.isPending ? <Loader2 className="animate-spin" size={18} /> : <Search size={18} />}
                </button>
              </div>
            </Field>
            {stockChoices.length > 0 && (
              <div className="grid gap-2">
                {stockChoices.slice(0, 4).map((stock) => (
                  <button
                    key={stock.symbol}
                    className="flex items-center justify-between gap-3 rounded border border-white/10 bg-[#11161b] px-3 py-2 text-left"
                    type="button"
                    onClick={() => {
                      setSymbolSearch(stock.symbol)
                      setValue('symbol', stock.symbol, { shouldDirty: true, shouldValidate: true })
                    }}
                  >
                    <span>
                      <span className="block text-sm font-semibold text-white">{stock.symbol}</span>
                      <span className="block truncate text-xs text-slate-400">{stock.companyName}</span>
                    </span>
                    <span className="shrink-0 text-sm font-semibold tabular-nums text-emerald-200">{currency(stock.lastPrice)}</span>
                  </button>
                ))}
              </div>
            )}
            <button className="primary-button" type="submit" disabled={symbolSearch.trim().length === 0 || addMutation.isPending}>
              <Plus size={18} />
              Add
            </button>
            {stocksQuery.isError && (
              <ActionNotice tone="warning" message={getApiErrorMessage(stocksQuery.error, 'Live stock prices are unavailable. Try again shortly.')} />
            )}
            {message && <ActionNotice tone={['failed', 'no matching'].some((term) => message.toLowerCase().includes(term)) ? 'warning' : 'success'} message={message} />}
          </form>
        </Panel>
        <Panel title="Tracked Prices" icon={Eye}>
          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
            {watchlist.map((item) => (
              <div key={item.id} className="rounded border border-white/10 bg-[#11161b] p-4">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="text-lg font-semibold text-white">{item.stock.symbol}</p>
                    <p className="text-sm text-slate-400">{item.stock.companyName}</p>
                  </div>
                  <button className="icon-button h-8 w-8" aria-label={`Remove ${item.stock.symbol}`} onClick={() => removeMutation.mutate(item)}>
                    <X size={15} />
                  </button>
                </div>
                <span className="mt-3 inline-flex rounded bg-emerald-400/10 px-2 py-1 text-xs text-emerald-200">{item.stock.sector}</span>
                <div className="mt-4 flex items-end justify-between">
                  <p className="text-2xl font-semibold text-white">{currency(item.stock.lastPrice)}</p>
                  <MiniTrend />
                </div>
              </div>
            ))}
          </div>
        </Panel>
      </div>
    </Page>
  )
}

function AdminPage() {
  const { isDemoSession } = useAuth()
  const [search, setSearch] = useState('')
  const [demoUsers, setDemoUsers] = useState(demoAdminUsers.content)
  const [accountStatuses, setAccountStatuses] = useState<Record<string, AdminUser['status']>>({})
  const [message, setMessage] = useState('')
  const queryClient = useQueryClient()
  const usersQuery = useQuery({ queryKey: ['admin-users', search], queryFn: () => api.adminUsers(search), placeholderData: demoAdminUsers })
  const ordersQuery = useQuery({ queryKey: ['admin-orders'], queryFn: api.adminOrders, placeholderData: pageOfOrders })
  const auditQuery = useQuery({ queryKey: ['audit-logs'], queryFn: api.auditLogs, placeholderData: demoAuditLogs })
  const alertsQuery = useQuery({ queryKey: ['risk-alerts'], queryFn: api.riskAlerts, placeholderData: { content: demoDashboard.riskAlerts, page: 0, size: 20, totalElements: 1, totalPages: 1, last: true } })
  const userRows = isDemoSession ? demoUsers : (usersQuery.data?.content ?? demoUsers)
  const users = userRows
    .map((user) => (accountStatuses[user.id] ? { ...user, status: accountStatuses[user.id] } : user))
    .filter((user) => `${user.fullName} ${user.email}`.toLowerCase().includes(search.trim().toLowerCase()))
  const accountMutation = useMutation({
    mutationFn: async ({ user, nextStatus }: { user: AdminUser; nextStatus: AdminUser['status'] }) => {
      if (isDemoSession) {
        setDemoUsers((current) => current.map((item) => (item.id === user.id ? { ...item, status: nextStatus, updatedAt: new Date().toISOString() } : item)))
        return { ...user, status: nextStatus }
      }
      if (nextStatus === 'FROZEN') {
        await api.freezeUser(user.id)
      } else {
        await api.unfreezeUser(user.id)
      }
      return { ...user, status: nextStatus }
    },
    onSuccess: (user) => {
      setAccountStatuses((current) => ({ ...current, [user.id]: user.status }))
      setMessage(`${user.fullName} is now ${user.status.toLowerCase()}`)
      if (!isDemoSession) {
        queryClient.setQueryData<PageResponse<AdminUser>>(['admin-users', search], (current) =>
          current ? { ...current, content: current.content.map((item) => (item.id === user.id ? user : item)) } : current,
        )
      }
      void queryClient.invalidateQueries({ queryKey: ['admin-users'] })
      void queryClient.invalidateQueries({ queryKey: ['audit-logs'] })
    },
    onError: () => setMessage('Account action failed. Confirm API/CORS and admin role.'),
  })
  return (
    <Page title="Admin Portal" eyebrow="Users, activity, audit, and risk">
      <div className="grid gap-4 xl:grid-cols-[1.1fr_0.9fr]">
        <Panel
          title="User Accounts"
          icon={ShieldCheck}
          action={
            <label className="relative block">
              <Search className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" size={16} />
              <input
                className="h-10 w-56 rounded border border-white/10 bg-[#11161b] pl-9 pr-3 text-sm text-white"
                placeholder="Search users"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
              />
            </label>
          }
        >
          <div className="overflow-x-auto scrollbar-thin">
            <table className="data-table">
              <thead>
                <tr>
                  <th>User</th>
                  <th>Status</th>
                  <th>Roles</th>
                  <th>Created</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {users.map((user: AdminUser) => (
                  <tr key={user.id}>
                    <td>
                      <p className="font-medium text-white">{user.fullName}</p>
                      <p className="text-xs text-slate-500">{user.email}</p>
                    </td>
                    <td>
                      <StatusPill status={user.status} />
                    </td>
                    <td>{user.roles.join(', ')}</td>
                    <td>{formatDate(user.createdAt)}</td>
                    <td>
                      <button
                        className="table-action"
                        disabled={accountMutation.isPending}
                        onClick={() => accountMutation.mutate({ user, nextStatus: user.status === 'ACTIVE' ? 'FROZEN' : 'ACTIVE' })}
                      >
                        {user.status === 'ACTIVE' ? <Ban size={14} /> : <RotateCcw size={14} />}
                        {user.status === 'ACTIVE' ? 'Freeze' : 'Activate'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {message && <ActionNotice tone={message.includes('failed') ? 'warning' : 'success'} message={message} className="mt-3" />}
          </div>
        </Panel>
        <RiskAlerts alerts={alertsQuery.data?.content ?? demoDashboard.riskAlerts} />
      </div>
      <div className="mt-4 grid gap-4 xl:grid-cols-2">
        <OrdersTable orders={ordersQuery.data?.content ?? demoOrders} />
        <Panel title="Audit Logs" icon={FileSearch}>
          <div className="space-y-3">
            {(auditQuery.data?.content ?? demoAuditLogs.content).map((log) => (
              <div key={log.id} className="rounded border border-white/10 bg-[#11161b] p-3">
                <div className="flex items-center justify-between gap-3">
                  <p className="font-medium text-white">{log.subject}</p>
                  <span className="text-xs text-slate-500">{formatDate(log.createdAt)}</span>
                </div>
                <p className="mt-1 text-sm text-slate-400">{log.details}</p>
                <p className="mt-2 text-xs uppercase tracking-[0.16em] text-slate-500">{log.action}</p>
              </div>
            ))}
          </div>
        </Panel>
      </div>
    </Page>
  )
}

function Page({ title, eyebrow, action, children }: { title: string; eyebrow: string; action?: React.ReactNode; children: React.ReactNode }) {
  return (
    <div className="mx-auto max-w-[1500px]">
      <div className="mb-5 flex flex-col justify-between gap-4 md:flex-row md:items-end">
        <div>
          <p className="text-sm font-medium uppercase tracking-[0.18em] text-emerald-300">{eyebrow}</p>
          <h1 className="mt-1 text-3xl font-semibold text-white">{title}</h1>
        </div>
        {action}
      </div>
      {children}
    </div>
  )
}

function Panel({
  title,
  icon: Icon,
  action,
  children,
}: {
  title: string
  icon: React.ElementType
  action?: React.ReactNode
  children: React.ReactNode
}) {
  return (
    <section className="rounded border border-white/10 bg-[#171d23]">
      <div className="flex min-h-14 items-center justify-between gap-3 border-b border-white/10 px-4 py-3">
        <h2 className="flex items-center gap-2 text-base font-semibold text-white">
          <Icon size={18} className="text-emerald-300" />
          {title}
        </h2>
        {action}
      </div>
      <div className="p-4">{children}</div>
    </section>
  )
}

function Metric({ metric }: { metric: { label: string; value: number; changePercent: number } }) {
  const isRisk = metric.label === 'Risk Score'
  const isCount = metric.label === 'Open Orders' || isRisk
  const positive = metric.changePercent >= 0
  return (
    <div className="rounded border border-white/10 bg-[#171d23] p-4">
      <div className="mb-4 flex h-9 w-9 items-center justify-center rounded bg-white/[0.06] text-emerald-300">
        {isRisk ? <Gauge size={18} /> : <Activity size={18} />}
      </div>
      <p className="text-sm text-slate-400">{metric.label}</p>
      <p className="mt-1 text-2xl font-semibold text-white">{isCount ? compactNumber(metric.value) : currency(metric.value)}</p>
      {!isRisk && metric.label !== 'Open Orders' && (
        <p className={classNames('mt-2 flex items-center gap-1 text-sm', positive ? 'text-emerald-300' : 'text-red-300')}>
          {positive ? <ArrowUpRight size={15} /> : <ArrowDownLeft size={15} />}
          {compactNumber(Math.abs(metric.changePercent))}%
        </p>
      )}
    </div>
  )
}

function TransactionsTable({ transactions }: { transactions: Dashboard['recentTransactions'] }) {
  return (
    <Panel title="Recent Transactions" icon={ClipboardList}>
      <div className="overflow-x-auto scrollbar-thin">
        <table className="data-table">
          <thead>
            <tr>
              <th>Symbol</th>
              <th>Action</th>
              <th>Price</th>
              <th>Qty</th>
              <th>Fees</th>
              <th>Time</th>
            </tr>
          </thead>
          <tbody>
            {transactions.map((transaction) => (
              <tr key={transaction.id}>
                <td className="font-semibold text-white">{transaction.symbol}</td>
                <td>{transaction.action}</td>
                <td>{currency(transaction.price)}</td>
                <td>{compactNumber(transaction.quantity)}</td>
                <td>{currency(transaction.fees)}</td>
                <td>{formatDate(transaction.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Panel>
  )
}

function OrdersTable({ orders, onCancel }: { orders: Order[]; onCancel?: (order: Order) => void }) {
  return (
    <Panel title="Orders" icon={ListFilter}>
      <div className="overflow-x-auto scrollbar-thin">
        <table className="data-table">
          <thead>
            <tr>
              <th>Symbol</th>
              <th>Side</th>
              <th>Type</th>
              <th>Status</th>
              <th>Qty</th>
              <th>Execution</th>
              <th>Created</th>
              {onCancel && <th>Action</th>}
            </tr>
          </thead>
          <tbody>
            {orders.map((order) => (
              <tr key={order.id}>
                <td className="font-semibold text-white">{order.symbol}</td>
                <td className={order.side === 'BUY' ? 'text-emerald-300' : 'text-amber-300'}>{order.side}</td>
                <td>{order.type}</td>
                <td>
                  <StatusPill status={order.status} />
                </td>
                <td>{compactNumber(order.quantity)}</td>
                <td>{order.executionPrice ? currency(order.executionPrice) : order.limitPrice ? currency(order.limitPrice) : '-'}</td>
                <td>{formatDate(order.createdAt)}</td>
                {onCancel && (
                  <td>
                    <button className="table-action" disabled={order.status !== 'PENDING'} onClick={() => onCancel(order)}>
                      <X size={14} />
                      Cancel
                    </button>
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Panel>
  )
}

function MarketBrowser({
  stocks,
  selectedSymbol,
  searchValue,
  searchPending,
  onSearchChange,
  onSearch,
  onSelect,
  onBuy,
  onSell,
}: {
  stocks: Stock[]
  selectedSymbol: string
  searchValue: string
  searchPending: boolean
  onSearchChange: (value: string) => void
  onSearch: () => void
  onSelect: (stock: Stock) => void
  onBuy: (stock: Stock) => void
  onSell: (stock: Stock) => void
}) {
  const visibleStocks = stocks.slice(0, 10)
  return (
    <Panel title="Market" icon={TrendingUp}>
      <div className="grid gap-3">
        <div className="grid grid-cols-[minmax(0,1fr)_auto] gap-2">
          <input
            className="input"
            value={searchValue}
            onChange={(event) => onSearchChange(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') {
                event.preventDefault()
                onSearch()
              }
            }}
          />
          <button className="secondary-button px-3" disabled={searchPending || searchValue.trim().length === 0} type="button" onClick={onSearch} aria-label="Search symbols">
            {searchPending ? <Loader2 className="animate-spin" size={18} /> : <Search size={18} />}
          </button>
        </div>
        <div className="grid gap-2">
          {visibleStocks.map((stock) => {
            const selected = selectedSymbol === stock.symbol
            return (
              <div
                key={stock.symbol}
                className={classNames(
                  'grid gap-2 rounded border bg-[#11161b] p-2 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center sm:gap-3',
                  selected ? 'border-emerald-400/40' : 'border-white/10',
                )}
              >
                <button className="min-w-0 text-left" type="button" onClick={() => onSelect(stock)}>
                  <span className="flex items-center gap-2">
                    <span className="text-base font-semibold text-white">{stock.symbol}</span>
                    <span className="rounded bg-white/[0.06] px-2 py-0.5 text-xs text-slate-400">{stock.sector}</span>
                  </span>
                  <span className="mt-1 block truncate text-sm text-slate-400">{stock.companyName}</span>
                </button>
                <div className="grid gap-2 sm:flex sm:items-center sm:justify-end sm:gap-3">
                  <div className="sm:text-right">
                    <p className="font-semibold tabular-nums text-white">{currency(stock.lastPrice)}</p>
                    <p className="text-xs text-emerald-300">Live quote</p>
                  </div>
                  <div className="grid grid-cols-2 gap-2 sm:flex sm:gap-2">
                    <button className="table-action w-full sm:w-auto" type="button" onClick={() => onBuy(stock)}>
                      Buy
                    </button>
                    <button className="table-action w-full sm:w-auto" type="button" onClick={() => onSell(stock)}>
                      Sell
                    </button>
                  </div>
                </div>
              </div>
            )
          })}
          {visibleStocks.length === 0 && (
            <div className="rounded border border-white/10 bg-[#11161b] p-4 text-sm text-slate-400">No symbols loaded</div>
          )}
        </div>
      </div>
    </Panel>
  )
}

function StockDetail({
  stock,
  estimatedNotional,
  buyingPower,
  estimatedCashAfterBuy,
  onBuy,
  onSell,
}: {
  stock: Stock | undefined
  estimatedNotional: number
  buyingPower: number
  estimatedCashAfterBuy: number
  onBuy: (stock: Stock) => void
  onSell: (stock: Stock) => void
}) {
  if (!stock) {
    return (
      <Panel title="Stock" icon={TrendingUp}>
        <div className="rounded border border-white/10 bg-[#11161b] p-4 text-sm text-slate-400">Search or select a symbol</div>
      </Panel>
    )
  }
  const chartData = Array.from({ length: 18 }, (_, index) => ({
    label: index,
    value: stock.lastPrice * (0.975 + index * 0.0018 + (index % 3) * 0.002),
  }))
  return (
    <Panel title="Stock" icon={TrendingUp}>
      <div className="grid gap-4">
        <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-start">
          <div>
            <div className="flex items-center gap-2">
              <h3 className="text-2xl font-semibold text-white">{stock.symbol}</h3>
              <span className="rounded bg-emerald-400/10 px-2 py-1 text-xs text-emerald-200">{stock.sector}</span>
            </div>
            <p className="mt-1 text-sm text-slate-400">{stock.companyName}</p>
          </div>
          <div className="sm:text-right">
            <p className="text-3xl font-semibold tabular-nums text-white">{currency(stock.lastPrice)}</p>
            <p className="mt-1 text-sm text-emerald-300">Live market price</p>
          </div>
        </div>
        <div className="h-44 rounded border border-white/10 bg-[#11161b] p-3">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={chartData}>
              <Line dataKey="value" stroke="#47c7a1" strokeWidth={2} dot={false} />
            </LineChart>
          </ResponsiveContainer>
        </div>
        <div className="grid gap-3 sm:grid-cols-3">
          <MiniStat label="Estimated order" value={currency(estimatedNotional)} />
          <MiniStat label="Buying power" value={currency(buyingPower)} />
          <MiniStat label="Cash after buy" value={currency(estimatedCashAfterBuy)} tone={estimatedCashAfterBuy >= 0 ? 'positive' : 'negative'} />
        </div>
        <div className="grid gap-2 sm:grid-cols-2">
          <button className="primary-button" type="button" onClick={() => onBuy(stock)}>
            <ArrowUpRight size={18} />
            Buy {stock.symbol}
          </button>
          <button className="secondary-button justify-center" type="button" onClick={() => onSell(stock)}>
            <ArrowDownLeft size={18} />
            Sell {stock.symbol}
          </button>
        </div>
      </div>
    </Panel>
  )
}

function RiskAlerts({ alerts }: { alerts: RiskAlert[] }) {
  return (
    <Panel title="Risk Alerts" icon={AlertTriangle}>
      <div className="space-y-3">
        {alerts.map((alert) => (
          <div key={alert.id} className="rounded border border-white/10 bg-[#11161b] p-4">
            <div className="flex items-center justify-between gap-3">
              <p className="font-semibold text-white">{alert.alertType}</p>
              <StatusPill status={alert.severity} />
            </div>
            <p className="mt-2 text-sm leading-6 text-slate-400">{alert.message}</p>
            <p className="mt-3 text-xs text-slate-500">{formatDate(alert.createdAt)}</p>
          </div>
        ))}
      </div>
    </Panel>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="mb-2 block text-sm font-medium text-slate-300">{label}</span>
      {children}
    </label>
  )
}

function Segmented({
  label,
  options,
  field,
}: {
  label: string
  options: [OrderSide, OrderSide] | [OrderType, OrderType]
  field: ReturnType<typeof useForm<OrderRequest>>['register'] extends (...args: never[]) => infer R ? R : never
}) {
  const name = field.name
  return (
    <fieldset>
      <legend className="mb-2 text-sm font-medium text-slate-300">{label}</legend>
      <div className="grid grid-cols-2 rounded border border-white/10 bg-[#11161b] p-1">
        {options.map((option) => (
          <label key={option} className="relative">
            <input className="peer sr-only" type="radio" value={option} {...field} defaultChecked={options[0] === option} name={name} />
            <span className="flex h-9 cursor-pointer items-center justify-center rounded text-sm text-slate-300 peer-checked:bg-emerald-400 peer-checked:font-semibold peer-checked:text-[#0d1114]">
              {option}
            </span>
          </label>
        ))}
      </div>
    </fieldset>
  )
}

function MiniStat({ label, value, tone }: { label: string; value: string; tone?: 'positive' | 'negative' }) {
  return (
    <div className="rounded border border-white/10 bg-[#11161b] p-3">
      <p className="text-sm text-slate-400">{label}</p>
      <p className={classNames('mt-1 text-lg font-semibold', tone === 'positive' ? 'text-emerald-300' : tone === 'negative' ? 'text-red-300' : 'text-white')}>{value}</p>
    </div>
  )
}

function QuoteRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded px-3 py-3">
      <span className="text-sm font-medium text-slate-400">{label}</span>
      <span className="mt-1 block text-xl font-semibold tabular-nums text-white">{value}</span>
    </div>
  )
}

function ActionNotice({ message, tone, className }: { message: string; tone: 'success' | 'warning'; className?: string }) {
  return (
    <p
      className={classNames(
        'flex items-start gap-2 rounded border px-3 py-2 text-sm',
        tone === 'success'
          ? 'border-emerald-400/30 bg-emerald-500/10 text-emerald-200'
          : 'border-amber-400/30 bg-amber-500/10 text-amber-200',
        className,
      )}
    >
      {tone === 'success' ? <CheckCircle2 className="mt-0.5 shrink-0" size={16} /> : <AlertTriangle className="mt-0.5 shrink-0" size={16} />}
      <span>{message}</span>
    </p>
  )
}

function RiskBadge({ score }: { score: number }) {
  const tone = score > 70 ? 'border-red-400/30 bg-red-500/10 text-red-200' : score > 45 ? 'border-amber-400/30 bg-amber-500/10 text-amber-200' : 'border-emerald-400/30 bg-emerald-500/10 text-emerald-200'
  return (
    <div className={classNames('inline-flex items-center gap-2 rounded border px-3 py-2 text-sm font-medium', tone)}>
      <Gauge size={16} />
      Risk Score {score}
    </div>
  )
}

function ApiModeBadge({ online, compact, className }: { online: boolean; compact?: boolean; className?: string }) {
  return (
    <div
      className={classNames(
        'inline-flex items-center gap-2 rounded border px-3 py-2 text-sm font-medium',
        online
          ? 'border-emerald-400/30 bg-emerald-500/10 text-emerald-200'
          : 'border-amber-400/30 bg-amber-500/10 text-amber-200',
        className,
      )}
      title={online ? 'Connected to the self-hosted LedgerOne API' : 'Backend API is offline; demo fallback data is active'}
    >
      {online ? <CheckCircleIcon /> : <DemoModeIcon />}
      {online ? (compact ? 'Live API' : 'Live self-hosted API') : compact ? 'Demo Mode' : 'Demo mode, no paid API required'}
    </div>
  )
}

function CheckCircleIcon() {
  return <span className="h-2.5 w-2.5 rounded-full bg-emerald-300" aria-hidden="true" />
}

function DemoModeIcon() {
  return <span className="h-2.5 w-2.5 rounded-full bg-amber-300" aria-hidden="true" />
}

function StatusPill({ status }: { status: OrderStatus | 'ACTIVE' | 'FROZEN' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL' }) {
  const tone =
    status === 'FILLED' || status === 'ACTIVE' || status === 'LOW'
      ? 'border-emerald-400/30 bg-emerald-500/10 text-emerald-200'
      : status === 'PENDING' || status === 'MEDIUM'
        ? 'border-amber-400/30 bg-amber-500/10 text-amber-200'
        : 'border-red-400/30 bg-red-500/10 text-red-200'
  return <span className={classNames('inline-flex rounded border px-2 py-1 text-xs font-medium', tone)}>{status}</span>
}

function ChartTooltip({ active, payload, label }: { active?: boolean; payload?: Array<{ value: number }>; label?: string }) {
  if (!active || !payload?.length) return null
  return (
    <div className="rounded border border-white/10 bg-[#11161b] px-3 py-2 shadow-xl">
      <p className="text-xs text-slate-400">{formatDateTime(label)}</p>
      <p className="text-sm font-semibold text-white">{currency(payload[0].value)}</p>
    </div>
  )
}

function MiniTrend() {
  const data = useMemo(() => Array.from({ length: 8 }, (_, index) => ({ index, value: 40 + Math.sin(index) * 6 + index * 2 })), [])
  return (
    <div className="h-12 w-24">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data}>
          <Line dataKey="value" stroke="#47c7a1" strokeWidth={2} dot={false} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}

function LogoMark() {
  return (
    <div className="flex h-10 w-10 items-center justify-center rounded bg-emerald-400 text-[#0d1114]">
      <Building2 size={22} />
    </div>
  )
}

export default App

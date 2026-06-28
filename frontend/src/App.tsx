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
  Lock,
  LogOut,
  PieChart as PieChartIcon,
  Plus,
  Pencil,
  RotateCcw,
  Search,
  ShieldCheck,
  Trash2,
  TrendingUp,
  WalletCards,
  X,
} from 'lucide-react'
import { useMemo, useState } from 'react'
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
import { api } from './api/client'
import { AuthProvider } from './auth/AuthContext'
import { useAuth } from './auth/auth-store'
import {
  demoAdminUsers,
  demoAuditLogs,
  demoDashboard,
  demoNotifications,
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
  Portfolio,
  RiskAlert,
  Stock,
  WatchlistItem,
} from './types'

const allocationColors = ['#47c7a1', '#e7b75f', '#6fb7d6', '#c17664', '#8abf72', '#b9a1e6']

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

function classNames(...values: Array<string | false | undefined>) {
  return values.filter(Boolean).join(' ')
}

function demoPortfolioFromName(name: string): Portfolio {
  const createdAt = new Date().toISOString()
  return {
    ...demoPortfolio,
    id: `demo-portfolio-${Date.now()}`,
    name,
    cashBalance: 50_000,
    marketValue: 0,
    totalValue: 50_000,
    realizedProfit: 0,
    unrealizedProfit: 0,
    holdings: [],
    allocation: [],
    createdAt,
    updatedAt: createdAt,
  }
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

  if (user) {
    return <Navigate to="/" replace />
  }

  return (
    <main className="min-h-screen bg-[#101317] text-slate-100">
      <div className="mx-auto grid min-h-screen w-full max-w-7xl grid-cols-1 lg:grid-cols-[0.88fr_1.12fr]">
        <section className="flex flex-col justify-between border-b border-white/10 bg-[#151a20] p-6 lg:border-b-0 lg:border-r lg:p-10">
          <div className="flex items-center gap-3">
            <LogoMark />
            <div>
              <p className="text-lg font-semibold text-white">LedgerOne</p>
              <p className="text-xs uppercase tracking-[0.24em] text-slate-400">Investment OMS</p>
            </div>
          </div>
          <div className="my-12 max-w-xl">
            <div className="mb-6 inline-flex items-center gap-2 rounded border border-emerald-400/30 bg-emerald-400/10 px-3 py-2 text-sm text-emerald-200">
              <ShieldCheck size={16} />
              Enterprise Investment Portfolio & Order Management Platform
            </div>
            <h1 className="text-4xl font-semibold leading-tight text-white sm:text-5xl">
              Secure portfolio operations with realistic order, audit, and risk workflows.
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
              setError('')
              try {
                if (mode === 'login') {
                  await login(values.email, values.password)
                } else {
                  await register(values.email, values.password, values.fullName)
                }
                navigate('/')
              } catch (exception) {
                setError(exception instanceof Error ? exception.message : 'Authentication failed')
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
                onClick={() => setMode('login')}
              >
                Sign in
              </button>
              <button
                type="button"
                className={classNames(
                  'h-10 flex-1 rounded text-sm font-medium',
                  mode === 'register' ? 'bg-emerald-400 text-[#0d1114]' : 'text-slate-300',
                )}
                onClick={() => setMode('register')}
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
            <button
              type="submit"
              className="mt-6 flex h-11 w-full items-center justify-center gap-2 rounded bg-emerald-400 px-4 font-semibold text-[#0d1114] hover:bg-emerald-300"
              disabled={formState.isSubmitting}
            >
              <Lock size={18} />
              {mode === 'login' ? 'Sign in' : 'Create account'}
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
    { to: '/portfolios', label: 'Portfolios', icon: BriefcaseBusiness },
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
            <p className="text-xs uppercase tracking-[0.18em] text-slate-500">Portfolio OMS</p>
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
  return (
    <Page title="Portfolio Overview" eyebrow={dashboard.portfolioName} action={<RiskBadge score={dashboard.riskScore} />}>
      <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-6">
        {dashboard.metrics.map((metric) => (
          <Metric key={metric.label} metric={metric} />
        ))}
      </section>
      <section className="mt-4 grid gap-4 xl:grid-cols-[1.45fr_0.55fr]">
        <Panel title="Investment Performance" icon={TrendingUp}>
          <div className="h-80">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={dashboard.performance}>
                <defs>
                  <linearGradient id="performance" x1="0" x2="0" y1="0" y2="1">
                    <stop offset="5%" stopColor="#47c7a1" stopOpacity={0.45} />
                    <stop offset="95%" stopColor="#47c7a1" stopOpacity={0.04} />
                  </linearGradient>
                </defs>
                <CartesianGrid stroke="#27313a" vertical={false} />
                <XAxis dataKey="timestamp" tickFormatter={(value) => new Date(value).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })} />
                <YAxis tickFormatter={(value) => `$${Math.round(Number(value) / 1000)}k`} width={54} />
                <Tooltip content={<ChartTooltip />} />
                <Area type="monotone" dataKey="value" stroke="#47c7a1" strokeWidth={2} fill="url(#performance)" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </Panel>
        <Panel title="Portfolio Allocation" icon={PieChartIcon}>
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
  const queryClient = useQueryClient()
  const [demoPortfolios, setDemoPortfolios] = useState<Portfolio[]>([demoPortfolio])
  const [message, setMessage] = useState('')
  const portfoliosQuery = useQuery({ queryKey: ['portfolios'], queryFn: api.portfolios, placeholderData: [demoPortfolio] })
  const portfolios = isDemoSession ? demoPortfolios : (portfoliosQuery.data ?? demoPortfolios)
  const { register, handleSubmit, reset } = useForm({ defaultValues: { name: '' } })
  const createMutation = useMutation({
    mutationFn: async ({ name }: { name: string }) => {
      if (isDemoSession) {
        const created = demoPortfolioFromName(name)
        setDemoPortfolios((current) => [created, ...current])
        return created
      }
      return api.createPortfolio(name)
    },
    onSuccess: (portfolio) => {
      reset()
      setMessage(`Portfolio "${portfolio.name}" is ready`)
      void queryClient.invalidateQueries({ queryKey: ['portfolios'] })
    },
    onError: () => setMessage('Portfolio request failed. Check API access or try demo mode.'),
  })
  const renameMutation = useMutation({
    mutationFn: async ({ id, name }: { id: string; name: string }) => {
      if (isDemoSession) {
        setDemoPortfolios((current) =>
          current.map((portfolio) => (portfolio.id === id ? { ...portfolio, name, updatedAt: new Date().toISOString() } : portfolio)),
        )
        return { id, name }
      }
      await api.renamePortfolio(id, name)
      return { id, name }
    },
    onSuccess: ({ name }) => {
      setMessage(`Portfolio renamed to "${name}"`)
      void queryClient.invalidateQueries({ queryKey: ['portfolios'] })
    },
    onError: () => setMessage('Rename failed. The API may still be blocked by CORS.'),
  })
  const deleteMutation = useMutation({
    mutationFn: async (portfolio: Portfolio) => {
      if (portfolio.holdings.length > 0) {
        throw new Error('Liquidate holdings before deleting a portfolio')
      }
      if (isDemoSession) {
        setDemoPortfolios((current) => current.filter((item) => item.id !== portfolio.id))
        return portfolio
      }
      await api.deletePortfolio(portfolio.id)
      return portfolio
    },
    onSuccess: (portfolio) => {
      setMessage(`Portfolio "${portfolio.name}" deleted`)
      void queryClient.invalidateQueries({ queryKey: ['portfolios'] })
    },
    onError: (error) => setMessage(error instanceof Error ? error.message : 'Delete failed'),
  })
  return (
    <Page title="Portfolios" eyebrow="Accounts, holdings, cost basis">
      <div className="grid gap-4 xl:grid-cols-[0.72fr_1.28fr]">
        <Panel title="Create Portfolio" icon={Plus}>
          <form className="space-y-4" onSubmit={handleSubmit((values) => createMutation.mutate(values))}>
            <Field label="Portfolio name">
              <input className="input" {...register('name', { required: true, minLength: 2 })} placeholder="Long Horizon Equity" />
            </Field>
            <button className="primary-button" type="submit" disabled={createMutation.isPending}>
              <Plus size={18} />
              Create
            </button>
            {message && <ActionNotice tone={message.includes('failed') || message.includes('Liquidate') ? 'warning' : 'success'} message={message} />}
          </form>
        </Panel>
        <div className="space-y-4">
          {portfolios.map((portfolio) => (
            <PortfolioDetail
              key={portfolio.id}
              portfolio={portfolio}
              onRename={(name) => renameMutation.mutate({ id: portfolio.id, name })}
              onDelete={() => deleteMutation.mutate(portfolio)}
              busy={renameMutation.isPending || deleteMutation.isPending}
            />
          ))}
        </div>
      </div>
    </Page>
  )
}

function PortfolioDetail({
  portfolio,
  onRename,
  onDelete,
  busy,
}: {
  portfolio: Portfolio
  onRename: (name: string) => void
  onDelete: () => void
  busy: boolean
}) {
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState(portfolio.name)
  return (
    <Panel
      title={portfolio.name}
      icon={WalletCards}
      action={
        <div className="flex items-center gap-2">
          <span className="hidden text-sm text-slate-400 sm:inline">{currency(portfolio.totalValue)}</span>
          <button className="icon-button h-9 w-9" aria-label={`Rename ${portfolio.name}`} onClick={() => setEditing((value) => !value)}>
            <Pencil size={16} />
          </button>
          <button className="icon-button h-9 w-9" aria-label={`Delete ${portfolio.name}`} disabled={busy} onClick={onDelete}>
            <Trash2 size={16} />
          </button>
        </div>
      }
    >
      {editing && (
        <form
          className="mb-4 flex flex-col gap-2 sm:flex-row"
          onSubmit={(event) => {
            event.preventDefault()
            onRename(name)
            setEditing(false)
          }}
        >
          <input
            aria-label={`New name for ${portfolio.name}`}
            className="input min-w-0 flex-1"
            value={name}
            onChange={(event) => setName(event.target.value)}
          />
          <button className="secondary-button" type="submit" disabled={busy || name.trim().length < 2}>
            Save
          </button>
        </form>
      )}
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
  const [message, setMessage] = useState('')
  const portfoliosQuery = useQuery({ queryKey: ['portfolios'], queryFn: api.portfolios, placeholderData: [demoPortfolio] })
  const stocksQuery = useQuery({ queryKey: ['stocks'], queryFn: api.stocks, placeholderData: demoStocks })
  const portfolios = portfoliosQuery.data ?? [demoPortfolio]
  const selectedPortfolioId = portfolios[0]?.id ?? demoPortfolio.id
  const ordersQuery = useQuery({
    queryKey: ['orders', selectedPortfolioId],
    queryFn: () => api.orders(selectedPortfolioId),
    placeholderData: pageOfOrders,
  })
  const stocks = stocksQuery.data ?? demoStocks
  const orders = isDemoSession ? demoOrdersState : (ordersQuery.data?.content ?? demoOrdersState)
  const { register, handleSubmit, watch, reset } = useForm<OrderRequest>({
    defaultValues: {
      portfolioId: selectedPortfolioId,
      symbol: 'AAPL',
      side: 'BUY',
      type: 'MARKET',
      quantity: 5,
      clientOrderId: `web-${Date.now()}`,
    },
  })
  const type = watch('type')
  const mutation = useMutation({
    mutationFn: async (request: OrderRequest) => {
      if (isDemoSession) {
        const portfolio = portfolios.find((item) => item.id === request.portfolioId)
        const order = demoOrderFromRequest(request, stocks, portfolio?.name ?? demoPortfolio.name)
        setDemoOrdersState((current) => [order, ...current])
        return order
      }
      return api.placeOrder(request)
    },
    onSuccess: (order) => {
      setMessage(`Order ${order.status.toLowerCase()} for ${order.symbol}`)
      reset({ portfolioId: selectedPortfolioId, symbol: 'AAPL', side: 'BUY', type: 'MARKET', quantity: 5, clientOrderId: `web-${Date.now()}` })
      void queryClient.invalidateQueries({ queryKey: ['orders'] })
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      void queryClient.invalidateQueries({ queryKey: ['portfolios'] })
    },
    onError: () => setMessage('Order request failed. Confirm API/CORS or continue in demo mode.'),
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
      void queryClient.invalidateQueries({ queryKey: ['orders'] })
    },
    onError: () => setMessage('Only pending orders can be cancelled.'),
  })
  return (
    <Page title="Trading" eyebrow="Simulated equity order management">
      <div className="grid gap-4 xl:grid-cols-[0.72fr_1.28fr]">
        <Panel title="New Order" icon={CircleDollarSign}>
          <form
            className="grid gap-4"
            onSubmit={handleSubmit((values) =>
              mutation.mutate({
                ...values,
                quantity: Number(values.quantity),
                limitPrice: values.type === 'LIMIT' ? Number(values.limitPrice) : undefined,
                clientOrderId: values.clientOrderId || `web-${Date.now()}`,
              }),
            )}
          >
            <Field label="Portfolio">
              <select className="input" {...register('portfolioId', { required: true })}>
                {portfolios.map((portfolio) => (
                  <option key={portfolio.id} value={portfolio.id}>
                    {portfolio.name}
                  </option>
                ))}
              </select>
            </Field>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Symbol">
                <select className="input" {...register('symbol', { required: true })}>
                  {stocks.map((stock: Stock) => (
                    <option key={stock.symbol} value={stock.symbol}>
                      {stock.symbol}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="Quantity">
                <input className="input" min="0.000001" step="0.000001" type="number" {...register('quantity', { required: true, min: 0.000001, valueAsNumber: true })} />
              </Field>
            </div>
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
            <button className="primary-button" disabled={mutation.isPending} type="submit">
              <ArrowUpRight size={18} />
              Submit order
            </button>
            {message && <ActionNotice tone={message.includes('failed') || message.includes('Only pending') ? 'warning' : 'success'} message={message} />}
          </form>
        </Panel>
        <OrdersTable orders={orders} onCancel={(order) => cancelMutation.mutate(order)} />
      </div>
    </Page>
  )
}

function WatchlistPage() {
  const { isDemoSession } = useAuth()
  const queryClient = useQueryClient()
  const [demoWatchlistState, setDemoWatchlistState] = useState(demoWatchlist)
  const [message, setMessage] = useState('')
  const watchlistQuery = useQuery({ queryKey: ['watchlist'], queryFn: api.watchlist, placeholderData: demoWatchlist })
  const stocksQuery = useQuery({ queryKey: ['stocks'], queryFn: api.stocks, placeholderData: demoStocks })
  const stocks = stocksQuery.data ?? demoStocks
  const watchlist = isDemoSession ? demoWatchlistState : (watchlistQuery.data ?? demoWatchlistState)
  const { register, handleSubmit, reset } = useForm({ defaultValues: { symbol: 'GOOGL' } })
  const addMutation = useMutation({
    mutationFn: async ({ symbol }: { symbol: string }) => {
      if (isDemoSession) {
        const stock = stocks.find((item) => item.symbol === symbol) ?? demoStocks[0]
        const existing = demoWatchlistState.find((item) => item.stock.symbol === symbol)
        if (existing) return existing
        const item = { id: `demo-watch-${symbol}-${Date.now()}`, stock, createdAt: new Date().toISOString() }
        setDemoWatchlistState((current) => [item, ...current])
        return item
      }
      return api.addWatchlist(symbol)
    },
    onSuccess: (item) => {
      reset({ symbol: 'GOOGL' })
      setMessage(`${item.stock.symbol} is on the watchlist`)
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
      void queryClient.invalidateQueries({ queryKey: ['watchlist'] })
    },
    onError: () => setMessage('Remove failed. Check API/CORS.'),
  })
  return (
    <Page title="Watchlist" eyebrow="Tracked equities and price movement">
      <div className="grid gap-4 xl:grid-cols-[0.62fr_1.38fr]">
        <Panel title="Add Symbol" icon={Plus}>
          <form className="space-y-4" onSubmit={handleSubmit((values) => addMutation.mutate(values))}>
            <Field label="Symbol">
              <select className="input" {...register('symbol', { required: true })}>
                {stocks.map((stock) => (
                  <option key={stock.symbol} value={stock.symbol}>
                    {stock.symbol}
                  </option>
                ))}
              </select>
            </Field>
            <button className="primary-button" type="submit">
              <Plus size={18} />
              Add
            </button>
            {message && <ActionNotice tone={message.includes('failed') ? 'warning' : 'success'} message={message} />}
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
  const [message, setMessage] = useState('')
  const queryClient = useQueryClient()
  const usersQuery = useQuery({ queryKey: ['admin-users', search], queryFn: () => api.adminUsers(search), placeholderData: demoAdminUsers })
  const ordersQuery = useQuery({ queryKey: ['admin-orders'], queryFn: api.adminOrders, placeholderData: pageOfOrders })
  const auditQuery = useQuery({ queryKey: ['audit-logs'], queryFn: api.auditLogs, placeholderData: demoAuditLogs })
  const alertsQuery = useQuery({ queryKey: ['risk-alerts'], queryFn: api.riskAlerts, placeholderData: { content: demoDashboard.riskAlerts, page: 0, size: 20, totalElements: 1, totalPages: 1, last: true } })
  const filteredDemoUsers = demoUsers.filter((user) =>
    `${user.fullName} ${user.email}`.toLowerCase().includes(search.trim().toLowerCase()),
  )
  const users = isDemoSession ? filteredDemoUsers : (usersQuery.data?.content ?? filteredDemoUsers)
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
      setMessage(`${user.fullName} is now ${user.status.toLowerCase()}`)
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
      <p className="text-xs text-slate-400">{formatDate(label)}</p>
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

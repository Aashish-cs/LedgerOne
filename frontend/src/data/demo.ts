import type {
  AdminUser,
  AppNotification,
  AuditLog,
  AuthResponse,
  Dashboard,
  Order,
  PageResponse,
  PaperAccount,
  Portfolio,
  Stock,
  WatchlistItem,
} from '../types'

const now = new Date().toISOString()
const daysAgo = (days: number) => new Date(Date.now() - days * 86_400_000).toISOString()

export const demoAuth: AuthResponse = {
  accessToken: 'demo-access-token',
  refreshToken: 'demo-refresh-token',
  tokenType: 'Bearer',
  expiresAt: new Date(Date.now() + 20 * 60_000).toISOString(),
  user: {
    id: '22222222-2222-2222-2222-222222222222',
    email: 'user@ledgerone.com',
    fullName: 'Ashish Mishra',
    status: 'ACTIVE',
    accountCashBalance: 0,
    roles: ['USER', 'ADMIN'],
    createdAt: daysAgo(30),
  },
}

export const demoStocks: Stock[] = [
  { id: 'aapl', symbol: 'AAPL', companyName: 'Apple Inc.', sector: 'Technology', lastPrice: 214.32, updatedAt: now },
  { id: 'msft', symbol: 'MSFT', companyName: 'Microsoft Corporation', sector: 'Technology', lastPrice: 486.1, updatedAt: now },
  { id: 'nvda', symbol: 'NVDA', companyName: 'NVIDIA Corporation', sector: 'Semiconductors', lastPrice: 143.85, updatedAt: now },
  { id: 'jpm', symbol: 'JPM', companyName: 'JPMorgan Chase & Co.', sector: 'Financial Services', lastPrice: 292.45, updatedAt: now },
  { id: 'amzn', symbol: 'AMZN', companyName: 'Amazon.com, Inc.', sector: 'Consumer Discretionary', lastPrice: 220.76, updatedAt: now },
  { id: 'meta', symbol: 'META', companyName: 'Meta Platforms, Inc.', sector: 'Communication Services', lastPrice: 711.64, updatedAt: now },
  { id: 'googl', symbol: 'GOOGL', companyName: 'Alphabet Inc.', sector: 'Communication Services', lastPrice: 199.17, updatedAt: now },
]

export const demoPortfolio: Portfolio = {
  id: '33333333-3333-3333-3333-333333333333',
  name: 'Paper Trading Account',
  cashBalance: 100_000,
  marketValue: 0,
  totalValue: 100_000,
  realizedProfit: 0,
  unrealizedProfit: 0,
  holdings: [],
  allocation: [],
  createdAt: daysAgo(1),
  updatedAt: now,
}

export const demoPaperAccount: PaperAccount = {
  availableCash: demoPortfolio.cashBalance,
  portfolioCash: demoPortfolio.cashBalance,
  marketValue: demoPortfolio.marketValue,
  totalEquity: demoPortfolio.totalValue,
  activePortfolioCount: 1,
}

export const demoOrders: Order[] = []

export const demoDashboard: Dashboard = {
  portfolioId: demoPortfolio.id,
  portfolioName: demoPortfolio.name,
  portfolioValue: demoPortfolio.totalValue,
  cashBalance: demoPortfolio.cashBalance,
  dailyProfit: 0,
  monthlyProfit: 0,
  totalReturn: 0,
  openOrders: 0,
  riskScore: 25,
  metrics: [
    { label: 'Account Value', value: demoPortfolio.totalValue, changePercent: 0 },
    { label: 'Cash Balance', value: demoPortfolio.cashBalance, changePercent: 100 },
    { label: 'Daily Profit', value: 0, changePercent: 0 },
    { label: 'Monthly Profit', value: 0, changePercent: 0 },
    { label: 'Open Orders', value: 0, changePercent: 0 },
    { label: 'Risk Score', value: 25, changePercent: 0 },
  ],
  allocation: demoPortfolio.allocation,
  performance: Array.from({ length: 18 }, (_, index) => ({
    timestamp: daysAgo(17 - index),
    value: 100_000,
  })),
  recentTransactions: [],
  riskAlerts: [],
}

export const demoWatchlist: WatchlistItem[] = [
  { id: 'w-meta', stock: demoStocks[5], createdAt: daysAgo(6) },
  { id: 'w-googl', stock: demoStocks[6], createdAt: daysAgo(5) },
  { id: 'w-nvda', stock: demoStocks[2], createdAt: daysAgo(2) },
]

export const demoNotifications: AppNotification[] = [
  {
    id: 'n-account-funded',
    type: 'ACCOUNT_STATUS',
    title: 'Paper account funded',
    message: 'Your paper trading account has $100,000 buying power.',
    read: false,
    createdAt: daysAgo(0.02),
  },
  {
    id: 'n-account',
    type: 'ACCOUNT_STATUS',
    title: 'Account active',
    message: 'Ashish Mishra profile is active and ready for trading.',
    read: true,
    createdAt: daysAgo(1.5),
  },
]

export const demoAdminUsers: PageResponse<AdminUser> = {
  content: [
    {
      id: '11111111-1111-1111-1111-111111111111',
      email: 'admin@ledgerone.com',
      fullName: 'LedgerOne Administrator',
      status: 'ACTIVE',
      enabled: true,
      roles: ['USER', 'ADMIN'],
      createdAt: daysAgo(45),
      updatedAt: now,
    },
    {
      id: '22222222-2222-2222-2222-222222222222',
      email: 'user@ledgerone.com',
      fullName: 'Ashish Mishra',
      status: 'ACTIVE',
      enabled: true,
      roles: ['USER'],
      createdAt: daysAgo(30),
      updatedAt: now,
    },
  ],
  page: 0,
  size: 20,
  totalElements: 2,
  totalPages: 1,
  last: true,
}

export const demoAuditLogs: PageResponse<AuditLog> = {
  content: [
    {
      id: 'audit-login',
      userId: demoAuth.user.id,
      userEmail: demoAuth.user.email,
      action: 'LOGIN',
      subject: 'Login',
      details: 'Successful login',
      ipAddress: '127.0.0.1',
      createdAt: daysAgo(0.1),
    },
    {
      id: 'audit-funded',
      userId: demoAuth.user.id,
      userEmail: demoAuth.user.email,
      action: 'PROFILE_UPDATE',
      subject: 'Paper account funded',
      details: 'Seed demo account funded with $100,000 buying power',
      ipAddress: '127.0.0.1',
      createdAt: daysAgo(1),
    },
  ],
  page: 0,
  size: 20,
  totalElements: 2,
  totalPages: 1,
  last: true,
}

export const pageOfOrders: PageResponse<Order> = {
  content: demoOrders,
  page: 0,
  size: 20,
  totalElements: demoOrders.length,
  totalPages: 1,
  last: true,
}

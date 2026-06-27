export type AccountStatus = 'ACTIVE' | 'FROZEN'
export type RoleName = 'USER' | 'ADMIN'
export type OrderSide = 'BUY' | 'SELL'
export type OrderType = 'MARKET' | 'LIMIT'
export type OrderStatus = 'PENDING' | 'FILLED' | 'CANCELLED' | 'REJECTED'
export type AlertSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
  timestamp: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}

export interface UserPrincipal {
  id: string
  email: string
  fullName: string
  status: AccountStatus
  roles: RoleName[]
  createdAt: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  tokenType: 'Bearer'
  expiresAt: string
  user: UserPrincipal
}

export interface MetricCard {
  label: string
  value: number
  changePercent: number
}

export interface AllocationSlice {
  label: string
  value: number
  percent: number
}

export interface PerformancePoint {
  timestamp: string
  value: number
}

export interface Holding {
  id: string
  symbol: string
  companyName: string
  sector: string
  quantity: number
  averageCost: number
  marketPrice: number
  marketValue: number
  unrealizedProfit: number
  realizedProfit: number
  allocationPercent: number
}

export interface Portfolio {
  id: string
  name: string
  cashBalance: number
  marketValue: number
  totalValue: number
  realizedProfit: number
  unrealizedProfit: number
  holdings: Holding[]
  allocation: AllocationSlice[]
  createdAt: string
  updatedAt: string
}

export interface Transaction {
  id: string
  orderId: string
  symbol: string
  action: 'BUY' | 'SELL' | 'FEE' | 'CASH_DEBIT' | 'CASH_CREDIT'
  price: number
  quantity: number
  fees: number
  createdAt: string
}

export interface RiskAlert {
  id: string
  portfolioId: string
  severity: AlertSeverity
  alertType: string
  message: string
  resolved: boolean
  createdAt: string
}

export interface Dashboard {
  portfolioId: string
  portfolioName: string
  portfolioValue: number
  cashBalance: number
  dailyProfit: number
  monthlyProfit: number
  totalReturn: number
  openOrders: number
  riskScore: number
  metrics: MetricCard[]
  allocation: AllocationSlice[]
  performance: PerformancePoint[]
  recentTransactions: Transaction[]
  riskAlerts: RiskAlert[]
}

export interface Stock {
  id: string
  symbol: string
  companyName: string
  sector: string
  lastPrice: number
  updatedAt: string
}

export interface Order {
  id: string
  clientOrderId: string
  portfolioId: string
  portfolioName: string
  symbol: string
  side: OrderSide
  type: OrderType
  status: OrderStatus
  quantity: number
  limitPrice?: number
  executionPrice?: number
  fees: number
  rejectionReason?: string
  createdAt: string
  filledAt?: string
}

export interface OrderRequest {
  portfolioId: string
  symbol: string
  side: OrderSide
  type: OrderType
  quantity: number
  limitPrice?: number
  clientOrderId: string
}

export interface WatchlistItem {
  id: string
  stock: Stock
  createdAt: string
}

export interface SystemStatus {
  application: string
  environment: string
  apiMode: string
  marketSimulatorEnabled: boolean
  serverTime: string
  capabilities: string[]
}

export interface AdminUser {
  id: string
  email: string
  fullName: string
  status: AccountStatus
  enabled: boolean
  roles: RoleName[]
  createdAt: string
  updatedAt: string
}

export interface AuditLog {
  id: string
  userId?: string
  userEmail?: string
  action: string
  subject: string
  details: string
  ipAddress?: string
  createdAt: string
}

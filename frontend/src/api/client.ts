import axios from 'axios'
import type {
  AdminUser,
  AppNotification,
  ApiResponse,
  AuditLog,
  AuthResponse,
  Dashboard,
  Order,
  OrderRequest,
  PageResponse,
  Portfolio,
  RiskAlert,
  Stock,
  SystemStatus,
  WatchlistItem,
} from '../types'

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api'

export const http = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('ledgerone.accessToken')
  if (token && token !== 'demo-access-token') {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

const unwrap = <T>(response: { data: ApiResponse<T> }) => {
  if (!response.data.success) {
    throw new Error(response.data.message || 'Request failed')
  }
  return response.data.data
}

export function isApiNetworkError(error: unknown) {
  return axios.isAxiosError(error) && !error.response
}

export function getApiErrorMessage(error: unknown, fallback = 'Request failed') {
  if (axios.isAxiosError(error)) {
    const payload = error.response?.data
    if (payload && typeof payload === 'object' && 'message' in payload && typeof payload.message === 'string') {
      const details = 'data' in payload ? payload.data : undefined
      if (details && typeof details === 'object') {
        const firstDetail = Object.values(details).find((value): value is string => typeof value === 'string')
        return firstDetail ? `${payload.message}: ${firstDetail}` : payload.message
      }
      return payload.message
    }
  }
  return error instanceof Error ? error.message : fallback
}

export const api = {
  login: (email: string, password: string) =>
    http.post<ApiResponse<AuthResponse>>('/auth/login', { email, password }).then(unwrap),
  register: (email: string, password: string, fullName: string) =>
    http.post<ApiResponse<AuthResponse>>('/auth/register', { email, password, fullName }).then(unwrap),
  systemStatus: () => http.get<ApiResponse<SystemStatus>>('/system/status').then(unwrap),
  logout: (refreshToken: string) => http.post('/auth/logout', { refreshToken }),
  dashboard: (portfolioId?: string) =>
    http
      .get<ApiResponse<Dashboard>>('/dashboard', { params: portfolioId ? { portfolioId } : undefined })
      .then(unwrap),
  portfolios: () => http.get<ApiResponse<Portfolio[]>>('/portfolios').then(unwrap),
  createPortfolio: (name: string) => http.post<ApiResponse<Portfolio>>('/portfolios', { name }).then(unwrap),
  renamePortfolio: (id: string, name: string) => http.put<ApiResponse<Portfolio>>(`/portfolios/${id}`, { name }).then(unwrap),
  deletePortfolio: (id: string) => http.delete(`/portfolios/${id}`),
  stocks: () => http.get<ApiResponse<Stock[]>>('/market/stocks').then(unwrap),
  searchStocks: (query: string) => http.get<ApiResponse<Stock[]>>('/market/stocks/search', { params: { query } }).then(unwrap),
  quoteStock: (symbol: string) => http.get<ApiResponse<Stock>>(`/market/stocks/${encodeURIComponent(symbol)}`).then(unwrap),
  orders: (portfolioId: string) =>
    http.get<ApiResponse<PageResponse<Order>>>(`/portfolios/${portfolioId}/orders`, { params: { size: 20 } }).then(unwrap),
  placeOrder: (request: OrderRequest) => http.post<ApiResponse<Order>>('/orders', request).then(unwrap),
  cancelOrder: (orderId: string) => http.post<ApiResponse<Order>>(`/orders/${orderId}/cancel`).then(unwrap),
  watchlist: () => http.get<ApiResponse<WatchlistItem[]>>('/watchlist').then(unwrap),
  addWatchlist: (symbol: string) => http.post<ApiResponse<WatchlistItem>>('/watchlist', { symbol }).then(unwrap),
  removeWatchlist: (id: string) => http.delete(`/watchlist/${id}`),
  notifications: () => http.get<ApiResponse<AppNotification[]>>('/notifications').then(unwrap),
  adminUsers: (search = '') =>
    http.get<ApiResponse<PageResponse<AdminUser>>>('/admin/users', { params: { search, size: 20 } }).then(unwrap),
  freezeUser: (id: string) => http.patch(`/admin/users/${id}/freeze`),
  unfreezeUser: (id: string) => http.patch(`/admin/users/${id}/unfreeze`),
  adminOrders: () => http.get<ApiResponse<PageResponse<Order>>>('/admin/orders', { params: { size: 20 } }).then(unwrap),
  auditLogs: () => http.get<ApiResponse<PageResponse<AuditLog>>>('/admin/audit-logs', { params: { size: 20 } }).then(unwrap),
  riskAlerts: () => http.get<ApiResponse<PageResponse<RiskAlert>>>('/admin/risk-alerts', { params: { size: 20 } }).then(unwrap),
}

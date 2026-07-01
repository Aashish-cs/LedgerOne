import axios, { AxiosHeaders } from 'axios'
import type { AxiosError, InternalAxiosRequestConfig } from 'axios'
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
  PaperAccount,
  Portfolio,
  RiskAlert,
  Stock,
  SystemStatus,
  WatchlistItem,
} from '../types'

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api'

const ACCESS_TOKEN_KEY = 'ledgerone.accessToken'
const REFRESH_TOKEN_KEY = 'ledgerone.refreshToken'
const USER_KEY = 'ledgerone.user'
const DEMO_ACCESS_TOKEN = 'demo-access-token'
const DEMO_REFRESH_TOKEN = 'demo-refresh-token'

type RetryRequestConfig = InternalAxiosRequestConfig & {
  _retry?: boolean
}

export const http = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
})

const unwrap = <T>(response: { data: ApiResponse<T> }) => {
  if (!response.data.success) {
    throw new Error(response.data.message || 'Request failed')
  }
  return response.data.data
}

export const persistAuthSession = (auth: AuthResponse) => {
  localStorage.setItem(ACCESS_TOKEN_KEY, auth.accessToken)
  localStorage.setItem(REFRESH_TOKEN_KEY, auth.refreshToken)
  localStorage.setItem(USER_KEY, JSON.stringify(auth.user))
}

export const clearAuthSession = () => {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

let refreshPromise: Promise<AuthResponse> | null = null

const refreshAuthSession = async () => {
  const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY)
  if (!refreshToken || refreshToken === DEMO_REFRESH_TOKEN) {
    throw new Error('Session expired. Please sign in again.')
  }

  refreshPromise ??= axios
    .post<ApiResponse<AuthResponse>>(
      `${API_BASE_URL}/auth/refresh`,
      { refreshToken },
      {
        headers: {
          'Content-Type': 'application/json',
        },
      },
    )
    .then(unwrap)
    .then((auth) => {
      persistAuthSession(auth)
      window.dispatchEvent(new CustomEvent('ledgerone:auth-refreshed', { detail: auth.user }))
      return auth
    })
    .finally(() => {
      refreshPromise = null
    })

  return refreshPromise
}

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(ACCESS_TOKEN_KEY)
  if (token && token !== DEMO_ACCESS_TOKEN) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const status = error.response?.status
    const originalRequest = error.config as RetryRequestConfig | undefined
    const shouldRefresh = status === 401 || status === 403

    if (!shouldRefresh || !originalRequest || originalRequest._retry) {
      return Promise.reject(error)
    }

    const currentAccessToken = localStorage.getItem(ACCESS_TOKEN_KEY)
    if (!currentAccessToken || currentAccessToken === DEMO_ACCESS_TOKEN) {
      return Promise.reject(error)
    }

    originalRequest._retry = true

    try {
      const auth = await refreshAuthSession()
      originalRequest.headers = AxiosHeaders.from(originalRequest.headers)
      originalRequest.headers.set('Authorization', `Bearer ${auth.accessToken}`)
      return http(originalRequest)
    } catch (refreshError) {
      clearAuthSession()
      window.dispatchEvent(new Event('ledgerone:auth-expired'))
      return Promise.reject(refreshError)
    }
  }
)

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
  accountSummary: () => http.get<ApiResponse<PaperAccount>>('/account/summary').then(unwrap),
  portfolios: () => http.get<ApiResponse<Portfolio[]>>('/portfolios').then(unwrap),
  createPortfolio: (name: string, initialAllocation: number) =>
    http.post<ApiResponse<Portfolio>>('/portfolios', { name, initialAllocation }).then(unwrap),
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

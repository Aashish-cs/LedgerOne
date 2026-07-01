import { useEffect, useMemo, useState } from 'react'
import { api, clearAuthSession, getApiErrorMessage, isApiNetworkError, persistAuthSession } from '../api/client'
import { demoAuth } from '../data/demo'
import { AuthContext } from './auth-store'
import type { AuthContextValue } from './auth-store'
import type { AuthResponse, UserPrincipal } from '../types'

const readUser = () => {
  const raw = localStorage.getItem('ledgerone.user')
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as UserPrincipal
  } catch {
    clearAuthSession()
    return null
  }
}

const demoLogin = (email: string, password: string) => {
  const validUser = email === 'user@ledgerone.com' && password === 'User123!'
  const validAdmin = email === 'admin@ledgerone.com' && password === 'Admin123!'
  if (!validUser && !validAdmin) {
    throw new Error('Invalid credentials')
  }
  return {
    ...demoAuth,
    user: validAdmin
      ? { ...demoAuth.user, id: '11111111-1111-1111-1111-111111111111', email, fullName: 'LedgerOne Administrator', roles: ['USER', 'ADMIN'] }
      : { ...demoAuth.user, email, roles: ['USER'] },
  } satisfies AuthResponse
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<UserPrincipal | null>(readUser)
  const isDemoSession = localStorage.getItem('ledgerone.accessToken') === 'demo-access-token'

  useEffect(() => {
    const syncUser = () => setUser(readUser())
    const expireUser = () => setUser(null)

    window.addEventListener('ledgerone:auth-refreshed', syncUser)
    window.addEventListener('ledgerone:auth-expired', expireUser)
    window.addEventListener('storage', syncUser)

    return () => {
      window.removeEventListener('ledgerone:auth-refreshed', syncUser)
      window.removeEventListener('ledgerone:auth-expired', expireUser)
      window.removeEventListener('storage', syncUser)
    }
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isAdmin: Boolean(user?.roles.includes('ADMIN')),
      isDemoSession,
      login: async (email, password) => {
        let auth: AuthResponse
        try {
          auth = await api.login(email, password)
        } catch (error) {
          if (!isApiNetworkError(error)) {
            throw new Error(getApiErrorMessage(error, 'Sign in failed'))
          }
          auth = demoLogin(email, password)
        }
        persistAuthSession(auth)
        setUser(auth.user)
      },
      register: async (email, password, fullName) => {
        let auth: AuthResponse
        try {
          auth = await api.register(email, password, fullName)
        } catch (error) {
          if (!isApiNetworkError(error)) {
            throw new Error(getApiErrorMessage(error, 'Create account failed'))
          }
          auth = { ...demoAuth, user: { ...demoAuth.user, email, fullName, roles: ['USER'] } }
        }
        persistAuthSession(auth)
        setUser(auth.user)
      },
      logout: async () => {
        const refreshToken = localStorage.getItem('ledgerone.refreshToken')
        if (refreshToken) {
          await api.logout(refreshToken).catch(() => undefined)
        }
        clearAuthSession()
        setUser(null)
      },
    }),
    [isDemoSession, user],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

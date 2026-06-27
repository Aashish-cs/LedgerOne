import { useMemo, useState } from 'react'
import { api } from '../api/client'
import { demoAuth } from '../data/demo'
import { AuthContext } from './auth-store'
import type { AuthContextValue } from './auth-store'
import type { AuthResponse, UserPrincipal } from '../types'

const readUser = () => {
  const raw = localStorage.getItem('ledgerone.user')
  return raw ? (JSON.parse(raw) as UserPrincipal) : null
}

const persist = (auth: AuthResponse) => {
  localStorage.setItem('ledgerone.accessToken', auth.accessToken)
  localStorage.setItem('ledgerone.refreshToken', auth.refreshToken)
  localStorage.setItem('ledgerone.user', JSON.stringify(auth.user))
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

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isAdmin: Boolean(user?.roles.includes('ADMIN')),
      login: async (email, password) => {
        let auth: AuthResponse
        try {
          auth = await api.login(email, password)
        } catch {
          auth = demoLogin(email, password)
        }
        persist(auth)
        setUser(auth.user)
      },
      register: async (email, password, fullName) => {
        let auth: AuthResponse
        try {
          auth = await api.register(email, password, fullName)
        } catch {
          auth = { ...demoAuth, user: { ...demoAuth.user, email, fullName, roles: ['USER'] } }
        }
        persist(auth)
        setUser(auth.user)
      },
      logout: async () => {
        const refreshToken = localStorage.getItem('ledgerone.refreshToken')
        if (refreshToken) {
          await api.logout(refreshToken).catch(() => undefined)
        }
        localStorage.removeItem('ledgerone.accessToken')
        localStorage.removeItem('ledgerone.refreshToken')
        localStorage.removeItem('ledgerone.user')
        setUser(null)
      },
    }),
    [user],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Login from './Login'

const apiMock = vi.hoisted(() => ({
  login: vi.fn(),
  exchangeGoogleLogin: vi.fn(),
}))

vi.mock('../api/sonograma', () => ({
  api: apiMock,
  googleAuthorizationUrl: '/api/oauth2/authorization/google',
}))

vi.mock('../context/useTheme', () => ({
  useTheme: () => ({ dark: false, toggle: vi.fn() }),
}))

function renderLogin(initialEntry = '/login') {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/" element={<div>Panel Sonograma</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('Login', () => {
  beforeEach(() => {
    localStorage.clear()
    apiMock.login.mockReset()
    apiMock.exchangeGoogleLogin.mockReset()
  })

  it('keeps the username/password form and Google option visible', () => {
    renderLogin()

    expect(screen.getByLabelText('Usuario')).toBeVisible()
    expect(screen.getByLabelText('Contraseña')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Ingresar' })).toBeVisible()
    expect(screen.getByRole('link', { name: /Ingresar con Google/i }))
      .toHaveAttribute('href', '/api/oauth2/authorization/google')
  })

  it('preserves the existing password login behavior', async () => {
    apiMock.login.mockResolvedValue({
      token: 'password-jwt',
      usuario: { nombreUsuario: 'admin', rol: 'ADMIN' },
    })
    renderLogin()

    fireEvent.change(screen.getByLabelText('Usuario'), { target: { value: 'admin' } })
    fireEvent.change(screen.getByLabelText('Contraseña'), { target: { value: 'admin123' } })
    fireEvent.click(screen.getByRole('button', { name: 'Ingresar' }))

    await screen.findByText('Panel Sonograma')
    expect(apiMock.login).toHaveBeenCalledWith('admin', 'admin123')
    expect(localStorage.getItem('token')).toBe('password-jwt')
  })

  it('exchanges a one-time Google code and reaches the dashboard', async () => {
    apiMock.exchangeGoogleLogin.mockResolvedValue({
      token: 'google-jwt',
      usuario: { nombreUsuario: 'admin', rol: 'ADMIN' },
    })
    renderLogin('/login?oauth_code=single-use-code')

    await screen.findByText('Panel Sonograma')
    expect(apiMock.exchangeGoogleLogin).toHaveBeenCalledWith('single-use-code')
    expect(localStorage.getItem('token')).toBe('google-jwt')
  })

  it('returns Google failures to the login page with a safe message', async () => {
    apiMock.exchangeGoogleLogin.mockRejectedValue(
      new Error('Esta cuenta de Google no está autorizada para ingresar a Sonograma.'),
    )
    renderLogin('/login?oauth_code=denied-code')

    expect(await screen.findByText(
      'Esta cuenta de Google no está autorizada para ingresar a Sonograma.',
    )).toBeVisible()
    await waitFor(() => expect(localStorage.getItem('token')).toBeNull())
  })

  it('explains missing OAuth configuration without exposing details', async () => {
    renderLogin('/login?oauth_error=configuration')

    expect(await screen.findByText(
      'El ingreso con Google todavía no está configurado. Usá tu usuario y contraseña.',
    )).toBeVisible()
    expect(apiMock.exchangeGoogleLogin).not.toHaveBeenCalled()
  })
})

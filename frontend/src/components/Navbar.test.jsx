import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import Navbar from './Navbar'

vi.mock('../context/useTheme', () => ({
  useTheme: () => ({ dark: false, toggle: vi.fn() }),
}))

describe('Navbar logout', () => {
  it('keeps the current logout behavior for either authentication method', () => {
    localStorage.setItem('token', 'jwt')
    localStorage.setItem('usuario', JSON.stringify({ nombreUsuario: 'admin' }))

    render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<Navbar usuario={{ nombreUsuario: 'admin' }} />} />
          <Route path="/login" element={<div>Login Sonograma</div>} />
        </Routes>
      </MemoryRouter>,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Salir' }))

    expect(screen.getByText('Login Sonograma')).toBeVisible()
    expect(localStorage.getItem('token')).toBeNull()
    expect(localStorage.getItem('usuario')).toBeNull()
  })
})

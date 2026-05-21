import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api/sonograma'
import { useTheme } from '../context/useTheme'

function SunIcon() {
  return (
    <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 3v2.25m6.364.386-1.591 1.591M21 12h-2.25m-.386 6.364-1.591-1.591M12 18.75V21m-4.773-4.227-1.591 1.591M5.25 12H3m4.227-4.773L5.636 5.636M15.75 12a3.75 3.75 0 1 1-7.5 0 3.75 3.75 0 0 1 7.5 0Z" />
    </svg>
  )
}

function MoonIcon() {
  return (
    <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M21.752 15.002A9.72 9.72 0 0 1 18 15.75c-5.385 0-9.75-4.365-9.75-9.75 0-1.33.266-2.597.748-3.752A9.753 9.753 0 0 0 3 11.25C3 16.635 7.365 21 12.75 21a9.753 9.753 0 0 0 9.002-5.998Z" />
    </svg>
  )
}

export default function Login() {
  const navigate = useNavigate()
  const { dark, toggle } = useTheme()
  const [form, setForm] = useState({ nombreUsuario: '', contrasenia: '' })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const data = await api.login(form.nombreUsuario, form.contrasenia)
      localStorage.setItem('token', data.token)
      localStorage.setItem('usuario', JSON.stringify(data.usuario))
      navigate('/')
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-white dark:bg-black flex items-center justify-center px-4 transition-colors duration-300">

      <button
        onClick={toggle}
        className="fixed top-4 right-4 p-2 rounded-lg text-slate-500 dark:text-stone-400 hover:bg-slate-100 dark:hover:bg-stone-900 transition-colors"
        title="Cambiar tema"
      >
        {dark ? <SunIcon /> : <MoonIcon />}
      </button>

      <div className="w-full max-w-sm">

        <div className="text-center mb-8">
          <img
            src="/logo-sonograma.png"
            alt="Sonograma"
            className="h-16 w-16 object-contain dark:invert mx-auto mb-4 transition-all"
          />
          <h1 className="text-3xl font-bold text-slate-900 dark:text-white tracking-tight">Sonograma</h1>
          <p className="text-[10px] uppercase tracking-[0.18em] text-stone-500 dark:text-stone-400 mt-1">Disquería</p>
        </div>

        <div className="card p-8">
          <form onSubmit={handleSubmit} className="space-y-5">
            <div>
              <label className="block text-sm font-medium text-slate-700 dark:text-stone-300 mb-1.5">Usuario</label>
              <input
                type="text"
                value={form.nombreUsuario}
                onChange={e => setForm({ ...form, nombreUsuario: e.target.value })}
                className="input"
                placeholder="tu usuario"
                autoComplete="username"
                required
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-700 dark:text-stone-300 mb-1.5">Contraseña</label>
              <input
                type="password"
                value={form.contrasenia}
                onChange={e => setForm({ ...form, contrasenia: e.target.value })}
                className="input"
                placeholder="••••••••"
                autoComplete="current-password"
                required
              />
            </div>

            {error && (
              <div className="bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-800 text-red-600 dark:text-red-300 text-sm rounded-lg px-4 py-3">
                {error}
              </div>
            )}

            <button type="submit" disabled={loading} className="btn-primary w-full py-2.5 mt-1">
              {loading ? (
                <span className="flex items-center justify-center gap-2">
                  <svg className="animate-spin w-4 h-4" viewBox="0 0 24 24" fill="none">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/>
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/>
                  </svg>
                  Ingresando...
                </span>
              ) : 'Ingresar'}
            </button>
          </form>
        </div>

        <p className="text-center text-xs text-slate-400 dark:text-stone-600 mt-6">
          Sonograma v1.0 · Gestión de inventario
        </p>
      </div>
    </div>
  )
}

import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { api, googleAuthorizationUrl } from '../api/sonograma'
import { useTheme } from '../context/useTheme'

const OAUTH_ERRORS = {
  configuration: 'El ingreso con Google todavía no está configurado. Usá tu usuario y contraseña.',
}

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
  const location = useLocation()
  const { dark, toggle } = useTheme()
  const [form, setForm] = useState({ nombreUsuario: '', contrasenia: '' })
  const [error, setError] = useState(() => {
    const oauthError = new URLSearchParams(location.search).get('oauth_error')
    if (!oauthError) return ''
    return OAUTH_ERRORS[oauthError]
      || 'No se pudo completar el ingreso con Google. Intentá nuevamente.'
  })
  const [loading, setLoading] = useState(false)
  const [googleLoading, setGoogleLoading] = useState(() =>
    new URLSearchParams(location.search).has('oauth_code'))
  const handledGoogleCode = useRef(null)

  useEffect(() => {
    const params = new URLSearchParams(location.search)
    const configurationError = params.get('oauth_error')
    const handoffCode = params.get('oauth_code')

    if (configurationError) {
      navigate('/login', { replace: true })
      return
    }
    if (!handoffCode || handledGoogleCode.current === handoffCode) return

    handledGoogleCode.current = handoffCode
    navigate('/login', { replace: true })

    api.exchangeGoogleLogin(handoffCode)
      .then(data => {
        if (!data?.token || !data?.usuario) {
          throw new Error('El servidor no devolvió una sesión válida')
        }
        localStorage.setItem('token', data.token)
        localStorage.setItem('usuario', JSON.stringify(data.usuario))
        navigate('/', { replace: true })
      })
      .catch(err => setError(err.message))
      .finally(() => setGoogleLoading(false))
  }, [location.search, navigate])

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const data = await api.login(form.nombreUsuario.trim(), form.contrasenia)
      if (!data?.token || !data?.usuario) {
        throw new Error('El servidor no devolvió una sesión válida')
      }
      localStorage.setItem('token', data.token)
      localStorage.setItem('usuario', JSON.stringify(data.usuario))
      navigate('/', { replace: true })
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
            className="h-32 w-32 sm:h-40 sm:w-40 object-contain dark:invert mx-auto mb-3 transition-all"
          />
          <h1 className="text-3xl font-bold text-slate-900 dark:text-white tracking-tight">Sonograma</h1>
          <p className="text-[10px] uppercase tracking-[0.18em] text-stone-500 dark:text-stone-400 mt-1">Disquería</p>
        </div>

        <div className="card p-8">
          <form onSubmit={handleSubmit} className="space-y-5">
            <div>
              <label htmlFor="login-username" className="block text-sm font-medium text-slate-700 dark:text-stone-300 mb-1.5">Usuario</label>
              <input
                id="login-username"
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
              <label htmlFor="login-password" className="block text-sm font-medium text-slate-700 dark:text-stone-300 mb-1.5">Contraseña</label>
              <input
                id="login-password"
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

            <div className="flex items-center gap-3" aria-hidden="true">
              <span className="h-px flex-1 bg-slate-200 dark:bg-stone-700" />
              <span className="text-xs text-slate-400 dark:text-stone-500">o</span>
              <span className="h-px flex-1 bg-slate-200 dark:bg-stone-700" />
            </div>

            <a
              href={googleAuthorizationUrl}
              aria-disabled={googleLoading}
              className={`w-full py-2.5 px-4 rounded-lg border border-slate-200 dark:border-stone-700 bg-white dark:bg-stone-900 text-slate-700 dark:text-stone-200 text-sm font-semibold flex items-center justify-center gap-3 transition-colors hover:bg-slate-50 dark:hover:bg-stone-800 ${googleLoading ? 'pointer-events-none opacity-60' : ''}`}
            >
              <svg aria-hidden="true" className="w-4 h-4" viewBox="0 0 18 18">
                <path fill="#4285F4" d="M17.64 9.205c0-.638-.057-1.252-.164-1.841H9v3.482h4.844a4.14 4.14 0 0 1-1.797 2.715v2.258h2.909c1.702-1.567 2.684-3.874 2.684-6.614Z" />
                <path fill="#34A853" d="M9 18c2.43 0 4.467-.806 5.956-2.181l-2.909-2.258c-.806.54-1.835.859-3.047.859-2.344 0-4.328-1.585-5.037-3.714H.956v2.332A9 9 0 0 0 9 18Z" />
                <path fill="#FBBC05" d="M3.963 10.706A5.41 5.41 0 0 1 3.682 9c0-.592.102-1.168.281-1.706V4.962H.956A9 9 0 0 0 0 9c0 1.452.347 2.827.956 4.038l3.007-2.332Z" />
                <path fill="#EA4335" d="M9 3.58c1.321 0 2.507.454 3.441 1.346l2.581-2.581C13.463.892 11.426 0 9 0A9 9 0 0 0 .956 4.962l3.007 2.332C4.672 5.165 6.656 3.58 9 3.58Z" />
              </svg>
              {googleLoading ? 'Completando ingreso…' : 'Ingresar con Google'}
            </a>
          </form>
        </div>

        <p className="text-center text-xs text-slate-400 dark:text-stone-600 mt-6">
          Sonograma v1.0 · Gestión de inventario
        </p>
      </div>
    </div>
  )
}

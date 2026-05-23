import { NavLink, useNavigate } from 'react-router-dom'
import { useTheme } from '../context/useTheme'

function SunIcon() {
  return (
    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 3v2.25m6.364.386-1.591 1.591M21 12h-2.25m-.386 6.364-1.591-1.591M12 18.75V21m-4.773-4.227-1.591 1.591M5.25 12H3m4.227-4.773L5.636 5.636M15.75 12a3.75 3.75 0 1 1-7.5 0 3.75 3.75 0 0 1 7.5 0Z" />
    </svg>
  )
}

function MoonIcon() {
  return (
    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M21.752 15.002A9.72 9.72 0 0 1 18 15.75c-5.385 0-9.75-4.365-9.75-9.75 0-1.33.266-2.597.748-3.752A9.753 9.753 0 0 0 3 11.25C3 16.635 7.365 21 12.75 21a9.753 9.753 0 0 0 9.002-5.998Z" />
    </svg>
  )
}

function LogoutIcon() {
  return (
    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M8.25 9V5.25A2.25 2.25 0 0 1 10.5 3h6a2.25 2.25 0 0 1 2.25 2.25v13.5A2.25 2.25 0 0 1 16.5 21h-6a2.25 2.25 0 0 1-2.25-2.25V15m-3 0-3-3m0 0 3-3m-3 3H15" />
    </svg>
  )
}

const navLinkClass = ({ isActive }) =>
  `text-sm font-medium px-3 py-1.5 rounded-lg transition-colors ${
    isActive
      ? 'bg-[#7E9FA8]/15 text-[#5C7D87] dark:text-[#7E9FA8]'
      : 'text-slate-600 dark:text-stone-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-stone-900'
  }`

export default function Navbar({ usuario }) {
  const navigate = useNavigate()
  const { dark, toggle } = useTheme()

  function logout() {
    localStorage.removeItem('token')
    localStorage.removeItem('usuario')
    navigate('/login')
  }

  const initials = usuario?.nombreUsuario
    ? usuario.nombreUsuario.slice(0, 2).toUpperCase()
    : '?'

  return (
    <nav className="bg-white dark:bg-black border-b border-slate-200 dark:border-stone-800 px-6 py-0 transition-colors duration-300 sticky top-0 z-40">
      <div className="max-w-6xl mx-auto flex items-center justify-between h-14 gap-4">

        {/* Izquierda: logo + navegación */}
        <div className="flex items-center gap-6">
          <div className="flex items-center">
            <img
              src="/logo.png"
              alt="Sonograma"
              className="h-10 w-auto object-contain dark:invert"
            />
          </div>

          <div className="flex items-center gap-1">
            <NavLink to="/" end className={navLinkClass}>Inventario</NavLink>
            <NavLink to="/discos" className={navLinkClass}>Catálogo</NavLink>
            <NavLink to="/clientes" className={navLinkClass}>Clientes</NavLink>
            <NavLink to="/ventas/nueva" className={navLinkClass}>Nueva venta</NavLink>
            <NavLink to="/importar" className={navLinkClass}>Importar</NavLink>
          </div>
        </div>

        {/* Right side */}
        <div className="flex items-center gap-2">
          <button
            onClick={toggle}
            className="p-2 rounded-lg text-slate-500 dark:text-stone-400 hover:bg-slate-100 dark:hover:bg-stone-900 transition-colors"
            title={dark ? 'Modo claro' : 'Modo oscuro'}
          >
            {dark ? <SunIcon /> : <MoonIcon />}
          </button>

          <div className="w-px h-5 bg-slate-200 dark:bg-stone-800 mx-1" />

          <div className="flex items-center gap-2.5">
            <div className="w-7 h-7 rounded-full bg-[#7E9FA8]/20 dark:bg-[#7E9FA8]/20 flex items-center justify-center">
              <span className="text-[#5C7D87] dark:text-[#7E9FA8] text-xs font-bold">{initials}</span>
            </div>
            <div className="hidden sm:block">
              <div className="text-slate-700 dark:text-stone-300 text-sm font-medium leading-none">{usuario?.nombreUsuario}</div>
              <div className="text-[#7E9FA8] text-xs mt-0.5">{usuario?.rol}</div>
            </div>
          </div>

          <button
            onClick={logout}
            className="ml-1 flex items-center gap-1.5 text-slate-400 dark:text-stone-500 hover:text-red-500 dark:hover:text-red-400 text-sm transition-colors px-2 py-1.5 rounded-lg hover:bg-red-50 dark:hover:bg-red-900/20"
            title="Cerrar sesión"
          >
            <LogoutIcon />
            <span className="hidden sm:inline">Salir</span>
          </button>
        </div>
      </div>
    </nav>
  )
}

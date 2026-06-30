import { NavLink, Outlet } from 'react-router-dom'

const tabClass = ({ isActive }) =>
  `px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
    isActive
      ? 'bg-[#7E9FA8] text-white'
      : 'text-slate-600 dark:text-stone-400 hover:bg-slate-100 dark:hover:bg-stone-900 hover:text-slate-900 dark:hover:text-white'
  }`

export default function StockLayout() {
  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 py-6 space-y-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-xl font-bold text-slate-900 dark:text-white">Stock</h1>
          <p className="text-sm text-slate-500 dark:text-stone-500 mt-1">
            Gestión de catálogo, stock y pricing.
          </p>
        </div>
        <div className="flex items-center gap-2 rounded-xl border border-slate-200 dark:border-stone-800 p-1 bg-white dark:bg-stone-950 w-fit">
          <NavLink to="/stock/catalogo" className={tabClass}>Catálogo</NavLink>
          <NavLink to="/stock/pricing" className={tabClass}>Pricing</NavLink>
        </div>
      </div>
      <Outlet />
    </div>
  )
}

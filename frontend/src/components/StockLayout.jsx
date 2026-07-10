import { Outlet } from 'react-router-dom'

export default function StockLayout() {
  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 py-6 space-y-5">
      <div>
        <div>
          <h1 className="text-xl font-bold text-slate-900 dark:text-white">Gestión de stock</h1>
          <p className="text-sm text-slate-500 dark:text-stone-500 mt-1">
            Gestión de stock, costos y precios.
          </p>
        </div>
      </div>
      <Outlet />
    </div>
  )
}

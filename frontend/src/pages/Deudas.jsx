export default function Deudas() {
  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 py-6 space-y-5">

      {/* Header */}
      <div className="flex items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-900 dark:text-white">Deudas</h1>
          <p className="text-slate-400 dark:text-stone-500 text-sm mt-0.5">Cuentas corrientes y pagos pendientes</p>
        </div>
      </div>

      {/* Summary cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        {[
          { label: 'Total adeudado',     value: '$0',  desc: 'Suma de todas las deudas activas' },
          { label: 'Clientes con deuda', value: '0',   desc: 'Clientes con saldo pendiente' },
          { label: 'Pagos pendientes',   value: '0',   desc: 'Cuotas o pagos por confirmar' },
        ].map(({ label, value, desc }) => (
          <div key={label} className="card p-5">
            <p className="text-xs uppercase tracking-wider text-slate-400 dark:text-stone-500 mb-1">{label}</p>
            <p className="text-2xl font-bold text-slate-900 dark:text-white tabular-nums">{value}</p>
            <p className="text-xs text-slate-400 dark:text-stone-500 mt-1">{desc}</p>
          </div>
        ))}
      </div>

      {/* Search placeholder */}
      <div className="relative max-w-sm">
        <svg className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-300 dark:text-stone-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="m21 21-5.197-5.197m0 0A7.5 7.5 0 1 0 5.196 5.196a7.5 7.5 0 0 0 10.607 10.607Z" />
        </svg>
        <input
          disabled
          placeholder="Buscar cliente o deuda… (próximamente)"
          className="input pl-9 opacity-50 cursor-not-allowed w-full"
        />
      </div>

      {/* Table placeholder */}
      <div className="card overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-100 dark:border-stone-800">
                {['Cliente', 'Deuda total', 'Última venta', 'Estado', 'Acciones'].map(h => (
                  <th key={h} className="text-left px-5 py-3 text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              <tr>
                <td colSpan={5} className="px-5 py-16 text-center text-slate-400 dark:text-stone-500 text-sm">
                  No hay deudas registradas
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      {/* Note */}
      <p className="text-xs text-slate-400 dark:text-stone-500 italic text-center">
        Módulo de cuentas corrientes — en desarrollo
      </p>
    </div>
  )
}

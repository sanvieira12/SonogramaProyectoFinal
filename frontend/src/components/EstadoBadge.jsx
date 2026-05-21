const ESTILOS = {
  DISPONIBLE:    'bg-emerald-50 text-emerald-700 dark:bg-emerald-900/20 dark:text-emerald-400',
  RESERVADO:     'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400',
  VENDIDO:       'bg-slate-100 text-slate-500 dark:bg-stone-800 dark:text-stone-400',
  FUERA_STOCK:   'bg-orange-100 text-orange-700 dark:bg-orange-900/25 dark:text-orange-400',
  DESCONTINUADO: 'bg-red-100 text-red-600 dark:bg-red-900/30 dark:text-red-400',
}

const DOTS = {
  DISPONIBLE:    'bg-emerald-500',
  RESERVADO:     'bg-amber-500',
  VENDIDO:       'bg-slate-400',
  FUERA_STOCK:   'bg-orange-500',
  DESCONTINUADO: 'bg-red-500',
}

const LABELS = {
  DISPONIBLE: 'Disponible',
  RESERVADO: 'Reservado',
  VENDIDO: 'Vendido',
  FUERA_STOCK: 'Fuera de stock',
  DESCONTINUADO: 'Descontinuado',
}

export default function EstadoBadge({ estado }) {
  const estilos = ESTILOS[estado] || ESTILOS.DESCONTINUADO
  const dot = DOTS[estado] || DOTS.DESCONTINUADO
  const label = LABELS[estado] || (estado ? estado.charAt(0) + estado.slice(1).toLowerCase() : '—')

  return (
    <span className={`inline-flex items-center gap-1.5 text-xs px-2.5 py-1 rounded-full font-medium ${estilos}`}>
      <span className={`w-1.5 h-1.5 rounded-full flex-shrink-0 ${dot}`} />
      {label}
    </span>
  )
}

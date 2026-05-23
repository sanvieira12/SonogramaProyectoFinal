import { useMemo } from 'react'

// Para paginación server-side futura: reemplazar el slice() en el componente
// padre por una llamada a la API con ?page=(pagina-1)&size=porPagina y usar el
// total que devuelve el backend en lugar de array.length.
export default function Paginacion({ total, porPagina, pagina, onPagina, onPorPagina }) {
  const totalPaginas = Math.max(1, Math.ceil(total / porPagina))
  const inicio = total === 0 ? 0 : (pagina - 1) * porPagina + 1
  const fin = Math.min(pagina * porPagina, total)

  const paginas = useMemo(() => {
    if (totalPaginas <= 5) return Array.from({ length: totalPaginas }, (_, i) => i + 1)
    let start = Math.max(1, pagina - 2)
    let end = start + 4
    if (end > totalPaginas) { end = totalPaginas; start = Math.max(1, end - 4) }
    const pages = []
    if (start > 1) { pages.push(1); if (start > 2) pages.push('...') }
    for (let i = start; i <= end; i++) pages.push(i)
    if (end < totalPaginas) { if (end < totalPaginas - 1) pages.push('...'); pages.push(totalPaginas) }
    return pages
  }, [pagina, totalPaginas])

  if (total === 0) return null

  const btnNav = 'px-3 py-1.5 rounded-lg text-sm font-medium text-slate-600 dark:text-stone-400 hover:bg-slate-100 dark:hover:bg-stone-800 disabled:opacity-40 disabled:cursor-not-allowed transition-colors'

  return (
    <div className="flex flex-col sm:flex-row items-center justify-between gap-3 px-1 pt-3">
      {/* Info + selector de cantidad */}
      <div className="flex items-center gap-3 text-sm text-slate-500 dark:text-stone-400">
        <span className="hidden sm:inline">Mostrando {inicio}–{fin} de {total} registros</span>
        <select
          value={porPagina}
          onChange={e => onPorPagina(Number(e.target.value))}
          className="input w-auto py-1 text-xs"
        >
          {[10, 20, 50].map(n => <option key={n} value={n}>{n} por página</option>)}
        </select>
      </div>

      {/* Navegación entre páginas */}
      <div className="flex items-center gap-1">
        <button onClick={() => onPagina(pagina - 1)} disabled={pagina === 1} className={btnNav}>
          Anterior
        </button>

        {/* Desktop: números de página con ellipsis */}
        <div className="hidden sm:flex items-center gap-1">
          {paginas.map((p, i) =>
            p === '...' ? (
              <span key={`dots-${i}`} className="px-2 text-slate-400 dark:text-stone-600 text-sm select-none">…</span>
            ) : (
              <button
                key={p}
                onClick={() => onPagina(p)}
                className={`min-w-[32px] h-8 px-2 rounded-lg text-sm font-medium transition-colors ${
                  p === pagina
                    ? 'bg-indigo-600 text-white'
                    : 'text-slate-600 dark:text-stone-400 hover:bg-slate-100 dark:hover:bg-stone-800'
                }`}
              >
                {p}
              </button>
            )
          )}
        </div>

        {/* Mobile: solo página actual / total */}
        <span className="sm:hidden text-sm text-slate-600 dark:text-stone-400 px-2">
          {pagina} / {totalPaginas}
        </span>

        <button onClick={() => onPagina(pagina + 1)} disabled={pagina === totalPaginas} className={btnNav}>
          Siguiente
        </button>
      </div>
    </div>
  )
}

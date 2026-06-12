import { useRef, useState, useEffect, useCallback } from 'react'
import { api } from '../api/sonograma'

const ESTADO_STYLES = {
  PENDIENTE:  'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
  PAGADO:     'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400',
  CANCELADO:  'bg-slate-100 text-slate-500 dark:bg-stone-800 dark:text-stone-400',
}

const ROW_ESTADO_STYLES = {
  CREATED: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400',
  UPDATED: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
  SKIPPED: 'bg-slate-100 text-slate-500 dark:bg-stone-800 dark:text-stone-400',
  ERROR:   'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
}

function fmt(n) {
  if (n == null) return '—'
  return `$${Number(n).toLocaleString('es-UY', { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`
}

function fmtDate(s) {
  if (!s) return '—'
  return new Date(s + 'T00:00:00').toLocaleDateString('es-UY', { day: '2-digit', month: '2-digit', year: 'numeric' })
}

function ImportSummaryPanel({ result, onClose }) {
  return (
    <div className="card p-4 space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="font-semibold text-slate-800 dark:text-stone-100 text-sm">Resultado de importación</h3>
        <button onClick={onClose} className="text-slate-400 hover:text-slate-600 dark:hover:text-stone-300 text-lg leading-none">✕</button>
      </div>

      <div className="grid grid-cols-3 sm:grid-cols-7 gap-2 text-center">
        {[
          { label: 'Total leídas', value: result.totalFilas },
          { label: 'Vacías',       value: result.filasVacias },
          { label: 'Detectadas',   value: result.detectados },
          { label: 'Creadas',      value: result.creados,     color: 'text-emerald-600 dark:text-emerald-400' },
          { label: 'Actualizadas', value: result.actualizados, color: 'text-blue-600 dark:text-blue-400' },
          { label: 'Omitidas',     value: result.omitidos },
          { label: 'Errores',      value: result.errores,     color: result.errores > 0 ? 'text-red-600 dark:text-red-400' : '' },
        ].map(({ label, value, color }) => (
          <div key={label} className="bg-slate-50 dark:bg-stone-800 rounded-lg p-2">
            <p className={`text-lg font-bold tabular-nums ${color || 'text-slate-800 dark:text-stone-100'}`}>{value ?? 0}</p>
            <p className="text-xs text-slate-400 dark:text-stone-500 mt-0.5">{label}</p>
          </div>
        ))}
      </div>

      {result.filas && result.filas.length > 0 && (
        <div className="overflow-x-auto max-h-64 overflow-y-auto rounded-lg border border-slate-100 dark:border-stone-800">
          <table className="w-full text-xs">
            <thead className="sticky top-0 bg-white dark:bg-stone-900">
              <tr className="border-b border-slate-100 dark:border-stone-800">
                {['Fila', 'Nombre', 'Monto original', 'Monto $', 'Fecha', 'Estado', 'Mensaje'].map(h => (
                  <th key={h} className="px-3 py-2 text-left text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider whitespace-nowrap">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-stone-800">
              {result.filas.map((f, i) => (
                <tr key={i} className="hover:bg-slate-50 dark:hover:bg-stone-900/50">
                  <td className="px-3 py-2 tabular-nums text-slate-500">{f.fila}</td>
                  <td className="px-3 py-2 text-slate-700 dark:text-stone-300">{f.nombreDeudor || '—'}</td>
                  <td className="px-3 py-2 font-mono text-slate-500">{f.montoOriginal || '—'}</td>
                  <td className="px-3 py-2 tabular-nums text-slate-700 dark:text-stone-300">{fmt(f.montoUyu)}</td>
                  <td className="px-3 py-2 whitespace-nowrap text-slate-500">{fmtDate(f.fechaEstimada)}</td>
                  <td className="px-3 py-2">
                    <span className={`inline-flex items-center px-1.5 py-0.5 rounded-full text-xs font-medium ${ROW_ESTADO_STYLES[f.estado] || ''}`}>
                      {f.estado}
                    </span>
                  </td>
                  <td className="px-3 py-2 text-slate-400 dark:text-stone-500">{f.mensaje}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

export default function Deudores() {
  const [deudores, setDeudores] = useState([])
  const [loading, setLoading] = useState(true)
  const [importing, setImporting] = useState(false)
  const [importResult, setImportResult] = useState(null)
  const [importError, setImportError] = useState(null)
  const fileRef = useRef(null)

  const cargar = useCallback(async () => {
    setLoading(true)
    try {
      setDeudores(await api.deudores.todos())
    } catch (e) {
      console.error(e)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { cargar() }, [cargar])

  async function onFileChange(e) {
    const file = e.target.files[0]
    if (!file) return
    setImporting(true)
    setImportResult(null)
    setImportError(null)
    try {
      const fd = new FormData()
      fd.append('file', file)
      const result = await api.deudores.importarExcel(fd)
      setImportResult(result)
      cargar()
    } catch (err) {
      setImportError(err.message)
    } finally {
      setImporting(false)
      e.target.value = ''
    }
  }

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 py-6 space-y-5">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-900 dark:text-white">Deudores</h1>
          <p className="text-slate-400 dark:text-stone-500 text-sm mt-0.5">Deudores importados desde Excel</p>
        </div>
        <div className="flex-shrink-0">
          <input ref={fileRef} type="file" accept=".xlsx,.xls" className="hidden" onChange={onFileChange} />
          <button
            onClick={() => fileRef.current?.click()}
            disabled={importing}
            className="btn-secondary text-sm disabled:opacity-40"
          >
            {importing ? 'Importando…' : 'Importar Excel'}
          </button>
        </div>
      </div>

      {importError && (
        <div className="p-3 rounded-xl bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 text-sm text-red-700 dark:text-red-400 flex items-center justify-between gap-4">
          <span>Error: {importError}</span>
          <button onClick={() => setImportError(null)} className="text-xs hover:underline">Cerrar</button>
        </div>
      )}

      {importResult && (
        <ImportSummaryPanel result={importResult} onClose={() => setImportResult(null)} />
      )}

      <div className="card overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-100 dark:border-stone-800">
                {['Nombre', 'Monto', 'Fecha estimada', 'Notas', 'Discos', 'Estado'].map(h => (
                  <th key={h} className="text-left px-5 py-3 text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider whitespace-nowrap">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-stone-800">
              {loading ? (
                <tr><td colSpan={6} className="px-5 py-12 text-center text-slate-400">Cargando…</td></tr>
              ) : deudores.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-5 py-16 text-center text-slate-400 dark:text-stone-500 text-sm">
                    No hay deudores registrados. Importá un Excel para comenzar.
                  </td>
                </tr>
              ) : deudores.map(d => (
                <tr key={d.id} className="hover:bg-slate-50 dark:hover:bg-stone-900/50 transition-colors">
                  <td className="px-5 py-3 font-medium text-slate-800 dark:text-stone-200">
                    {d.nombreDeudor}
                    {d.clienteNombre && (
                      <span className="ml-2 text-xs text-[#5C7D87] dark:text-[#7E9FA8]">({d.clienteNombre})</span>
                    )}
                  </td>
                  <td className="px-5 py-3 tabular-nums text-slate-700 dark:text-stone-300">
                    {fmt(d.montoUyu)}
                    {d.montoOriginal && d.montoOriginal !== String(d.montoUyu) && (
                      <span className="ml-1 text-xs text-slate-400 font-mono">({d.montoOriginal})</span>
                    )}
                  </td>
                  <td className="px-5 py-3 text-slate-600 dark:text-stone-400 whitespace-nowrap">{fmtDate(d.fechaEstimada)}</td>
                  <td className="px-5 py-3 text-slate-500 dark:text-stone-400 max-w-xs truncate" title={d.notas}>{d.notas || '—'}</td>
                  <td className="px-5 py-3 text-slate-500 dark:text-stone-400 max-w-xs truncate" title={d.descripcionDiscos}>{d.descripcionDiscos || '—'}</td>
                  <td className="px-5 py-3">
                    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${ESTADO_STYLES[d.estado] || ''}`}>
                      {d.estado}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}

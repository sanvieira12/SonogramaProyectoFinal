import { useState, useEffect, useCallback } from 'react'
import { api } from '../api/sonograma'

const ESTADO_STYLES = {
  PENDIENTE: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
  PARCIAL:   'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400',
  PAGADO:    'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400',
}

function fmt(n) {
  if (n == null) return '$0'
  return `$${Number(n).toLocaleString('es-UY', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function fmtDate(s) {
  if (!s) return '—'
  return new Date(s + 'T00:00:00').toLocaleDateString('es-UY', { day: '2-digit', month: '2-digit', year: 'numeric' })
}

function PagoModal({ deuda, onClose, onPagado }) {
  const [monto, setMonto] = useState('')
  const [notas, setNotas] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  async function submit(e) {
    e.preventDefault()
    const n = parseFloat(monto)
    if (!n || n <= 0) { setError('Ingresá un monto válido'); return }
    if (n > deuda.montoPendiente) { setError('El monto excede la deuda pendiente'); return }
    setLoading(true)
    setError(null)
    try {
      const actualizada = await api.deudas.registrarPago(deuda.idDeuda, n, notas || null)
      onPagado(actualizada)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div className="bg-white dark:bg-stone-900 rounded-xl shadow-2xl w-full max-w-sm">
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-100 dark:border-stone-800">
          <h2 className="font-semibold text-slate-900 dark:text-white text-sm">Registrar pago</h2>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600">✕</button>
        </div>
        <form onSubmit={submit} className="p-5 space-y-4">
          <div className="bg-slate-50 dark:bg-stone-800 rounded-lg p-3 text-sm space-y-1">
            <p className="font-semibold text-slate-800 dark:text-stone-200">{deuda.nombreCliente}</p>
            {deuda.numeroFactura && <p className="text-xs text-slate-500">Factura: {deuda.numeroFactura}</p>}
            <p className="text-xs text-slate-500">Pendiente: <span className="font-semibold text-red-600 dark:text-red-400">{fmt(deuda.montoPendiente)}</span></p>
          </div>
          {error && <p className="text-red-500 text-sm">{error}</p>}
          <div>
            <label className="block text-xs text-slate-500 dark:text-stone-400 mb-1">Monto a pagar *</label>
            <input
              type="number" step="0.01" min="0.01" max={deuda.montoPendiente}
              className="input w-full"
              placeholder={`Máx. ${fmt(deuda.montoPendiente)}`}
              value={monto}
              onChange={e => setMonto(e.target.value)}
              autoFocus
            />
          </div>
          <div>
            <label className="block text-xs text-slate-500 dark:text-stone-400 mb-1">Notas</label>
            <input type="text" className="input w-full" placeholder="Método, referencia…"
              value={notas} onChange={e => setNotas(e.target.value)} />
          </div>
          <div className="flex justify-end gap-3">
            <button type="button" onClick={onClose} className="btn-secondary text-sm">Cancelar</button>
            <button type="submit" disabled={loading} className="btn-primary text-sm disabled:opacity-50">
              {loading ? 'Registrando…' : 'Confirmar pago'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

export default function Deudas() {
  const [deudas, setDeudas] = useState([])
  const [resumen, setResumen] = useState(null)
  const [loading, setLoading] = useState(true)
  const [q, setQ] = useState('')
  const [search, setSearch] = useState('')
  const [pagoDeuda, setPagoDeuda] = useState(null)

  const cargar = useCallback(async (query) => {
    setLoading(true)
    try {
      const [d, r] = await Promise.all([
        api.deudas.listar(query),
        api.deudas.resumen(),
      ])
      setDeudas(d)
      setResumen(r)
    } catch (e) {
      console.error(e)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { cargar('') }, [cargar])

  function buscar() {
    setSearch(q)
    cargar(q)
  }

  function onPagado(actualizada) {
    setDeudas(prev => prev.map(d => d.idDeuda === actualizada.idDeuda ? actualizada : d)
      .filter(d => d.estadoPago !== 'PAGADO'))
    setPagoDeuda(null)
    api.deudas.resumen().then(setResumen).catch(() => {})
  }

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 py-6 space-y-5">
      <div>
        <h1 className="text-xl font-bold text-slate-900 dark:text-white">Deudas</h1>
        <p className="text-slate-400 dark:text-stone-500 text-sm mt-0.5">Cuentas corrientes y pagos pendientes</p>
      </div>

      {/* Summary cards */}
      <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
        {[
          { label: 'Total adeudado',     value: fmt(resumen?.totalPendiente), desc: 'Suma de todas las deudas activas' },
          { label: 'Clientes con deuda', value: resumen?.cantDeudores ?? '—',  desc: 'Clientes con saldo pendiente' },
          { label: 'Deudas activas',     value: resumen?.cantDeudas ?? '—',   desc: 'Deudas sin saldar' },
          { label: 'Mayor deuda',        value: fmt(resumen?.mayorDeuda),      desc: 'Deuda individual más alta' },
        ].map(({ label, value, desc }) => (
          <div key={label} className="card p-5">
            <p className="text-xs uppercase tracking-wider text-slate-400 dark:text-stone-500 mb-1">{label}</p>
            <p className="text-2xl font-bold text-slate-900 dark:text-white tabular-nums">{value}</p>
            <p className="text-xs text-slate-400 dark:text-stone-500 mt-1">{desc}</p>
          </div>
        ))}
      </div>

      {/* Search */}
      <div className="relative max-w-sm flex gap-2">
        <div className="relative flex-1">
          <svg className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-300 dark:text-stone-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="m21 21-5.197-5.197m0 0A7.5 7.5 0 1 0 5.196 5.196a7.5 7.5 0 0 0 10.607 10.607Z" />
          </svg>
          <input
            placeholder="Buscar cliente o factura…"
            className="input pl-9 w-full"
            value={q}
            onChange={e => setQ(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && buscar()}
          />
        </div>
        <button onClick={buscar} className="btn-primary text-sm">Buscar</button>
      </div>

      {/* Table */}
      <div className="card overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-100 dark:border-stone-800">
                {['Cliente', 'Factura', 'Fecha venta', 'Total', 'Pagado', 'Pendiente', 'Estado', 'Acciones'].map(h => (
                  <th key={h} className="text-left px-5 py-3 text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider whitespace-nowrap">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-stone-800">
              {loading ? (
                <tr><td colSpan={8} className="px-5 py-12 text-center text-slate-400">Cargando…</td></tr>
              ) : deudas.length === 0 ? (
                <tr>
                  <td colSpan={8} className="px-5 py-16 text-center text-slate-400 dark:text-stone-500 text-sm">
                    {search ? 'No se encontraron deudas para esa búsqueda' : 'No hay deudas pendientes'}
                  </td>
                </tr>
              ) : deudas.map(d => (
                <tr key={d.idDeuda} className="hover:bg-slate-50 dark:hover:bg-stone-900/50 transition-colors">
                  <td className="px-5 py-3 font-medium text-slate-800 dark:text-stone-200">{d.nombreCliente}</td>
                  <td className="px-5 py-3 font-mono text-xs text-slate-500">{d.numeroFactura || '—'}</td>
                  <td className="px-5 py-3 text-slate-600 dark:text-stone-400 whitespace-nowrap">{fmtDate(d.fechaVenta)}</td>
                  <td className="px-5 py-3 tabular-nums text-slate-700 dark:text-stone-300">{fmt(d.montoTotal)}</td>
                  <td className="px-5 py-3 tabular-nums text-emerald-600 dark:text-emerald-400">{fmt(d.montoPagado)}</td>
                  <td className="px-5 py-3 tabular-nums font-semibold text-red-600 dark:text-red-400">{fmt(d.montoPendiente)}</td>
                  <td className="px-5 py-3">
                    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${ESTADO_STYLES[d.estadoPago] || ''}`}>
                      {d.estadoPago}
                    </span>
                  </td>
                  <td className="px-5 py-3">
                    <button
                      onClick={() => setPagoDeuda(d)}
                      className="text-xs text-[#5C7D87] hover:underline font-medium"
                    >
                      Registrar pago
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {pagoDeuda && (
        <PagoModal
          deuda={pagoDeuda}
          onClose={() => setPagoDeuda(null)}
          onPagado={onPagado}
        />
      )}
    </div>
  )
}

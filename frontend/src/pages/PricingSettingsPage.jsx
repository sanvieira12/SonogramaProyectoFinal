import { useEffect, useMemo, useState } from 'react'
import { api } from '../api/sonograma'

const EMPTY = {
  eurUyuRate: '49.5',
  extraCostSingleEur: '5',
  extraCostDoubleEur: '8',
  extraCostMultiEur: '9',
  markupSingle: '1.7',
  markupDouble: '1.5',
  markupMulti: '1.4',
}

function toForm(settings) {
  if (!settings) return EMPTY
  return {
    eurUyuRate: String(settings.eurUyuRate ?? ''),
    extraCostSingleEur: String(settings.extraCostSingleEur ?? ''),
    extraCostDoubleEur: String(settings.extraCostDoubleEur ?? ''),
    extraCostMultiEur: String(settings.extraCostMultiEur ?? ''),
    markupSingle: String(settings.markupSingle ?? ''),
    markupDouble: String(settings.markupDouble ?? ''),
    markupMulti: String(settings.markupMulti ?? ''),
  }
}

function toPayload(form) {
  return {
    eurUyuRate: Number(form.eurUyuRate),
    extraCostSingleEur: Number(form.extraCostSingleEur),
    extraCostDoubleEur: Number(form.extraCostDoubleEur),
    extraCostMultiEur: Number(form.extraCostMultiEur),
    markupSingle: Number(form.markupSingle),
    markupDouble: Number(form.markupDouble),
    markupMulti: Number(form.markupMulti),
  }
}

function money(value, digits = 0) {
  if (value == null || value === '') return '—'
  const number = Number(value)
  if (!Number.isFinite(number)) return '—'
  return number.toLocaleString('es-UY', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  })
}

function dateLabel(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleDateString('es-UY', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  })
}

function InputField({ label, value, onChange, step = '0.01', min = '0' }) {
  return (
    <label className="space-y-1.5">
      <span className="text-xs uppercase tracking-wider text-slate-500 dark:text-stone-500">{label}</span>
      <input
        type="number"
        className="input"
        value={value}
        onChange={onChange}
        min={min}
        step={step}
      />
    </label>
  )
}

export default function PricingSettingsPage() {
  const [form, setForm] = useState(EMPTY)
  const [preview, setPreview] = useState([])
  const [loading, setLoading] = useState(true)
  const [previewing, setPreviewing] = useState(false)
  const [saving, setSaving] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true)
      setError('')
      try {
        const settings = await api.pricing.settings()
        if (cancelled) return
        const next = toForm(settings)
        setForm(next)
        const previewData = await api.pricing.preview(toPayload(next))
        if (!cancelled) setPreview(previewData.rows || [])
      } catch (err) {
        if (!cancelled) setError(err.message)
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    load()
    return () => { cancelled = true }
  }, [])

  useEffect(() => {
    if (loading) return undefined
    const timeout = window.setTimeout(async () => {
      setPreviewing(true)
      setError('')
      try {
        const data = await api.pricing.preview(toPayload(form))
        setPreview(data.rows || [])
      } catch (err) {
        setError(err.message)
      } finally {
        setPreviewing(false)
      }
    }, 350)
    return () => window.clearTimeout(timeout)
  }, [form, loading])

  const totals = useMemo(() => {
    return preview.reduce((acc, row) => {
      acc.total += Number(row.finalSalePriceUyu || 0)
      acc.auto += row.pricingMode === 'AUTO' ? 1 : 0
      acc.manual += row.pricingMode === 'MANUAL' ? 1 : 0
      return acc
    }, { total: 0, auto: 0, manual: 0 })
  }, [preview])

  function setField(field, value) {
    setForm(current => ({ ...current, [field]: value }))
    setMessage('')
  }

  async function runPreview() {
    setPreviewing(true)
      setError('')
      try {
        const data = await api.pricing.preview(toPayload(form))
        setPreview(data.rows || [])
        setMessage('Vista previa actualizada.')
      } catch (err) {
        setError(err.message)
      } finally {
      setPreviewing(false)
    }
  }

  async function apply(scope) {
    setSaving(scope)
    setError('')
    setMessage('')
    try {
      const response = await api.pricing.apply(toPayload(form), scope)
      setMessage(`Cambios aplicados. ${response.updatedCount} registros actualizados.`)
      const data = await api.pricing.preview(toPayload(form))
      setPreview(data.rows || [])
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving('')
    }
  }

  async function resetDefaults() {
    setSaving('reset')
    setError('')
    setMessage('')
    try {
      const settings = await api.pricing.reset()
      const next = toForm(settings)
      setForm(next)
      const data = await api.pricing.preview(toPayload(next))
      setPreview(data.rows || [])
      setMessage('Configuración restablecida a valores por defecto.')
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving('')
    }
  }

  return (
    <div className="space-y-5">
      <section className="card p-5 space-y-5">
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          <InputField label="EUR/UYU exchange rate" value={form.eurUyuRate} onChange={e => setField('eurUyuRate', e.target.value)} step="0.0001" min="0.0001" />
          <InputField label="Extra cost single EUR" value={form.extraCostSingleEur} onChange={e => setField('extraCostSingleEur', e.target.value)} />
          <InputField label="Extra cost double EUR" value={form.extraCostDoubleEur} onChange={e => setField('extraCostDoubleEur', e.target.value)} />
          <InputField label="Extra cost multi EUR" value={form.extraCostMultiEur} onChange={e => setField('extraCostMultiEur', e.target.value)} />
          <InputField label="Markup single" value={form.markupSingle} onChange={e => setField('markupSingle', e.target.value)} step="0.0001" min="0.0001" />
          <InputField label="Markup double" value={form.markupDouble} onChange={e => setField('markupDouble', e.target.value)} step="0.0001" min="0.0001" />
          <InputField label="Markup multi" value={form.markupMulti} onChange={e => setField('markupMulti', e.target.value)} step="0.0001" min="0.0001" />
        </div>

        <div className="flex flex-wrap gap-3">
          <button type="button" onClick={runPreview} className="btn-secondary" disabled={previewing}>
            {previewing ? 'Actualizando...' : 'Preview recalculation'}
          </button>
          <button type="button" onClick={() => apply('automatic')} className="btn-primary" disabled={saving !== ''}>
            {saving === 'automatic' ? 'Aplicando...' : 'Apply to automatic prices'}
          </button>
          <button type="button" onClick={() => apply('all')} className="btn-secondary" disabled={saving !== ''}>
            {saving === 'all' ? 'Aplicando...' : 'Apply to all prices'}
          </button>
          <button type="button" onClick={resetDefaults} className="btn-secondary" disabled={saving !== ''}>
            {saving === 'reset' ? 'Reseteando...' : 'Reset to defaults'}
          </button>
        </div>

        {message && <div className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700 dark:border-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-300">{message}</div>}
        {error && <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-900 dark:bg-red-950/40 dark:text-red-300">{error}</div>}
      </section>

      <section className="grid gap-4 md:grid-cols-3">
        <div className="card p-4">
          <p className="text-xs uppercase tracking-wider text-slate-500 dark:text-stone-500">Rows in preview</p>
          <p className="mt-2 text-2xl font-bold text-slate-900 dark:text-white">{preview.length}</p>
        </div>
        <div className="card p-4">
          <p className="text-xs uppercase tracking-wider text-slate-500 dark:text-stone-500">Automatic vs manual</p>
          <p className="mt-2 text-sm text-slate-700 dark:text-stone-300">{totals.auto} auto · {totals.manual} manual</p>
        </div>
        <div className="card p-4">
          <p className="text-xs uppercase tracking-wider text-slate-500 dark:text-stone-500">Projected total</p>
          <p className="mt-2 text-2xl font-bold text-slate-900 dark:text-white">UYU ${money(totals.total, 0)}</p>
        </div>
      </section>

      <section className="card overflow-hidden">
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-100 dark:border-stone-800">
          <div>
            <h2 className="font-semibold text-slate-900 dark:text-white">Vista previa de recálculo</h2>
            <p className="text-xs text-slate-500 dark:text-stone-500 mt-1">Impacto de los costos y precios con la configuración actual.</p>
          </div>
          {loading || previewing ? <span className="text-xs text-slate-400 dark:text-stone-500">Updating…</span> : null}
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm min-w-[1760px]">
            <thead className="bg-slate-50 dark:bg-stone-950">
              <tr className="text-left">
                {['Invoice Number', 'Invoice Date', 'Supplier', 'Shipping', 'Code', 'Artist', 'Title', 'Format', 'Type', 'Unit price EUR', 'Qty', 'Unit line total EUR', 'Extra cost EUR', 'Real cost EUR', 'Real cost UYU', 'Markup', 'Final sale price UYU', 'Mode'].map(label => (
                  <th key={label} className="px-4 py-3 text-xs font-semibold uppercase tracking-wider text-slate-500 dark:text-stone-500">{label}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-stone-800">
              {preview.map(row => (
                <tr key={row.idDisco || `${row.code}-${row.title}`}>
                  <td className="px-4 py-3 text-slate-600 dark:text-stone-400">{row.invoiceNumber || '—'}</td>
                  <td className="px-4 py-3 text-slate-600 dark:text-stone-400">{dateLabel(row.invoiceDate)}</td>
                  <td className="px-4 py-3 text-slate-600 dark:text-stone-400">{row.supplier || '—'}</td>
                  <td className="px-4 py-3 tabular-nums text-slate-600 dark:text-stone-400">{money(row.shipping, 2)}</td>
                  <td className="px-4 py-3 text-slate-600 dark:text-stone-400">{row.code || '—'}</td>
                  <td className="px-4 py-3 font-medium text-slate-900 dark:text-white">{row.artist || '—'}</td>
                  <td className="px-4 py-3 text-slate-600 dark:text-stone-400">{row.title || '—'}</td>
                  <td className="px-4 py-3 text-slate-600 dark:text-stone-400">{row.format || '—'}</td>
                  <td className="px-4 py-3 text-slate-600 dark:text-stone-400">{row.type}</td>
                  <td className="px-4 py-3 tabular-nums text-white">{money(row.unitPriceEur, 2)}</td>
                  <td className="px-4 py-3 tabular-nums text-white">{row.quantity ?? '—'}</td>
                  <td className="px-4 py-3 tabular-nums text-white">{money(row.unitLineTotalEur, 2)}</td>
                  <td className="px-4 py-3 tabular-nums text-white">{money(row.extraCostEur, 2)}</td>
                  <td className="px-4 py-3 tabular-nums text-white">{money(row.realCostEur, 2)}</td>
                  <td className="px-4 py-3 tabular-nums text-white">{money(row.realCostUyu, 2)}</td>
                  <td className="px-4 py-3 tabular-nums text-white">{money(row.markup, 2)}</td>
                  <td className="px-4 py-3 font-semibold text-slate-900 dark:text-white tabular-nums">{money(row.finalSalePriceUyu, 0)}</td>
                  <td className="px-4 py-3">
                    <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-medium ${row.pricingMode === 'MANUAL' ? 'bg-amber-100 text-amber-700 dark:bg-amber-950/50 dark:text-amber-300' : 'bg-emerald-100 text-emerald-700 dark:bg-emerald-950/50 dark:text-emerald-300'}`}>
                      {row.pricingMode === 'MANUAL' ? 'manual' : 'automatic'}
                    </span>
                  </td>
                </tr>
              ))}
              {!loading && preview.length === 0 ? (
                <tr>
                  <td colSpan={18} className="px-4 py-10 text-center text-slate-500 dark:text-stone-500">No hay discos para recalcular.</td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  )
}

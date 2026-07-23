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

const FIELD_CONFIG = {
  eurUyuRate: { label: 'Cotización EUR/UYU', minExclusive: 0, step: '0.0001' },
  extraCostSingleEur: { label: 'Costo extra disco simple (EUR)', minInclusive: 0, step: '0.0001' },
  extraCostDoubleEur: { label: 'Costo extra disco doble (EUR)', minInclusive: 0, step: '0.0001' },
  extraCostMultiEur: { label: 'Costo extra disco múltiple (EUR)', minInclusive: 0, step: '0.0001' },
  markupSingle: { label: 'Markup disco simple', minExclusive: 0, step: '0.0001' },
  markupDouble: { label: 'Markup disco doble', minExclusive: 0, step: '0.0001' },
  markupMulti: { label: 'Markup disco múltiple', minExclusive: 0, step: '0.0001' },
}

const TYPE_LABELS = {
  SINGLE: 'Simple',
  DOUBLE: 'Doble',
  MULTI: 'Múltiple',
}

const CONDITION_FILTERS = [
  { value: 'TODOS', label: 'Todos' },
  { value: 'NUEVO', label: 'Nuevos' },
  { value: 'USADO', label: 'Usados' },
]

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

function normalizeDecimalString(value) {
  return String(value ?? '').trim().replace(',', '.')
}

function parseDecimalInput(value, { label, minInclusive, minExclusive }) {
  const normalized = normalizeDecimalString(value)
  if (!normalized) {
    return { error: `Completá ${label.toLowerCase()}.` }
  }
  if (!/^-?\d+(\.\d+)?$/.test(normalized)) {
    return { error: `${label} debe ser un número válido.` }
  }
  const parsed = Number.parseFloat(normalized)
  if (!Number.isFinite(parsed)) {
    return { error: `${label} debe ser un número válido.` }
  }
  if (minExclusive != null && parsed <= minExclusive) {
    return { error: `${label} debe ser mayor a ${minExclusive}.` }
  }
  if (minInclusive != null && parsed < minInclusive) {
    return { error: `${label} no puede ser negativo.` }
  }
  return { value: normalized }
}

function validateForm(form) {
  const errors = {}
  const payload = {}

  Object.entries(FIELD_CONFIG).forEach(([field, config]) => {
    const result = parseDecimalInput(form[field], config)
    if (result.error) {
      errors[field] = result.error
      return
    }
    payload[field] = result.value
  })

  return {
    errors,
    payload,
    isValid: Object.keys(errors).length === 0,
  }
}

function formatDecimal(value, maximumFractionDigits = 6) {
  if (value == null || value === '') return '—'
  const number = Number(value)
  if (!Number.isFinite(number)) return '—'
  return number.toLocaleString('es-UY', {
    maximumFractionDigits,
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

function markupDraftValue(value) {
  if (value == null || value === '') return ''
  return String(value)
}

function normalizeSearchValue(value) {
  return String(value ?? '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .trim()
}

function normalizeCondition(value) {
  const normalized = normalizeSearchValue(value)
  if (['nuevo', 'nueva', 'new', 'mint', 'm'].includes(normalized)) return 'NUEVO'
  if (['usado', 'used', 'very good', 'vg', 'vg+', 'vg++'].includes(normalized)) return 'USADO'
  return 'OTRO'
}

function searchableValuesForRow(row) {
  const formattedDate = dateLabel(row.invoiceDate)
  const formattedType = TYPE_LABELS[row.type] || row.type || ''
  const numericValues = [
    row.unitPriceEur,
    row.quantity,
    row.unitLineTotalEur,
    row.extraCostEur,
    row.realCostEur,
    row.realCostUyu,
    row.markup,
    row.finalSalePriceUyu,
  ]

  return [
    row.invoiceNumber,
    formattedDate,
    row.supplier,
    row.shipping,
    row.code,
    row.artist,
    row.title,
    row.format,
    formattedType,
    ...numericValues.flatMap(value => [value, formatDecimal(value)]),
  ]
    .map(normalizeSearchValue)
    .filter(Boolean)
}

function InputField({ field, value, onChange, error }) {
  const config = FIELD_CONFIG[field]
  const inputId = `pricing-${field}`
  const errorId = `${inputId}-error`
  return (
    <label className="space-y-1.5" htmlFor={inputId}>
      <span className="text-xs uppercase tracking-wider text-slate-500 dark:text-stone-500">{config.label}</span>
      <input
        id={inputId}
        type="number"
        className={`input ${error ? 'border-red-400 dark:border-red-700' : ''}`}
        value={value}
        onChange={onChange}
        min={config.minExclusive != null ? '0.0001' : '0'}
        step={config.step}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? errorId : undefined}
      />
      {error ? <p id={errorId} className="text-xs text-red-600 dark:text-red-400">{error}</p> : null}
    </label>
  )
}

function SelectedApplyDialog({ open, count, loading, onCancel, onConfirm }) {
  if (!open) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4 backdrop-blur-sm" onClick={onCancel}>
      <div
        className="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-6 shadow-2xl dark:border-stone-700 dark:bg-stone-900"
        onClick={event => event.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="selected-apply-title"
      >
        <h3 id="selected-apply-title" className="text-base font-bold text-slate-900 dark:text-white">
          Aplicar a seleccionados
        </h3>
        <p className="mt-2 text-sm text-slate-600 dark:text-stone-400">
          {`Se recalcularán los precios de ${count} discos seleccionados. Los discos no seleccionados no se modificarán.`}
        </p>
        <div className="mt-6 flex gap-3">
          <button type="button" className="btn-secondary flex-1" onClick={onCancel} disabled={loading}>
            Cancelar
          </button>
          <button
            type="button"
            className="flex-1 rounded-lg bg-stone-600 px-4 py-2 text-sm font-semibold text-white transition-all duration-200 hover:bg-stone-700 disabled:opacity-50"
            onClick={onConfirm}
            disabled={loading}
          >
            {loading ? 'Aplicando cambios...' : 'Aplicar cambios'}
          </button>
        </div>
      </div>
    </div>
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
  const [fieldErrors, setFieldErrors] = useState({})
  const [selectedIds, setSelectedIds] = useState(() => new Set())
  const [markupDrafts, setMarkupDrafts] = useState({})
  const [markupFeedback, setMarkupFeedback] = useState({})
  const [savingMarkupId, setSavingMarkupId] = useState(null)
  const [selectedDialogOpen, setSelectedDialogOpen] = useState(false)
  const [search, setSearch] = useState('')
  const [conditionFilter, setConditionFilter] = useState('TODOS')
  const [supplierSort, setSupplierSort] = useState('none')

  function syncRows(nextRows) {
    setPreview(nextRows)
    setMarkupDrafts(Object.fromEntries(nextRows.map(row => [row.idDisco, markupDraftValue(row.markup)])))
    const nextIds = new Set(nextRows.map(row => row.idDisco))
    setSelectedIds(current => new Set([...current].filter(id => nextIds.has(id))))
  }

  useEffect(() => {
    let cancelled = false

    async function load() {
      setLoading(true)
      setError('')
      try {
        const settings = await api.pricing.settings()
        if (cancelled) return
        const nextForm = toForm(settings)
        setForm(nextForm)
        const previewData = await api.pricing.preview(validateForm(nextForm).payload)
        if (cancelled) return
        syncRows(previewData.rows || [])
      } catch (err) {
        if (!cancelled) setError(err.message)
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    load()
    return () => { cancelled = true }
  }, [])

  const totals = useMemo(() => {
    return preview.reduce((acc, row) => {
      acc.total += Number(row.finalSalePriceUyu || 0)
      acc.auto += row.pricingMode === 'AUTO' ? 1 : 0
      acc.manual += row.pricingMode === 'MANUAL' ? 1 : 0
      return acc
    }, { total: 0, auto: 0, manual: 0 })
  }, [preview])

  const normalizedSearch = normalizeSearchValue(search)
  const filteredPreview = useMemo(() => {
    return preview.filter(row => {
      const matchesCondition = conditionFilter === 'TODOS'
        || normalizeCondition(row.condicion) === conditionFilter
      const matchesSearch = !normalizedSearch
        || searchableValuesForRow(row).some(value => value.includes(normalizedSearch))
      return matchesCondition && matchesSearch
    })
  }, [conditionFilter, normalizedSearch, preview])
  const sortedPreview = useMemo(() => {
    if (supplierSort === 'none') return filteredPreview
    const factor = supplierSort === 'asc' ? 1 : -1
    return [...filteredPreview].sort((left, right) => {
      const bySupplier = normalizeSearchValue(left.supplier).localeCompare(
        normalizeSearchValue(right.supplier),
        'es',
        { sensitivity: 'base' }
      )
      if (bySupplier !== 0) return bySupplier * factor
      return (left.idDisco ?? 0) - (right.idDisco ?? 0)
    })
  }, [filteredPreview, supplierSort])
  const visibleIds = useMemo(() => sortedPreview.map(row => row.idDisco).filter(Boolean), [sortedPreview])
  const allVisibleSelected = visibleIds.length > 0 && visibleIds.every(id => selectedIds.has(id))
  const selectedCount = selectedIds.size
  const visibleCount = sortedPreview.length

  function toggleSupplierSort() {
    setSupplierSort(current => {
      if (current === 'none') return 'asc'
      if (current === 'asc') return 'desc'
      return 'none'
    })
  }

  function clearSearchAndCondition() {
    setSearch('')
    setConditionFilter('TODOS')
  }

  function setField(field, value) {
    setForm(current => ({ ...current, [field]: value }))
    setFieldErrors(current => {
      if (!current[field]) return current
      const next = { ...current }
      delete next[field]
      return next
    })
    setMessage('')
    setError('')
  }

  async function refreshPreview({ successMessage = '', nextForm = form } = {}) {
    const validation = validateForm(nextForm)
    setFieldErrors(validation.errors)
    if (!validation.isValid) {
      setMessage('')
      return false
    }

    setPreviewing(true)
    setError('')
    try {
      const data = await api.pricing.preview(validation.payload)
      syncRows(data.rows || [])
      if (successMessage) setMessage(successMessage)
      return true
    } catch (err) {
      setError(err.message)
      return false
    } finally {
      setPreviewing(false)
    }
  }

  function toggleRow(id) {
    setSelectedIds(current => {
      const next = new Set(current)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  function toggleAllVisible() {
    setSelectedIds(current => {
      const next = new Set(current)
      if (allVisibleSelected) {
        visibleIds.forEach(id => next.delete(id))
      } else {
        visibleIds.forEach(id => next.add(id))
      }
      return next
    })
  }

  function clearAllSelections() {
    setSelectedIds(new Set())
  }

  async function apply(scope) {
    const validation = validateForm(form)
    setFieldErrors(validation.errors)
    if (!validation.isValid) {
      setMessage('')
      return
    }

    setSaving(scope)
    setError('')
    setMessage('')
    try {
      const response = await api.pricing.apply(validation.payload, scope, [])
      await refreshPreview()
      setMessage(`Cambios aplicados correctamente a ${response.updatedCount} discos.`)
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving('')
    }
  }

  async function applySelected() {
    const validation = validateForm(form)
    const ids = [...selectedIds]
    setFieldErrors(validation.errors)
    if (!validation.isValid) {
      setSelectedDialogOpen(false)
      return
    }
    if (ids.length === 0) {
      return
    }

    setSaving('selected')
    setError('')
    setMessage('')
    try {
      const response = await api.pricing.apply(validation.payload, 'selected', ids)
      await refreshPreview()
      setMessage(`Cambios aplicados correctamente a ${response.updatedCount} discos.`)
      setSelectedDialogOpen(false)
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
      setFieldErrors({})
      await refreshPreview({
        nextForm: next,
        successMessage: 'Configuración restablecida a valores predeterminados.',
      })
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving('')
    }
  }

  function setMarkupDraft(id, value) {
    setMarkupDrafts(current => ({ ...current, [id]: value }))
    setMarkupFeedback(current => ({ ...current, [id]: null }))
  }

  async function saveMarkup(row) {
    const draft = markupDrafts[row.idDisco]
    const parsed = parseDecimalInput(draft, { label: 'Markup', minExclusive: 0 })
    if (parsed.error) {
      setMarkupFeedback(current => ({
        ...current,
        [row.idDisco]: { type: 'error', message: parsed.error },
      }))
      return
    }

    setSavingMarkupId(row.idDisco)
    setMarkupFeedback(current => ({ ...current, [row.idDisco]: null }))
    setError('')
    setMessage('')
    try {
      const response = await api.pricing.updateMarkup(row.idDisco, parsed.value)
      syncRows(preview.map(item => (
        item.idDisco === row.idDisco
          ? {
              ...item,
              markup: response.markup,
              finalSalePriceUyu: response.finalSalePriceUyu,
              pricingMode: response.pricingMode,
            }
          : item
      )))
      setMarkupFeedback(current => ({
        ...current,
        [row.idDisco]: { type: 'success', message: 'Markup actualizado correctamente.' },
      }))
    } catch (err) {
      setMarkupFeedback(current => ({
        ...current,
        [row.idDisco]: { type: 'error', message: 'No se pudo actualizar el markup.' },
      }))
      setError(err.message)
    } finally {
      setSavingMarkupId(null)
    }
  }

  return (
    <div className="space-y-5">
      <section className="card space-y-5 p-5">
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          {Object.keys(FIELD_CONFIG).map(field => (
            <InputField
              key={field}
              field={field}
              value={form[field]}
              onChange={event => setField(field, event.target.value)}
              error={fieldErrors[field]}
            />
          ))}
        </div>

        <div className="flex flex-wrap gap-3">
          <button type="button" onClick={() => refreshPreview({ successMessage: 'Vista previa actualizada.' })} className="btn-secondary" disabled={previewing}>
            {previewing ? 'Actualizando…' : 'Actualizar vista previa'}
          </button>
          <button type="button" onClick={() => apply('automatic')} className="btn-primary" disabled={saving !== ''}>
            {saving === 'automatic' ? 'Aplicando…' : 'Aplicar a precios automáticos'}
          </button>
          <button type="button" onClick={() => apply('all')} className="btn-secondary" disabled={saving !== ''}>
            {saving === 'all' ? 'Aplicando…' : 'Aplicar a todos los discos'}
          </button>
          <button
            type="button"
            onClick={() => setSelectedDialogOpen(true)}
            className="btn-secondary"
            disabled={saving !== '' || selectedCount === 0}
          >
            Aplicar a seleccionados
          </button>
          <button type="button" onClick={resetDefaults} className="btn-secondary" disabled={saving !== ''}>
            {saving === 'reset' ? 'Restableciendo…' : 'Restablecer valores predeterminados'}
          </button>
        </div>

        {message ? (
          <div className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700 dark:border-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-300">
            {message}
          </div>
        ) : null}
        {error ? (
          <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-900 dark:bg-red-950/40 dark:text-red-300">
            {error}
          </div>
        ) : null}
      </section>

      <section className="grid gap-4 md:grid-cols-3">
        <div className="card p-4">
          <p className="text-xs uppercase tracking-wider text-slate-500 dark:text-stone-500">Discos en la vista previa</p>
          <p className="mt-2 text-2xl font-bold text-slate-900 dark:text-white">{preview.length}</p>
        </div>
        <div className="card p-4">
          <p className="text-xs uppercase tracking-wider text-slate-500 dark:text-stone-500">Precios automáticos y manuales</p>
          <p className="mt-2 text-sm text-slate-700 dark:text-stone-300">{totals.auto} automáticos · {totals.manual} manuales</p>
        </div>
        <div className="card p-4">
          <p className="text-xs uppercase tracking-wider text-slate-500 dark:text-stone-500">Valor total proyectado</p>
          <p className="mt-2 text-2xl font-bold text-slate-900 dark:text-white">UYU ${formatDecimal(totals.total)}</p>
        </div>
      </section>

      <section className="card overflow-hidden">
        <div className="sticky top-0 z-20 border-b border-slate-100 bg-white px-5 py-3 dark:border-stone-800 dark:bg-stone-950">
          <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
            <div className="flex min-w-0 flex-1 flex-col gap-2 sm:flex-row sm:items-center">
              <input
                type="search"
                value={search}
                onChange={event => setSearch(event.target.value)}
                placeholder="Buscar por artista, título, código, proveedor, factura…"
                aria-label="Buscar discos en stock"
                className="input h-10 min-w-0 max-w-2xl"
              />
              <button
                type="button"
                className="btn-secondary h-10 px-3 py-2 text-sm"
                onClick={clearSearchAndCondition}
                disabled={search.trim() === '' && conditionFilter === 'TODOS'}
              >
                Limpiar búsqueda
              </button>
              <div
                className="inline-flex w-fit max-w-full shrink-0 overflow-x-auto rounded-lg border border-slate-200 bg-slate-50 p-0.5 dark:border-stone-700 dark:bg-stone-900"
                role="group"
                aria-label="Filtrar por condición"
              >
                {CONDITION_FILTERS.map(option => (
                  <button
                    key={option.value}
                    type="button"
                    className={`shrink-0 rounded-md px-3 py-1.5 text-xs font-semibold transition-colors ${
                      conditionFilter === option.value
                        ? 'bg-white text-slate-900 shadow-sm dark:bg-stone-700 dark:text-white'
                        : 'text-slate-500 hover:text-slate-700 dark:text-stone-400 dark:hover:text-stone-200'
                    }`}
                    onClick={() => setConditionFilter(option.value)}
                    aria-pressed={conditionFilter === option.value}
                  >
                    {option.label}
                  </button>
                ))}
              </div>
            </div>
            <p className="text-sm text-slate-600 dark:text-stone-400">
              {normalizedSearch && visibleCount === 0
                ? 'Sin resultados para esta búsqueda'
                : `${visibleCount} ${visibleCount === 1 ? 'resultado' : 'resultados'}`}
            </p>
          </div>
          {loading || previewing ? <p className="mt-2 text-xs text-slate-400 dark:text-stone-500">Actualizando…</p> : null}
        </div>

        <div className="max-h-[70vh] overflow-auto">
          <table className="min-w-[2050px] w-full text-sm">
            <thead className="bg-slate-50 dark:bg-stone-950">
              <tr className="text-left">
                <th className="sticky left-0 top-0 z-30 border-b border-slate-200 bg-slate-50 px-4 py-3 dark:border-stone-800 dark:bg-stone-950">
                  <div className="flex min-w-[140px] items-start gap-2">
                    <input
                      type="checkbox"
                      checked={allVisibleSelected}
                      onChange={toggleAllVisible}
                      disabled={visibleIds.length === 0}
                      aria-label={allVisibleSelected ? 'Deseleccionar todos los discos visibles' : 'Seleccionar todos los discos visibles'}
                    />
                    <button
                      type="button"
                      className="text-left text-[11px] font-semibold uppercase tracking-wider text-slate-500 hover:text-slate-700 focus:outline-none focus:ring-2 focus:ring-stone-400 dark:text-stone-500 dark:hover:text-stone-300"
                      onClick={allVisibleSelected ? clearAllSelections : toggleAllVisible}
                      disabled={visibleIds.length === 0}
                    >
                      {allVisibleSelected ? 'Deseleccionar visibles' : 'Seleccionar visibles'}
                    </button>
                  </div>
                </th>
                <th className="sticky top-0 z-20 border-b border-slate-200 bg-slate-50 px-4 py-3 text-xs font-semibold uppercase tracking-wider text-slate-500 dark:border-stone-800 dark:bg-stone-950 dark:text-stone-500">
                  Número de factura
                </th>
                <th className="sticky top-0 z-20 border-b border-slate-200 bg-slate-50 px-4 py-3 text-xs font-semibold uppercase tracking-wider text-slate-500 dark:border-stone-800 dark:bg-stone-950 dark:text-stone-500">
                  Fecha de factura
                </th>
                <th
                  className="sticky top-0 z-20 border-b border-slate-200 bg-slate-50 px-4 py-3 text-xs font-semibold uppercase tracking-wider text-slate-500 dark:border-stone-800 dark:bg-stone-950 dark:text-stone-500"
                  aria-sort={supplierSort === 'asc' ? 'ascending' : supplierSort === 'desc' ? 'descending' : 'none'}
                >
                  <button
                    type="button"
                    className="flex items-center gap-1 text-left focus:outline-none focus:ring-2 focus:ring-stone-400"
                    onClick={toggleSupplierSort}
                    aria-label={
                      supplierSort === 'asc'
                        ? 'Ordenar proveedor de Z a A'
                        : supplierSort === 'desc'
                          ? 'Restablecer orden de proveedor'
                          : 'Ordenar proveedor de A a Z'
                    }
                  >
                    <span>Proveedor</span>
                    <span aria-hidden="true" className="inline-block min-w-[1ch] text-[10px]">
                      {supplierSort === 'asc' ? '▲' : supplierSort === 'desc' ? '▼' : '↕'}
                    </span>
                  </button>
                </th>
                {[
                  'Envío',
                  'Código',
                  'Artista',
                  'Título',
                  'Formato',
                  'Tipo',
                  'Precio unitario (EUR)',
                  'Cantidad',
                  'Total de línea (EUR)',
                  'Costo extra (EUR)',
                  'Costo real (EUR)',
                  'Costo real (UYU)',
                  'Markup',
                  'Precio final de venta (UYU)',
                  'Actualizar',
                ].map(label => (
                  <th
                    key={label}
                    className="sticky top-0 z-20 border-b border-slate-200 bg-slate-50 px-4 py-3 text-xs font-semibold uppercase tracking-wider text-slate-500 dark:border-stone-800 dark:bg-stone-950 dark:text-stone-500"
                  >
                    {label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-stone-800">
              {sortedPreview.map(row => {
                const selected = selectedIds.has(row.idDisco)
                const feedback = markupFeedback[row.idDisco]
                const savingMarkup = savingMarkupId === row.idDisco
                const stickyCellClass = selected
                  ? 'sticky left-0 z-10 bg-stone-100 px-4 py-2 dark:bg-stone-900'
                  : 'sticky left-0 z-10 bg-white px-4 py-2 dark:bg-stone-950'

                return (
                  <tr key={row.idDisco} className={selected ? 'bg-stone-50 dark:bg-stone-900/40' : 'bg-white dark:bg-stone-950'}>
                    <td className={stickyCellClass}>
                      <input
                        type="checkbox"
                        checked={selected}
                        onChange={() => toggleRow(row.idDisco)}
                        aria-label={`Seleccionar disco ${row.artist || row.title || row.idDisco}`}
                      />
                    </td>
                    <td className="px-4 py-2 text-slate-600 dark:text-stone-400">{row.invoiceNumber || '—'}</td>
                    <td className="px-4 py-2 text-slate-600 dark:text-stone-400">{dateLabel(row.invoiceDate)}</td>
                    <td className="px-4 py-2 text-slate-600 dark:text-stone-400">{row.supplier || '—'}</td>
                    <td className="px-4 py-2 text-slate-700 dark:text-stone-300">{row.shipping || '—'}</td>
                    <td className="px-4 py-2 text-slate-600 dark:text-stone-400">{row.code || '—'}</td>
                    <td className="px-4 py-2 font-medium text-slate-900 dark:text-white">{row.artist || '—'}</td>
                    <td className="px-4 py-2 text-slate-600 dark:text-stone-400">{row.title || '—'}</td>
                    <td className="px-4 py-2 text-slate-600 dark:text-stone-400">{row.format || '—'}</td>
                    <td className="px-4 py-2 text-slate-600 dark:text-stone-400">{TYPE_LABELS[row.type] || row.type || '—'}</td>
                    <td className="px-4 py-2 tabular-nums text-slate-700 dark:text-stone-300">{formatDecimal(row.unitPriceEur)}</td>
                    <td className="px-4 py-2 tabular-nums text-slate-700 dark:text-stone-300">{row.quantity ?? '—'}</td>
                    <td className="px-4 py-2 tabular-nums text-slate-700 dark:text-stone-300">{formatDecimal(row.unitLineTotalEur)}</td>
                    <td className="px-4 py-2 tabular-nums text-slate-700 dark:text-stone-300">{formatDecimal(row.extraCostEur)}</td>
                    <td className="px-4 py-2 tabular-nums text-slate-700 dark:text-stone-300">{formatDecimal(row.realCostEur)}</td>
                    <td className="px-4 py-2 tabular-nums text-slate-700 dark:text-stone-300">{formatDecimal(row.realCostUyu)}</td>
                    <td className="px-4 py-2 align-middle">
                      <div className="flex items-center">
                        <input
                          type="number"
                          className={`input h-9 min-w-[8rem] py-1.5 text-sm ${feedback?.type === 'error' ? 'border-red-400 dark:border-red-700' : ''}`}
                          value={markupDrafts[row.idDisco] ?? ''}
                          step="0.0001"
                          min="0.0001"
                          inputMode="decimal"
                          aria-label={`Markup del disco ${row.artist || row.title || row.idDisco}`}
                          onChange={event => setMarkupDraft(row.idDisco, event.target.value)}
                          onKeyDown={event => {
                            if (event.key === 'Enter') {
                              event.preventDefault()
                              saveMarkup(row)
                            }
                          }}
                        />
                      </div>
                    </td>
                    <td className="px-4 py-2 tabular-nums font-semibold text-slate-900 dark:text-white">{formatDecimal(row.finalSalePriceUyu)}</td>
                    <td className="px-4 py-2">
                      <div className="flex flex-col items-start gap-1">
                        <button
                          type="button"
                          className="btn-secondary inline-flex h-9 min-w-[140px] items-center justify-center whitespace-nowrap px-4 py-1.5 text-xs"
                          onClick={() => saveMarkup(row)}
                          disabled={savingMarkup}
                          aria-label={`Guardar markup del disco ${row.artist || row.title || row.idDisco}`}
                        >
                          {savingMarkup ? 'Guardando…' : 'Guardar markup'}
                        </button>
                        {feedback ? (
                          <p className={`text-[11px] leading-tight ${feedback.type === 'success' ? 'text-emerald-600 dark:text-emerald-400' : 'text-red-600 dark:text-red-400'}`}>
                            {feedback.message}
                          </p>
                        ) : null}
                      </div>
                    </td>
                  </tr>
                )
              })}
              {!loading && preview.length > 0 && filteredPreview.length === 0 ? (
                <tr>
                  <td colSpan={19} className="px-4 py-10 text-center text-slate-500 dark:text-stone-500">
                    {normalizedSearch ? 'Sin resultados para esta búsqueda' : 'Sin resultados para este filtro'}
                  </td>
                </tr>
              ) : null}
              {!loading && preview.length === 0 ? (
                <tr>
                  <td colSpan={19} className="px-4 py-10 text-center text-slate-500 dark:text-stone-500">
                    No hay discos para recalcular.
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </section>

      <SelectedApplyDialog
        open={selectedDialogOpen}
        count={selectedCount}
        loading={saving === 'selected'}
        onCancel={() => {
          if (saving === 'selected') return
          setSelectedDialogOpen(false)
        }}
        onConfirm={applySelected}
      />
    </div>
  )
}

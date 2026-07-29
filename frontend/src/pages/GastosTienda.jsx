import { useEffect, useMemo, useState } from 'react'
import { api } from '../api/sonograma'
import { CATEGORY_LABELS, EXPENSE_CATEGORIES } from './gastosCategorias'

const UNCATEGORIZED = '__UNCATEGORIZED__'

function fmtMoney(value) {
  return `UYU $${Number(value || 0).toLocaleString('es-UY', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function emptyForm() {
  return { fecha: new Date().toISOString().slice(0, 10), categoria: '', descripcion: '', monto: '' }
}

function currentMonth() {
  return new Date().toISOString().slice(0, 7)
}

function categoryKey(item) {
  return item.categoria || UNCATEGORIZED
}

function categoryLabel(category) {
  if (category === UNCATEGORIZED) return 'Sin categoría'
  return CATEGORY_LABELS[category] || String(category).replaceAll('_', ' ')
}

function sortNewestFirst(a, b) {
  const dateOrder = String(b.fecha || '').localeCompare(String(a.fecha || ''))
  if (dateOrder !== 0) return dateOrder
  return Number(b.idGasto || 0) - Number(a.idGasto || 0)
}

function ExpenseCard({ item, onEdit, onRemove }) {
  return (
    <article className="rounded-lg border border-slate-200 bg-slate-50 p-3 dark:border-stone-800 dark:bg-stone-950/40">
      <div className="flex items-start justify-between gap-3">
        <time dateTime={item.fecha || undefined} className="text-xs font-semibold uppercase tracking-wider text-slate-500 dark:text-stone-500">
          {item.fecha || 'Sin fecha'}
        </time>
        <p className="shrink-0 whitespace-nowrap text-sm font-bold tabular-nums text-slate-900 dark:text-white">{fmtMoney(item.monto)}</p>
      </div>
      <p className="mt-3 break-words text-sm text-slate-900 dark:text-white">{item.descripcion || 'Sin motivo'}</p>
      <div className="mt-3 flex flex-wrap gap-1.5">
        <button type="button" className="btn-secondary px-2 py-1.5 text-xs" onClick={() => onEdit(item)}>Editar</button>
        <button type="button" className="btn-secondary px-2 py-1.5 text-xs text-red-600 dark:text-red-400" onClick={() => onRemove(item.idGasto)}>Eliminar</button>
      </div>
    </article>
  )
}

function ExpenseCategoryColumn({ category, items, onEdit, onRemove }) {
  const monthlySubtotal = items
    .filter(item => String(item.fecha || '').startsWith(currentMonth()))
    .reduce((sum, item) => sum + Number(item.monto || 0), 0)

  return (
    <section className="card flex min-w-0 flex-col p-4" aria-labelledby={`gasto-categoria-${category}`}>
      <header className="flex items-start justify-between gap-3 border-b border-slate-100 pb-3 dark:border-stone-800">
        <div className="min-w-0">
          <h2 id={`gasto-categoria-${category}`} className="break-words text-sm font-bold uppercase tracking-wider text-slate-900 dark:text-white">{categoryLabel(category)}</h2>
          <p className="mt-1 text-xs text-slate-500 dark:text-stone-500">{items.length} {items.length === 1 ? 'gasto registrado' : 'gastos registrados'}</p>
        </div>
        <p className="shrink-0 whitespace-nowrap text-sm font-bold tabular-nums text-slate-900 dark:text-white">{fmtMoney(monthlySubtotal)}</p>
      </header>
      <div className="mt-3 space-y-3">
        {items.map(item => <ExpenseCard key={item.idGasto} item={item} onEdit={onEdit} onRemove={onRemove} />)}
      </div>
    </section>
  )
}

function ExpenseCategoryBoard({ groups, onEdit, onRemove }) {
  if (groups.length === 0) {
    return <div className="px-4 py-10 text-center text-sm text-slate-400 dark:text-stone-500">No hay gastos registrados.</div>
  }

  return (
    <div className="overflow-x-auto overscroll-x-contain">
      <div className="grid gap-4 sm:grid-cols-[repeat(auto-fit,minmax(280px,1fr))] lg:min-w-full lg:grid-flow-col lg:grid-cols-none lg:auto-cols-[minmax(280px,1fr)]">
        {groups.map(group => (
          <ExpenseCategoryColumn
            key={group.category}
            category={group.category}
            items={group.items}
            onEdit={onEdit}
            onRemove={onRemove}
          />
        ))}
      </div>
    </div>
  )
}

export default function GastosTienda() {
  const [items, setItems] = useState([])
  const [editingId, setEditingId] = useState(null)
  const [form, setForm] = useState(emptyForm)
  const [categoryFilter, setCategoryFilter] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    api.gastosTienda.listar().then(setItems).catch(() => setItems([]))
  }, [])

  const filteredItems = useMemo(
    () => categoryFilter === UNCATEGORIZED
      ? items.filter(item => !item.categoria)
      : categoryFilter ? items.filter(item => item.categoria === categoryFilter) : items,
    [categoryFilter, items],
  )

  const categoryOptions = useMemo(() => {
    const options = [...EXPENSE_CATEGORIES]
    const knownValues = new Set(options.map(category => category.value))
    items.forEach(item => {
      if (item.categoria && !knownValues.has(item.categoria)) {
        options.push({ value: item.categoria, label: categoryLabel(item.categoria) })
        knownValues.add(item.categoria)
      }
    })
    if (items.some(item => !item.categoria)) options.push({ value: UNCATEGORIZED, label: 'Sin categoría' })
    return options
  }, [items])

  const groupedItems = useMemo(() => {
    const groups = new Map()
    filteredItems.forEach(item => {
      const category = categoryKey(item)
      const group = groups.get(category)
      if (group) group.push(item)
      else groups.set(category, [item])
    })

    const order = new Map(categoryOptions.map((category, index) => [category.value, index]))
    return [...groups.entries()]
      .sort(([categoryA], [categoryB]) => (order.get(categoryA) ?? Number.MAX_SAFE_INTEGER) - (order.get(categoryB) ?? Number.MAX_SAFE_INTEGER))
      .map(([category, categoryItems]) => ({ category, items: [...categoryItems].sort(sortNewestFirst) }))
  }, [categoryOptions, filteredItems])

  const totalMes = useMemo(() => filteredItems
    .filter(item => String(item.fecha || '').startsWith(currentMonth()))
    .reduce((sum, item) => sum + Number(item.monto || 0), 0), [filteredItems])

  const totalLabel = categoryFilter
    ? `TOTAL · ${categoryLabel(categoryFilter).toUpperCase()}`
    : 'TOTAL DEL MES'

  function resetForm() {
    setEditingId(null)
    setForm(emptyForm())
  }

  async function submit(e) {
    e.preventDefault()
    setError('')
    if (!form.categoria) {
      setError('Seleccioná una categoría para el gasto.')
      return
    }
    try {
      const payload = { ...form, monto: Number(form.monto) }
      if (editingId) {
        const updated = await api.gastosTienda.actualizar(editingId, payload)
        setItems(prev => prev.map(item => item.idGasto === editingId ? updated : item))
      } else {
        const created = await api.gastosTienda.crear(payload)
        setItems(prev => [created, ...prev])
      }
      resetForm()
    } catch (err) {
      setError(err.message || 'No se pudo guardar el gasto')
    }
  }

  async function remove(id) {
    setError('')
    try {
      await api.gastosTienda.eliminar(id)
      setItems(prev => prev.filter(item => item.idGasto !== id))
      if (editingId === id) resetForm()
    } catch (err) {
      setError(err.message || 'No se pudo eliminar el gasto')
    }
  }

  function edit(item) {
    setEditingId(item.idGasto)
    setForm({
      fecha: item.fecha || new Date().toISOString().slice(0, 10),
      categoria: item.categoria || '',
      descripcion: item.descripcion || '',
      monto: String(item.monto ?? ''),
    })
    setError('')
  }

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 py-6 space-y-5">
      <div className="flex items-center justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-xl font-bold text-slate-900 dark:text-white">Gastos de tienda</h1>
          <p className="text-slate-400 dark:text-stone-500 text-sm mt-0.5">Registro manual de gastos del local.</p>
        </div>
        <div className="card px-4 py-3">
          <p className="text-xs uppercase tracking-wider text-slate-500 dark:text-stone-500">{totalLabel}</p>
          <p className="mt-1 text-xl font-bold text-slate-900 dark:text-white">{fmtMoney(totalMes)}</p>
        </div>
      </div>

      <div className="grid gap-5 lg:grid-cols-[380px_minmax(0,1fr)]">
        <form noValidate onSubmit={submit} className="card p-5 space-y-4">
          <div>
            <label htmlFor="gasto-fecha" className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Fecha</label>
            <input id="gasto-fecha" className="input" type="date" value={form.fecha} onChange={e => setForm(prev => ({ ...prev, fecha: e.target.value }))} />
          </div>
          <div>
            <label htmlFor="gasto-categoria" className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">CATEGORÍA</label>
            <select id="gasto-categoria" className="input" required value={form.categoria} onChange={e => setForm(prev => ({ ...prev, categoria: e.target.value }))}>
              <option value="">Seleccionar categoría</option>
              {EXPENSE_CATEGORIES.map(category => <option key={category.value} value={category.value}>{category.label}</option>)}
            </select>
          </div>
          <div>
            <label htmlFor="gasto-motivo" className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Motivo</label>
            <input id="gasto-motivo" className="input" value={form.descripcion} onChange={e => setForm(prev => ({ ...prev, descripcion: e.target.value }))} />
          </div>
          <div>
            <label htmlFor="gasto-monto" className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Monto</label>
            <input id="gasto-monto" className="input" type="number" min="0" step="0.01" value={form.monto} onChange={e => setForm(prev => ({ ...prev, monto: e.target.value }))} />
          </div>
          {error && <div role="alert" className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-900 dark:bg-red-950/40 dark:text-red-300">{error}</div>}
          <div className="flex gap-2">
            <button className="btn-primary flex-1">{editingId ? 'Guardar cambios' : 'Agregar gasto'}</button>
            {editingId && <button type="button" className="btn-secondary" onClick={resetForm}>Cancelar</button>}
          </div>
        </form>

        <div className="card min-w-0 overflow-hidden">
          <div className="flex items-center justify-between gap-3 border-b border-slate-100 dark:border-stone-800 px-4 py-3">
            <label htmlFor="gasto-filtro-categoria" className="text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider">Filtrar por categoría</label>
            <select id="gasto-filtro-categoria" className="input max-w-xs" value={categoryFilter} onChange={e => setCategoryFilter(e.target.value)}>
              <option value="">Todas</option>
              {categoryOptions.map(category => <option key={category.value} value={category.value}>{category.label}</option>)}
            </select>
          </div>
          <ExpenseCategoryBoard groups={groupedItems} onEdit={edit} onRemove={remove} />
        </div>
      </div>
    </div>
  )
}

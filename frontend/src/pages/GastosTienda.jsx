import { useEffect, useMemo, useState } from 'react'
import { api } from '../api/sonograma'
import { CATEGORY_LABELS, EXPENSE_CATEGORIES } from './gastosCategorias'

const UNCATEGORIZED = '__UNCATEGORIZED__'

function fmtMoney(value) {
  return `UYU $${Number(value || 0).toLocaleString('es-UY', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function fechaInputLocal(date = new Date()) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function currentMonth() {
  return fechaInputLocal().slice(0, 7)
}

function fmtDate(value) {
  if (!value) return '—'
  return new Date(`${value}T00:00:00`).toLocaleDateString('es-UY', { day: '2-digit', month: '2-digit', year: 'numeric' })
}

function emptyForm() {
  return { fecha: fechaInputLocal(), categoria: '', descripcion: '', monto: '' }
}

function categoryLabel(category) {
  return category ? (CATEGORY_LABELS[category] || category) : 'Sin categoría'
}

function categoryFilterMatches(item, filter) {
  if (!filter) return true
  if (filter === UNCATEGORIZED) return !item.categoria
  return item.categoria === filter
}

function searchMatches(item, query) {
  const normalizedQuery = query.trim().toLocaleLowerCase('es-UY')
  if (!normalizedQuery) return true

  const amount = Number(item.monto || 0)
  const searchable = [
    item.descripcion,
    item.categoria,
    categoryLabel(item.categoria),
    String(item.monto ?? ''),
    amount.toLocaleString('es-UY', { minimumFractionDigits: 2, maximumFractionDigits: 2 }),
    fmtMoney(amount),
  ].filter(Boolean).join(' ').toLocaleLowerCase('es-UY')

  return searchable.includes(normalizedQuery)
}

function sortableValue(item, key) {
  if (key === 'fecha') return String(item.fecha || '')
  if (key === 'categoria') return categoryLabel(item.categoria).toLocaleLowerCase('es-UY')
  return Number(item.monto || 0)
}

function compareValues(a, b, key) {
  const first = sortableValue(a, key)
  const second = sortableValue(b, key)
  if (key === 'monto') return first - second
  return first.localeCompare(second, 'es-UY', { sensitivity: 'base' })
}

function SortIcon({ active, direction }) {
  return (
    <span aria-hidden="true" className={`inline-flex ml-1 align-[-1px] text-[10px] ${active ? 'text-slate-700 dark:text-stone-200' : 'text-slate-300 dark:text-stone-600'}`}>
      {active ? (direction === 'asc' ? '↑' : '↓') : '↕'}
    </span>
  )
}

function SortHeader({ label, sortKey, sort, onSort }) {
  const active = sort.key === sortKey
  const direction = active ? sort.direction : 'asc'
  return (
    <th scope="col" aria-sort={active ? (sort.direction === 'asc' ? 'ascending' : 'descending') : 'none'} className="px-2 sm:px-3 py-2.5 text-left text-[11px] font-semibold uppercase tracking-[0.02em] text-slate-500 dark:text-stone-500">
      <button
        type="button"
        onClick={() => onSort(sortKey)}
        aria-label={`Ordenar por ${label}`}
        className="inline-flex items-center whitespace-nowrap rounded focus:outline-none focus-visible:ring-2 focus-visible:ring-stone-400/50"
      >
        {label}<SortIcon active={active} direction={direction} />
      </button>
    </th>
  )
}

export default function GastosTienda() {
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(true)
  const [editingId, setEditingId] = useState(null)
  const [form, setForm] = useState(emptyForm)
  const [periodo, setPeriodo] = useState(currentMonth)
  const [search, setSearch] = useState('')
  const [categoryFilter, setCategoryFilter] = useState('')
  const [sort, setSort] = useState({ key: null, direction: 'asc' })
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    api.gastosTienda.listar()
      .then(data => { if (!cancelled) setItems(data) })
      .catch(err => { if (!cancelled) setError(err.message || 'No se pudieron cargar los gastos secundarios.') })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [])

  const filteredItems = useMemo(() => {
    const visible = items.filter(item => (
      String(item.fecha || '').startsWith(periodo)
      && categoryFilterMatches(item, categoryFilter)
      && searchMatches(item, search)
    ))

    if (!sort.key) return visible
    return [...visible].sort((a, b) => {
      const comparison = compareValues(a, b, sort.key)
      return sort.direction === 'asc' ? comparison : -comparison
    })
  }, [categoryFilter, items, periodo, search, sort])

  const summary = useMemo(() => {
    const categoryTotals = filteredItems.reduce((totals, item) => {
      const key = item.categoria || UNCATEGORIZED
      totals.set(key, (totals.get(key) || 0) + Number(item.monto || 0))
      return totals
    }, new Map())

    return {
      total: filteredItems.reduce((total, item) => total + Number(item.monto || 0), 0),
      categories: [...categoryTotals.entries()]
        .sort(([first], [second]) => categoryLabel(first === UNCATEGORIZED ? '' : first).localeCompare(categoryLabel(second === UNCATEGORIZED ? '' : second), 'es-UY', { sensitivity: 'base' }))
        .map(([key, total]) => ({ key, label: categoryLabel(key === UNCATEGORIZED ? '' : key), total })),
    }
  }, [filteredItems])

  function resetForm() {
    setEditingId(null)
    setForm(emptyForm())
  }

  function applyFilters() {
    setSearch(value => value.trim())
  }

  function clearFilters() {
    setPeriodo(currentMonth())
    setSearch('')
    setCategoryFilter('')
  }

  function toggleSort(key) {
    setSort(previous => previous.key === key
      ? { key, direction: previous.direction === 'asc' ? 'desc' : 'asc' }
      : { key, direction: 'asc' })
  }

  async function submit(e) {
    e.preventDefault()
    setError('')
    if (!form.categoria) {
      setError('Seleccioná una categoría para el gasto secundario.')
      return
    }
    const amount = Number(form.monto)
    if (form.monto === '' || !Number.isFinite(amount) || amount < 0) {
      setError('Ingresá un monto válido que no sea negativo.')
      return
    }
    try {
      const payload = { ...form, monto: amount }
      if (editingId) {
        const updated = await api.gastosTienda.actualizar(editingId, payload)
        setItems(previous => previous.map(item => item.idGasto === editingId ? updated : item))
      } else {
        const created = await api.gastosTienda.crear(payload)
        setItems(previous => [created, ...previous])
      }
      resetForm()
    } catch (err) {
      setError(err.message || 'No se pudo guardar el gasto secundario.')
    }
  }

  async function remove(id) {
    setError('')
    try {
      await api.gastosTienda.eliminar(id)
      setItems(previous => previous.filter(item => item.idGasto !== id))
      if (editingId === id) resetForm()
    } catch (err) {
      setError(err.message || 'No se pudo eliminar el gasto secundario.')
    }
  }

  function edit(item) {
    setEditingId(item.idGasto)
    setForm({
      fecha: item.fecha || fechaInputLocal(),
      categoria: item.categoria || '',
      descripcion: item.descripcion || '',
      monto: String(item.monto ?? ''),
    })
    setError('')
  }

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 py-6 space-y-5">
      <div>
        <h1 className="text-xl font-bold text-slate-900 dark:text-white">Gastos secundarios</h1>
        <p className="text-slate-400 dark:text-stone-500 text-sm mt-0.5">Registro manual de gastos secundarios del local.</p>
      </div>

      <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-5 gap-3">
        <div className="card p-4 text-center">
          <p className="text-xs uppercase tracking-wider text-slate-400 dark:text-stone-500">Total gastos secundarios</p>
          <p className="text-xl font-bold mt-1 tabular-nums text-slate-900 dark:text-white">{fmtMoney(summary.total)}</p>
        </div>
        {summary.categories.map(category => (
          <div key={category.key} className="card p-4 text-center">
            <p className="text-xs uppercase tracking-wider text-slate-400 dark:text-stone-500">{category.label}</p>
            <p className="text-xl font-bold mt-1 tabular-nums text-slate-900 dark:text-white">{fmtMoney(category.total)}</p>
          </div>
        ))}
      </div>

      <div className="card p-4">
        <div className="flex flex-wrap gap-3 items-end">
          <div>
            <label htmlFor="gasto-periodo" className="block text-xs text-slate-500 dark:text-stone-400 mb-1">Mes a analizar</label>
            <input id="gasto-periodo" type="month" value={periodo} max={currentMonth()} onChange={e => setPeriodo(e.target.value || currentMonth())} className="input text-sm" />
          </div>
          <div className="flex-1 min-w-[12rem]">
            <label htmlFor="gasto-busqueda" className="block text-xs text-slate-500 dark:text-stone-400 mb-1">Buscar</label>
            <input id="gasto-busqueda" type="text" placeholder="Motivo, categoría o monto…" value={search} onChange={e => setSearch(e.target.value)} onKeyDown={e => e.key === 'Enter' && applyFilters()} className="input text-sm" />
          </div>
          <div>
            <label htmlFor="gasto-filtro-categoria" className="block text-xs text-slate-500 dark:text-stone-400 mb-1">Filtrar por categoría</label>
            <select id="gasto-filtro-categoria" className="input text-sm min-w-44" value={categoryFilter} onChange={e => setCategoryFilter(e.target.value)}>
              <option value="">Todas</option>
              <option value={UNCATEGORIZED}>Sin categoría</option>
              {EXPENSE_CATEGORIES.map(category => <option key={category.value} value={category.value}>{category.label}</option>)}
            </select>
          </div>
          <button type="button" onClick={applyFilters} className="btn-primary text-sm">Filtrar</button>
          <button type="button" onClick={clearFilters} className="btn-secondary text-sm">Limpiar</button>
        </div>
      </div>

      {error && <div role="alert" className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-900 dark:bg-red-950/40 dark:text-red-300">{error}</div>}

      <div className="grid gap-5 lg:grid-cols-[minmax(0,3fr)_minmax(280px,1fr)] items-start">
        <div className="card overflow-hidden min-w-0">
          <div className="overflow-x-auto">
            <table className="w-full table-fixed text-[12.5px] sm:text-[13px]">
              <colgroup>
                <col className="w-[15%] sm:w-[14%]" />
                <col className="w-[20%] sm:w-[21%]" />
                <col className="w-[27%] sm:w-[30%]" />
                <col className="w-[17%] sm:w-[18%]" />
                <col className="w-[21%] sm:w-[17%]" />
              </colgroup>
              <thead>
                <tr className="border-b border-slate-100 dark:border-stone-800">
                  <SortHeader label="Fecha" sortKey="fecha" sort={sort} onSort={toggleSort} />
                  <SortHeader label="Categoría" sortKey="categoria" sort={sort} onSort={toggleSort} />
                  <th scope="col" className="px-2 sm:px-3 py-2.5 text-left text-[11px] font-semibold uppercase tracking-[0.02em] text-slate-500 dark:text-stone-500">Motivo</th>
                  <SortHeader label="Monto" sortKey="monto" sort={sort} onSort={toggleSort} />
                  <th scope="col" className="px-2 sm:px-3 py-2.5 text-left text-[11px] font-semibold uppercase tracking-[0.02em] text-slate-500 dark:text-stone-500">Acciones</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-stone-800">
                {loading ? (
                  <tr><td colSpan={5} className="px-3 py-8 text-center text-slate-400 dark:text-stone-500">Cargando gastos secundarios…</td></tr>
                ) : filteredItems.length === 0 ? (
                  <tr><td colSpan={5} className="px-3 py-8 text-center text-slate-400 dark:text-stone-500">No hay gastos secundarios para este período.</td></tr>
                ) : filteredItems.map(item => (
                  <tr key={item.idGasto} className="align-middle hover:bg-slate-50 dark:hover:bg-stone-900/50 transition-colors">
                    <td className="px-2 sm:px-3 py-2 whitespace-nowrap text-slate-700 dark:text-stone-300">{fmtDate(item.fecha)}</td>
                    <td className="px-2 sm:px-3 py-2 text-slate-600 dark:text-stone-400"><span className="block truncate" title={categoryLabel(item.categoria)}>{categoryLabel(item.categoria)}</span></td>
                    <td className="px-2 sm:px-3 py-2 text-slate-900 dark:text-white"><div className="line-clamp-2 leading-4" title={item.descripcion}>{item.descripcion}</div></td>
                    <td className="px-2 sm:px-3 py-2 tabular-nums font-medium text-slate-800 dark:text-stone-200 whitespace-nowrap">{fmtMoney(item.monto)}</td>
                    <td className="px-2 sm:px-3 py-2">
                      <div className="flex flex-nowrap gap-1">
                        <button type="button" className="btn-secondary whitespace-nowrap text-[10px] sm:text-xs px-1.5 sm:px-2 py-1" onClick={() => edit(item)}>Editar</button>
                        <button type="button" className="btn-secondary whitespace-nowrap text-[10px] sm:text-xs px-1.5 sm:px-2 py-1 text-red-600 dark:text-red-400" onClick={() => remove(item.idGasto)}>Eliminar</button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <form noValidate onSubmit={submit} className="card p-4 sm:p-5 space-y-3.5">
          <div>
            <h2 className="font-semibold text-slate-900 dark:text-white">{editingId ? 'Editar gasto secundario' : 'Nuevo gasto secundario'}</h2>
            <p className="text-xs text-slate-400 dark:text-stone-500 mt-0.5">Completá los datos del gasto secundario.</p>
          </div>
          <div>
            <label htmlFor="gasto-fecha" className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Fecha</label>
            <input id="gasto-fecha" className="input" type="date" value={form.fecha} onChange={e => setForm(previous => ({ ...previous, fecha: e.target.value }))} />
          </div>
          <div>
            <label htmlFor="gasto-categoria" className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Categoría</label>
            <select id="gasto-categoria" className="input" required value={form.categoria} onChange={e => setForm(previous => ({ ...previous, categoria: e.target.value }))}>
              <option value="">Seleccionar categoría</option>
              {EXPENSE_CATEGORIES.map(category => <option key={category.value} value={category.value}>{category.label}</option>)}
            </select>
          </div>
          <div>
            <label htmlFor="gasto-motivo" className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Motivo</label>
            <input id="gasto-motivo" className="input" value={form.descripcion} onChange={e => setForm(previous => ({ ...previous, descripcion: e.target.value }))} />
          </div>
          <div>
            <label htmlFor="gasto-monto" className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Monto</label>
            <input id="gasto-monto" className="input" type="number" min="0" step="0.01" required value={form.monto} onChange={e => setForm(previous => ({ ...previous, monto: e.target.value }))} />
          </div>
          <div className="flex gap-2 pt-1">
            <button className="btn-primary flex-1">{editingId ? 'Guardar cambios' : 'Agregar gasto'}</button>
            {editingId && <button type="button" className="btn-secondary" onClick={resetForm}>Cancelar</button>}
          </div>
        </form>
      </div>
    </div>
  )
}

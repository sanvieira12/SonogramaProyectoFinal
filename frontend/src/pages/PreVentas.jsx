import { useEffect, useMemo, useRef, useState } from 'react'
import { api, resolveApiUrl } from '../api/sonograma'
import ConfirmModal from '../components/ConfirmModal'

const emptyManualForm = () => ({ descripcion: '', codigo: '', cantidad: '1', precio: '', notas: '' })
const emptyMetaForm = () => ({ fecha: new Date().toISOString().slice(0, 10), notas: '' })

const ESTADO_LABELS = {
  DISPONIBLE: 'Disponible',
  RESERVADO: 'Reservado',
  VENDIDO: 'Vendido',
  FUERA_STOCK: 'Fuera de stock',
  DESCONTINUADO: 'Descontinuado',
}

function money(value) {
  return value == null || value === '' ? '—' : `UYU $${Number(value).toLocaleString('es-UY', { maximumFractionDigits: 2 })}`
}

function fmtDate(value) {
  if (!value) return '—'
  const [year, month, day] = String(value).slice(0, 10).split('-')
  if (!year || !month || !day) return value
  return `${day}/${month}/${year}`
}

function asDateStamp(value) {
  const stamp = Date.parse(`${value}T00:00:00`)
  return Number.isFinite(stamp) ? stamp : 0
}

function normalizeText(value) {
  return String(value || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
}

function selectedPrice(selectedItem) {
  return Number(selectedItem?.precio || 0)
}

function selectedQuantity(selectedItem) {
  const qty = Number(selectedItem?.cantidad || 0)
  return Number.isFinite(qty) && qty > 0 ? qty : 1
}

function buildSelectedFromDisco(disco) {
  return {
    mode: 'catalogo',
    idDisco: disco.idDisco,
    artista: disco.artista || '',
    album: disco.album || '',
    descripcion: '',
    codigoDisco: disco.codigoInterno || '',
    cantidad: '1',
    precio: disco.precioVenta != null ? String(Number(disco.precioVenta)) : '',
    notas: '',
    imagenUrl: disco.imagenUrl || '',
    estado: disco.estado || '',
    selloDiscografico: disco.selloDiscografico || '',
  }
}

function buildSelectedFromManual(manualForm) {
  return {
    mode: 'manual',
    idDisco: null,
    artista: '',
    album: '',
    descripcion: manualForm.descripcion.trim(),
    codigoDisco: manualForm.codigo.trim(),
    cantidad: manualForm.cantidad,
    precio: manualForm.precio,
    notas: manualForm.notas.trim(),
    imagenUrl: '',
    estado: '',
    selloDiscografico: '',
  }
}

export default function PreVentas() {
  const clientSearchRef = useRef(null)
  const listSectionRef = useRef(null)
  const [preVentas, setPreVentas] = useState([])
  const [clientes, setClientes] = useState([])
  const [discos, setDiscos] = useState([])
  const [saving, setSaving] = useState(false)
  const [busyId, setBusyId] = useState(null)
  const [deleting, setDeleting] = useState(null)
  const [error, setError] = useState('')

  const [selectedItem, setSelectedItem] = useState(null)
  const [metaForm, setMetaForm] = useState(emptyMetaForm)
  const [showManualForm, setShowManualForm] = useState(false)
  const [manualForm, setManualForm] = useState(emptyManualForm)
  const [discoQuery, setDiscoQuery] = useState('')
  const [selectedDiscoDetalle, setSelectedDiscoDetalle] = useState(null)
  const [clienteQuery, setClienteQuery] = useState('')
  const [selectedCliente, setSelectedCliente] = useState(null)
  const [showClienteResults, setShowClienteResults] = useState(false)
  const [showClienteModal, setShowClienteModal] = useState(false)
  const [sortOrder, setSortOrder] = useState('desc')
  const [showCreateForm, setShowCreateForm] = useState(false)

  useEffect(() => {
    api.preVentas.listar().then(setPreVentas).catch(() => setPreVentas([]))
    api.clientes.todos().then(setClientes).catch(() => setClientes([]))
    api.discos.todos().then(setDiscos).catch(() => setDiscos([]))
  }, [])

  const discosById = useMemo(
    () => new Map(discos.map(d => [String(d.idDisco), d])),
    [discos],
  )

  const filteredDiscos = useMemo(() => {
    const q = normalizeText(discoQuery.trim())
    const pool = [...discos].sort((a, b) => {
      const aDisponible = a.estado === 'DISPONIBLE' ? -1 : 0
      const bDisponible = b.estado === 'DISPONIBLE' ? -1 : 0
      if (aDisponible !== bDisponible) return aDisponible - bDisponible
      return `${a.artista || ''} ${a.album || ''}`.localeCompare(`${b.artista || ''} ${b.album || ''}`)
    })
    if (!q) return pool.slice(0, 10)
    return pool.filter(d => [d.artista, d.album, d.codigoInterno, d.selloDiscografico].some(field => normalizeText(field).includes(q))).slice(0, 12)
  }, [discoQuery, discos])

  const filteredClientes = useMemo(() => {
    const q = normalizeText(clienteQuery.trim())
    if (!q) return clientes.slice(0, 8)
    return clientes.filter(c => [
      [c.nombre, c.apellido].filter(Boolean).join(' '),
      c.cedula,
      c.instagramUsuario,
      c.telefono,
      c.direccion,
    ].some(field => normalizeText(field).includes(q))).slice(0, 10)
  }, [clienteQuery, clientes])

  const sortedPreVentas = useMemo(() => {
    const dir = sortOrder === 'asc' ? 1 : -1
    return [...preVentas].sort((a, b) => {
      const dateDiff = (asDateStamp(a.fecha) - asDateStamp(b.fecha)) * dir
      if (dateDiff !== 0) return dateDiff
      return ((a.idPreVenta || 0) - (b.idPreVenta || 0)) * dir
    })
  }, [preVentas, sortOrder])

  const totalSeleccionado = useMemo(
    () => selectedPrice(selectedItem) * selectedQuantity(selectedItem),
    [selectedItem],
  )

  function handleAddCatalogDisco(disco) {
    setSelectedItem(buildSelectedFromDisco(disco))
    setSelectedDiscoDetalle(null)
    setShowManualForm(false)
    setManualForm(emptyManualForm())
    setError('')
  }

  function handleAddManual() {
    const description = manualForm.descripcion.trim()
    const quantity = Number(manualForm.cantidad || 0)
    const price = Number(manualForm.precio || 0)
    if (!description || quantity <= 0 || price <= 0) {
      setError('Completá descripción, cantidad y precio para el disco fuera de catálogo.')
      return
    }
    setSelectedItem(buildSelectedFromManual(manualForm))
    setShowManualForm(false)
    setManualForm(emptyManualForm())
    setError('')
  }

  function handleRemoveSelected() {
    setSelectedItem(null)
  }

  function selectCliente(cliente) {
    setSelectedCliente(cliente)
    setClienteQuery('')
    setShowClienteResults(false)
    setError('')
  }

  async function submit(e) {
    e.preventDefault()
    setSaving(true)
    setError('')
    try {
      if (!selectedCliente) throw new Error('Seleccioná un cliente.')
      if (!selectedItem) throw new Error('Agregá un disco para registrar la pre-venta.')
      if (selectedQuantity(selectedItem) <= 0) throw new Error('La cantidad debe ser mayor a cero.')
      if (selectedPrice(selectedItem) <= 0) throw new Error('Ingresá un precio válido para la pre-venta.')

      const created = await api.preVentas.crear({
        idCliente: Number(selectedCliente.idCliente),
        idDisco: selectedItem.idDisco ? Number(selectedItem.idDisco) : null,
        descripcion: selectedItem.idDisco ? null : selectedItem.descripcion,
        codigoDisco: selectedItem.codigoDisco || null,
        cantidad: selectedQuantity(selectedItem),
        precio: Number(totalSeleccionado.toFixed(2)),
        fecha: metaForm.fecha,
        notas: [metaForm.notas.trim(), selectedItem.mode === 'manual' ? selectedItem.notas.trim() : ''].filter(Boolean).join('\n\n') || null,
      })
      setPreVentas(prev => [created, ...prev])
      setSelectedItem(null)
      setMetaForm(emptyMetaForm())
      setManualForm(emptyManualForm())
      setSelectedCliente(null)
      setClienteQuery('')
      setShowManualForm(false)
      setDiscoQuery('')
      setShowCreateForm(false)
      setTimeout(() => {
        listSectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
      }, 0)
    } catch (err) {
      setError(err.message || 'No se pudo registrar la pre-venta')
      setShowCreateForm(true)
    } finally {
      setSaving(false)
    }
  }

  async function marcarPagada(item) {
    setBusyId(item.idPreVenta)
    setError('')
    try {
      const updated = await api.preVentas.marcarPagada(item.idPreVenta)
      setPreVentas(prev => prev.map(p => p.idPreVenta === updated.idPreVenta ? updated : p))
    } catch (err) {
      setError(err.message || 'No se pudo marcar la pre-venta como pagada')
    } finally {
      setBusyId(null)
    }
  }

  async function eliminar() {
    if (!deleting) return
    setBusyId(deleting.idPreVenta)
    setError('')
    try {
      await api.preVentas.eliminar(deleting.idPreVenta)
      setPreVentas(prev => prev.filter(p => p.idPreVenta !== deleting.idPreVenta))
      setDeleting(null)
    } catch (err) {
      setError(err.message || 'No se pudo eliminar la pre-venta')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 py-6 space-y-5">
      <div>
        <h1 className="text-xl font-bold text-slate-900 dark:text-white">Pre-ventas</h1>
        <p className="text-slate-400 dark:text-stone-500 text-sm mt-0.5">
          Reservas y pedidos anticipados sin tocar el stock físico actual.
        </p>
      </div>

      {error && (
        <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-900 dark:bg-red-950/40 dark:text-red-300">
          {error}
        </div>
      )}

      <section className="card overflow-hidden">
        <button
          type="button"
          aria-expanded={showCreateForm}
          aria-controls="nueva-preventa-panel"
          onClick={() => setShowCreateForm(prev => !prev)}
          className="w-full px-5 py-4 flex items-center justify-between gap-4 text-left transition-colors hover:bg-slate-50 dark:hover:bg-stone-900/60 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#5C7D87] focus-visible:ring-offset-2 focus-visible:ring-offset-white dark:focus-visible:ring-offset-stone-950"
        >
          <div>
            <h2 className="font-semibold text-slate-900 dark:text-white">Nueva preventa</h2>
            <p className="text-sm text-slate-400 dark:text-stone-500 mt-1">
              Abrí este bloque para cargar una nueva reserva sin perder el listado de vista.
            </p>
          </div>
          <ChevronDown className={`h-5 w-5 text-slate-400 dark:text-stone-500 transition-transform duration-200 ${showCreateForm ? 'rotate-180' : ''}`} />
        </button>

        <div
          id="nueva-preventa-panel"
          className={`grid transition-[grid-template-rows,opacity] duration-200 ease-out ${showCreateForm ? 'grid-rows-[1fr] opacity-100' : 'grid-rows-[0fr] opacity-0'}`}
        >
          <div className="overflow-hidden">
            <div className="border-t border-slate-100 dark:border-stone-800 p-5">
              <form onSubmit={submit} className="grid gap-5 xl:grid-cols-[minmax(0,1.3fr)_390px]">
                <div className="space-y-5 min-w-0">
                  <section className="card p-5 space-y-4">
            <div className="flex items-start justify-between gap-4">
              <div>
                <h2 className="text-sm font-semibold text-slate-700 dark:text-stone-300 uppercase tracking-wider">Discos</h2>
                <p className="text-sm text-slate-400 dark:text-stone-500 mt-1">
                  La pre-venta guarda un solo disco por registro. Elegí el ítem a reservar y mantené el flujo visual de selección.
                </p>
              </div>
              {selectedItem && (
                <button type="button" onClick={handleRemoveSelected} className="text-xs text-slate-400 hover:text-red-500 transition-colors">
                  Quitar selección
                </button>
              )}
            </div>

            {selectedItem ? (
              <div className="rounded-xl border border-slate-200 dark:border-stone-700 bg-slate-50 dark:bg-stone-950 p-4">
                <h3 className="text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-3">Disco seleccionado</h3>
                <div className="flex flex-col gap-4 md:flex-row md:items-start">
                  {selectedItem.imagenUrl ? (
                    <img src={resolveApiUrl(selectedItem.imagenUrl)} alt="" className="w-20 h-20 rounded-xl object-cover bg-slate-100 dark:bg-stone-800 flex-shrink-0" />
                  ) : (
                    <div className="w-20 h-20 rounded-xl bg-slate-100 dark:bg-stone-800 flex items-center justify-center text-[11px] text-slate-400 dark:text-stone-500 flex-shrink-0">
                      {selectedItem.mode === 'manual' ? 'Manual' : 'Sin portada'}
                    </div>
                  )}

                  <div className="flex-1 min-w-0 space-y-3">
                    <div>
                      <div className="font-semibold text-slate-900 dark:text-white text-sm">
                        {selectedItem.mode === 'catalogo'
                          ? `${selectedItem.artista || '—'} — ${selectedItem.album || '—'}`
                          : selectedItem.descripcion}
                      </div>
                      <div className="text-xs text-slate-400 dark:text-stone-500 mt-1">
                        {[
                          selectedItem.codigoDisco && `Código ${selectedItem.codigoDisco}`,
                          selectedItem.mode === 'catalogo' && selectedItem.estado ? ESTADO_LABELS[selectedItem.estado] || selectedItem.estado : null,
                          selectedItem.mode === 'catalogo' && selectedItem.selloDiscografico ? selectedItem.selloDiscografico : null,
                        ].filter(Boolean).join(' · ') || 'Sin datos adicionales'}
                      </div>
                    </div>

                    <div className="grid gap-3 sm:grid-cols-3">
                      <Field label="Cantidad">
                        <input
                          required
                          className="input"
                          type="number"
                          min="1"
                          step="1"
                          value={selectedItem.cantidad}
                          onChange={e => setSelectedItem(prev => ({ ...prev, cantidad: e.target.value }))}
                        />
                      </Field>
                      <Field label="Precio unitario">
                        <input
                          required
                          className="input"
                          type="number"
                          min="0.01"
                          step="0.01"
                          value={selectedItem.precio}
                          onChange={e => setSelectedItem(prev => ({ ...prev, precio: e.target.value }))}
                        />
                      </Field>
                      <Field label="Subtotal">
                        <div className="input flex items-center font-semibold text-slate-900 dark:text-white">
                          {money(totalSeleccionado)}
                        </div>
                      </Field>
                    </div>

                    {selectedItem.mode === 'manual' && (
                      <Field label="Notas del disco fuera de catálogo">
                        <textarea
                          className="input min-h-20 resize-y"
                          rows={3}
                          value={selectedItem.notas}
                          onChange={e => setSelectedItem(prev => ({ ...prev, notas: e.target.value }))}
                          placeholder="Proveedor, referencia de compra o aclaraciones..."
                        />
                      </Field>
                    )}
                  </div>
                </div>
              </div>
            ) : (
              <div className="rounded-xl border border-dashed border-slate-200 dark:border-stone-700 px-4 py-5 text-sm text-slate-400 dark:text-stone-500">
                Todavía no hay ningún disco seleccionado.
              </div>
            )}

            <div className="space-y-3">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <button type="button" onClick={() => setShowManualForm(v => !v)} className="btn-secondary text-sm">
                  Agregar disco fuera de catálogo
                </button>
                <p className="text-xs text-slate-400 dark:text-stone-500">
                  El código queda disponible para vincular esta pre-venta con una futura importación o compra.
                </p>
              </div>

              {showManualForm && (
                <div className="rounded-xl border border-slate-200 dark:border-stone-700 bg-slate-50 dark:bg-stone-950 p-4 space-y-3">
                  <div className="grid gap-3 sm:grid-cols-2">
                    <Field label="Descripción / release title">
                      <input
                        className="input"
                        value={manualForm.descripcion}
                        onChange={e => setManualForm(prev => ({ ...prev, descripcion: e.target.value }))}
                        placeholder="Título o release esperado"
                      />
                    </Field>
                    <Field label="Disc code">
                      <input
                        className="input"
                        value={manualForm.codigo}
                        onChange={e => setManualForm(prev => ({ ...prev, codigo: e.target.value }))}
                        placeholder="Future, Discogs o proveedor"
                      />
                    </Field>
                    <Field label="Cantidad">
                      <input
                        className="input"
                        type="number"
                        min="1"
                        step="1"
                        value={manualForm.cantidad}
                        onChange={e => setManualForm(prev => ({ ...prev, cantidad: e.target.value }))}
                      />
                    </Field>
                    <Field label="Precio">
                      <input
                        className="input"
                        type="number"
                        min="0.01"
                        step="0.01"
                        value={manualForm.precio}
                        onChange={e => setManualForm(prev => ({ ...prev, precio: e.target.value }))}
                      />
                    </Field>
                    <Field label="Notas" className="sm:col-span-2">
                      <textarea
                        className="input min-h-20 resize-y"
                        rows={3}
                        value={manualForm.notas}
                        onChange={e => setManualForm(prev => ({ ...prev, notas: e.target.value }))}
                      />
                    </Field>
                  </div>
                  <div className="flex justify-end gap-2">
                    <button type="button" onClick={() => setShowManualForm(false)} className="btn-secondary text-sm">Cancelar</button>
                    <button type="button" onClick={handleAddManual} className="btn-primary text-sm">Agregar ítem</button>
                  </div>
                </div>
              )}

              <div className="relative">
                <SearchIcon className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 dark:text-stone-500" />
                <input
                  value={discoQuery}
                  onChange={e => setDiscoQuery(e.target.value)}
                  placeholder="Buscar disco para agregar..."
                  className="input pl-9"
                />
              </div>

              <div className="space-y-2 max-h-[28rem] overflow-y-auto">
                {filteredDiscos.map(disco => (
                  <div key={disco.idDisco} className="rounded-xl border border-slate-200 dark:border-stone-800 bg-slate-50 dark:bg-stone-950 px-3 py-3 flex items-center gap-3">
                    {disco.imagenUrl ? (
                      <img src={resolveApiUrl(disco.imagenUrl)} alt="" className="w-12 h-12 rounded-lg object-cover bg-slate-100 dark:bg-stone-800 flex-shrink-0" />
                    ) : (
                      <div className="w-12 h-12 rounded-lg bg-slate-100 dark:bg-stone-800 flex items-center justify-center text-[10px] text-slate-400 dark:text-stone-500 flex-shrink-0">
                        Sin portada
                      </div>
                    )}
                    <div className="flex-1 min-w-0">
                      <div className="font-medium text-slate-900 dark:text-white text-sm truncate">
                        {disco.artista || '—'} — {disco.album || '—'}
                      </div>
                      <div className="text-xs text-slate-400 dark:text-stone-500 truncate mt-1">
                        {[
                          disco.codigoInterno,
                          disco.estado ? ESTADO_LABELS[disco.estado] || disco.estado : null,
                          disco.selloDiscografico,
                          disco.precioVenta != null ? money(disco.precioVenta) : 'Sin precio',
                        ].filter(Boolean).join(' · ')}
                      </div>
                    </div>
                    <button type="button" onClick={() => setSelectedDiscoDetalle(disco)} className="text-xs text-slate-500 dark:text-stone-400 hover:text-slate-900 dark:hover:text-white font-medium flex-shrink-0">
                      Ver
                    </button>
                    <button type="button" onClick={() => handleAddCatalogDisco(disco)} className="text-xs text-[#5C7D87] dark:text-[#7E9FA8] font-medium flex-shrink-0">
                      + Agregar
                    </button>
                  </div>
                ))}
                {filteredDiscos.length === 0 && (
                  <p className="text-slate-400 dark:text-stone-600 text-sm text-center py-3">
                    No hay discos que coincidan con esa búsqueda.
                  </p>
                )}
              </div>
            </div>
                  </section>

                  <section className="card p-5 space-y-4">
            <div>
              <h2 className="text-sm font-semibold text-slate-700 dark:text-stone-300 uppercase tracking-wider">Cliente</h2>
              <p className="text-sm text-slate-400 dark:text-stone-500 mt-1">
                Buscá por nombre, cédula, Instagram, teléfono o dirección y evitá duplicar registros.
              </p>
            </div>

            {selectedCliente ? (
              <div className="flex items-center justify-between gap-4 rounded-xl border border-slate-200 dark:border-stone-700 bg-slate-50 dark:bg-stone-950 px-4 py-3">
                <div className="min-w-0">
                  <div className="font-semibold text-slate-900 dark:text-white text-sm">
                    {[selectedCliente.nombre, selectedCliente.apellido].filter(Boolean).join(' ')}
                  </div>
                  <div className="text-xs text-slate-400 dark:text-stone-500 mt-1 truncate">
                    {[selectedCliente.cedula && `CI ${selectedCliente.cedula}`, selectedCliente.instagramUsuario, selectedCliente.telefono, selectedCliente.direccion].filter(Boolean).join(' · ') || 'Cliente seleccionado'}
                  </div>
                </div>
                <button type="button" onClick={() => { setSelectedCliente(null); setTimeout(() => clientSearchRef.current?.focus(), 0) }} className="text-xs text-slate-400 hover:text-red-500 transition-colors flex-shrink-0">
                  Cambiar
                </button>
              </div>
            ) : (
              <div className="space-y-2">
                <div className="relative">
                  <input
                    ref={clientSearchRef}
                    value={clienteQuery}
                    onChange={e => { setClienteQuery(e.target.value); setShowClienteResults(true) }}
                    onFocus={() => setShowClienteResults(true)}
                    placeholder="Buscar por nombre, cédula, Instagram, teléfono o dirección..."
                    className="input"
                    autoComplete="off"
                  />
                  {showClienteResults && filteredClientes.length > 0 && (
                    <div className="absolute z-10 w-full mt-1 bg-white dark:bg-stone-900 border border-slate-200 dark:border-stone-700 rounded-xl shadow-lg overflow-hidden">
                      {filteredClientes.map(cliente => (
                        <button
                          key={cliente.idCliente}
                          type="button"
                          onClick={() => selectCliente(cliente)}
                          className="w-full text-left px-4 py-2.5 hover:bg-slate-50 dark:hover:bg-stone-800 transition-colors"
                        >
                          <div className="font-medium text-slate-800 dark:text-stone-200 text-sm">
                            {[cliente.nombre, cliente.apellido].filter(Boolean).join(' ')}
                          </div>
                          <div className="text-xs text-slate-400 dark:text-stone-500">
                            {[cliente.cedula && `CI ${cliente.cedula}`, cliente.instagramUsuario, cliente.telefono, cliente.direccion].filter(Boolean).join(' · ') || 'Sin datos adicionales'}
                          </div>
                        </button>
                      ))}
                    </div>
                  )}
                </div>
                <button type="button" onClick={() => setShowClienteModal(true)} className="text-xs text-[#5C7D87] dark:text-[#7E9FA8] hover:underline">
                  + Nuevo cliente
                </button>
              </div>
            )}
                  </section>
                </div>

                <aside className="space-y-5">
                  <section className="card p-5 space-y-4">
            <div>
              <h2 className="text-sm font-semibold text-slate-700 dark:text-stone-300 uppercase tracking-wider">Datos de la pre-venta</h2>
              <p className="text-sm text-slate-400 dark:text-stone-500 mt-1">
                El estado inicial se mantiene en pendiente y el total se calcula con el disco seleccionado.
              </p>
            </div>

            <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-1">
              <Field label="Fecha">
                <input
                  required
                  className="input"
                  type="date"
                  value={metaForm.fecha}
                  onChange={e => setMetaForm(prev => ({ ...prev, fecha: e.target.value }))}
                />
              </Field>
              <Field label="Estado inicial">
                <div className="input flex items-center text-sm text-amber-700 dark:text-amber-300">
                  Pendiente
                </div>
              </Field>
            </div>

            <Field label="Total">
              <div className="input flex items-center font-semibold text-slate-900 dark:text-white">
                {money(totalSeleccionado)}
              </div>
            </Field>

            <Field label="Notas generales">
              <textarea
                className="input min-h-24 resize-y"
                rows={4}
                value={metaForm.notas}
                onChange={e => setMetaForm(prev => ({ ...prev, notas: e.target.value }))}
                placeholder="Notas internas sobre la reserva..."
              />
            </Field>

            <button className="btn-primary w-full" disabled={saving}>
              {saving ? 'Guardando…' : 'Registrar pre-venta'}
            </button>
                  </section>
                </aside>
              </form>
            </div>
          </div>
        </div>
      </section>

      <section ref={listSectionRef} className="card overflow-hidden min-w-0">
        <div className="px-5 py-4 border-b border-slate-100 dark:border-stone-800 flex items-center justify-between gap-4">
          <div>
            <h2 className="font-semibold text-slate-900 dark:text-white">Listado</h2>
            <p className="text-sm text-slate-400 dark:text-stone-500 mt-1">
              Seguimiento de reservas, cobros y eliminación segura de pendientes.
            </p>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-100 dark:border-stone-800">
                <th className="px-3 py-2.5 text-left text-xs font-semibold uppercase tracking-wider text-slate-500 dark:text-stone-500 whitespace-nowrap">
                  <button type="button" onClick={() => setSortOrder(prev => prev === 'desc' ? 'asc' : 'desc')} className="inline-flex items-center gap-1 hover:text-slate-700 dark:hover:text-stone-300">
                    Fecha
                    <SortChevron direction={sortOrder} />
                  </button>
                </th>
                {['Cliente', 'Disco / descripción', 'Código', 'Cantidad', 'Precio', 'Estado', 'Acciones'].map(label => (
                  <th key={label} className="px-3 py-2.5 text-left text-xs font-semibold uppercase tracking-wider text-slate-500 dark:text-stone-500 whitespace-nowrap">
                    {label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-stone-800">
              {sortedPreVentas.map(item => {
                const pagada = item.estado === 'PAGADA'
                const busy = busyId === item.idPreVenta
                const linkedDisco = item.idDisco ? discosById.get(String(item.idDisco)) : null
                const titulo = item.idDisco ? `${item.artista || '—'} — ${item.album || '—'}` : item.descripcion

                return (
                  <tr key={item.idPreVenta} className="align-middle">
                    <td className="px-3 py-3 text-slate-600 dark:text-stone-400 whitespace-nowrap">{fmtDate(item.fecha)}</td>
                    <td className="px-3 py-3 text-slate-900 dark:text-white min-w-40">{item.clienteNombre}</td>
                    <td className="px-3 py-3 min-w-64 max-w-80">
                      <div className="flex items-center gap-3 min-w-0">
                        {linkedDisco?.imagenUrl && (
                          <img src={resolveApiUrl(linkedDisco.imagenUrl)} alt="" className="w-10 h-10 rounded-lg object-cover bg-slate-100 dark:bg-stone-800 flex-shrink-0" />
                        )}
                        <div className="min-w-0">
                          <div className="text-slate-900 dark:text-white truncate" title={titulo}>{titulo || '—'}</div>
                          {linkedDisco?.estado && (
                            <div className="text-xs text-slate-400 dark:text-stone-500 mt-1 truncate">
                              {ESTADO_LABELS[linkedDisco.estado] || linkedDisco.estado}
                            </div>
                          )}
                        </div>
                      </div>
                    </td>
                    <td className="px-3 py-3 text-slate-600 dark:text-stone-400 whitespace-nowrap max-w-36 truncate" title={item.codigoDisco}>{item.codigoDisco || '—'}</td>
                    <td className="px-3 py-3 tabular-nums text-slate-600 dark:text-stone-400">{item.cantidad}</td>
                    <td className="px-3 py-3 tabular-nums text-slate-900 dark:text-white whitespace-nowrap">{money(item.precio)}</td>
                    <td className="px-3 py-3">
                      <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${pagada ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-300' : 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300'}`}>
                        {pagada ? 'Pagada' : 'Pendiente'}
                      </span>
                    </td>
                    <td className="px-3 py-3">
                      <div className="flex flex-wrap gap-1.5 min-w-36">
                        {!pagada && (
                          <button
                            type="button"
                            disabled={busy}
                            onClick={() => marcarPagada(item)}
                            className="rounded-lg bg-emerald-600 px-2.5 py-1.5 text-xs font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
                          >
                            {busy ? 'Procesando…' : 'Marcar pago'}
                          </button>
                        )}
                        <button
                          type="button"
                          disabled={busy || pagada}
                          onClick={() => setDeleting(item)}
                          title={pagada ? 'Las pre-ventas pagadas no se pueden eliminar' : 'Eliminar pre-venta'}
                          aria-label="Eliminar pre-venta"
                          className="rounded-lg border border-red-200 p-1.5 text-red-600 hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-35 dark:border-red-900 dark:hover:bg-red-950/30"
                        >
                          <TrashIcon />
                        </button>
                      </div>
                    </td>
                  </tr>
                )
              })}
              {sortedPreVentas.length === 0 && (
                <tr>
                  <td colSpan={8} className="px-4 py-10 text-center text-slate-400 dark:text-stone-500">
                    No hay pre-ventas registradas.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      {selectedDiscoDetalle && (
        <DiscoDetalleModal
          disco={selectedDiscoDetalle}
          onClose={() => setSelectedDiscoDetalle(null)}
          onAgregar={handleAddCatalogDisco}
        />
      )}

      {showClienteModal && (
        <NuevoClienteModal
          onCerrar={() => setShowClienteModal(false)}
          onCreado={(cliente) => {
            setClientes(prev => [cliente, ...prev])
            selectCliente(cliente)
            setShowClienteModal(false)
          }}
        />
      )}

      {deleting && (
        <ConfirmModal
          titulo="Eliminar pre-venta"
          mensaje="¿Seguro que querés eliminar esta pre-venta pendiente? No se registrará ningún ingreso ni se modificará el stock."
          onConfirmar={eliminar}
          onCancelar={() => setDeleting(null)}
          cargando={busyId === deleting.idPreVenta}
        />
      )}
    </div>
  )
}

function Field({ label, children, className = '' }) {
  return (
    <div className={className}>
      <label className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">{label}</label>
      {children}
    </div>
  )
}

function DiscoDetalleModal({ disco, onClose, onAgregar }) {
  return (
    <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 px-4 py-6 overflow-y-auto" onClick={onClose}>
      <div className="bg-white dark:bg-stone-900 border border-slate-200 dark:border-stone-700 rounded-2xl shadow-2xl w-full max-w-lg p-6 space-y-4" onClick={e => e.stopPropagation()}>
        <div className="flex items-start justify-between gap-4">
          <div>
            <h3 className="text-slate-900 dark:text-white font-bold text-base">{disco.artista || '—'}</h3>
            <p className="text-slate-500 dark:text-stone-400 text-sm">{disco.album || '—'}</p>
          </div>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-700 dark:hover:text-white">✕</button>
        </div>
        <div className="flex flex-col gap-4 sm:flex-row">
          {disco.imagenUrl ? (
            <img src={resolveApiUrl(disco.imagenUrl)} alt="" className="w-32 h-32 rounded-xl object-cover bg-slate-100 dark:bg-stone-800 flex-shrink-0" />
          ) : (
            <div className="w-32 h-32 rounded-xl bg-slate-100 dark:bg-stone-800 flex items-center justify-center text-sm text-slate-400 dark:text-stone-500 flex-shrink-0">
              Sin portada
            </div>
          )}
          <div className="grid grid-cols-2 gap-3 flex-1">
            {[
              ['Código', disco.codigoInterno],
              ['Estado', ESTADO_LABELS[disco.estado] || disco.estado],
              ['Sello', disco.selloDiscografico],
              ['Precio', disco.precioVenta != null ? money(disco.precioVenta) : '—'],
            ].map(([label, value]) => (
              <div key={label} className="rounded-lg border border-slate-100 dark:border-stone-800 bg-slate-50 dark:bg-stone-950 px-3 py-2">
                <p className="text-[10px] uppercase tracking-wider text-slate-400 dark:text-stone-500">{label}</p>
                <p className="text-sm text-slate-700 dark:text-stone-300 truncate">{value || '—'}</p>
              </div>
            ))}
          </div>
        </div>
        <div className="flex justify-end gap-3">
          <button type="button" onClick={onClose} className="btn-secondary">Cerrar</button>
          <button type="button" onClick={() => onAgregar(disco)} className="btn-primary">+ Agregar</button>
        </div>
      </div>
    </div>
  )
}

function NuevoClienteModal({ onCerrar, onCreado }) {
  const [form, setForm] = useState({
    nombre: '', apellido: '', cedula: '', telefono: '',
    instagramUsuario: '', email: '', direccion: '', observaciones: '',
  })
  const [error, setError] = useState('')
  const [cargando, setCargando] = useState(false)

  function set(field, value) {
    setForm(prev => ({ ...prev, [field]: value }))
  }

  async function handleSubmit(e) {
    e.preventDefault()
    if (!form.nombre.trim()) {
      setError('El nombre es obligatorio')
      return
    }
    setError('')
    setCargando(true)
    try {
      onCreado(await api.clientes.crear(form))
    } catch (err) {
      setError(err.message)
    } finally {
      setCargando(false)
    }
  }

  return (
    <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 px-4 py-6 overflow-y-auto" onClick={onCerrar}>
      <div className="bg-white dark:bg-stone-900 border border-slate-200 dark:border-stone-700 rounded-2xl shadow-2xl w-full max-w-2xl p-6" onClick={e => e.stopPropagation()}>
        <div className="flex items-start justify-between gap-4 mb-4">
          <div>
            <h3 className="text-slate-900 dark:text-white font-bold text-base">Nuevo cliente</h3>
            <p className="text-slate-500 dark:text-stone-400 text-sm">Completá los datos necesarios para usarlo en la pre-venta.</p>
          </div>
          <button onClick={onCerrar} className="text-slate-400 hover:text-slate-700 dark:hover:text-white">✕</button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Nombre"><input className="input" value={form.nombre} onChange={e => set('nombre', e.target.value)} /></Field>
            <Field label="Apellido"><input className="input" value={form.apellido} onChange={e => set('apellido', e.target.value)} /></Field>
            <Field label="Cédula"><input className="input" value={form.cedula} onChange={e => set('cedula', e.target.value)} /></Field>
            <Field label="Teléfono"><input className="input" value={form.telefono} onChange={e => set('telefono', e.target.value)} /></Field>
            <Field label="Instagram"><input className="input" value={form.instagramUsuario} onChange={e => set('instagramUsuario', e.target.value)} /></Field>
            <Field label="Email"><input className="input" value={form.email} onChange={e => set('email', e.target.value)} /></Field>
            <Field label="Dirección" className="sm:col-span-2"><input className="input" value={form.direccion} onChange={e => set('direccion', e.target.value)} /></Field>
            <Field label="Observaciones" className="sm:col-span-2">
              <textarea className="input min-h-24 resize-y" rows={4} value={form.observaciones} onChange={e => set('observaciones', e.target.value)} />
            </Field>
          </div>
          {error && <p className="text-sm text-red-500">{error}</p>}
          <div className="flex gap-3">
            <button type="button" onClick={onCerrar} disabled={cargando} className="btn-secondary flex-1">Cancelar</button>
            <button type="submit" disabled={cargando} className="btn-primary flex-1">{cargando ? 'Guardando...' : 'Crear cliente'}</button>
          </div>
        </form>
      </div>
    </div>
  )
}

function SearchIcon({ className }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="m21 21-5.197-5.197m0 0A7.5 7.5 0 1 0 5.196 5.196a7.5 7.5 0 0 0 10.607 10.607Z" />
    </svg>
  )
}

function ChevronDown({ className }) {
  return (
    <svg className={className} viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
      <path fillRule="evenodd" d="M5.23 7.21a.75.75 0 0 1 1.06.02L10 11.168l3.71-3.938a.75.75 0 1 1 1.08 1.04l-4.25 4.508a.75.75 0 0 1-1.08 0L5.21 8.27a.75.75 0 0 1 .02-1.06Z" clipRule="evenodd" />
    </svg>
  )
}

function SortChevron({ direction }) {
  return (
    <svg className={`h-3.5 w-3.5 transition-transform ${direction === 'asc' ? 'rotate-180' : ''}`} viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
      <path fillRule="evenodd" d="M5.23 7.21a.75.75 0 0 1 1.06.02L10 11.168l3.71-3.938a.75.75 0 1 1 1.08 1.04l-4.25 4.508a.75.75 0 0 1-1.08 0L5.21 8.27a.75.75 0 0 1 .02-1.06Z" clipRule="evenodd" />
    </svg>
  )
}

function TrashIcon() {
  return (
    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="1.8">
      <path strokeLinecap="round" strokeLinejoin="round" d="m14.74 9-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166M18.16 5.79 17.41 19.5a2.25 2.25 0 0 1-2.244 2.126H8.834A2.25 2.25 0 0 1 6.59 19.5L5.84 5.79m12.32 0a48.108 48.108 0 0 0-3.478-.397m-8.842.397a48.11 48.11 0 0 1 3.478-.397m5.364 0V4.477c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 0 0-1.184 0c-1.18.037-2.09 1.022-2.09 2.201v.916m5.364 0a48.667 48.667 0 0 0-5.364 0" />
    </svg>
  )
}

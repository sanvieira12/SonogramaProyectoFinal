import { useEffect, useMemo, useRef, useState } from 'react'
import { api, FINANCIAL_DATA_CHANGED_EVENT, resolveApiUrl } from '../api/sonograma'
import ConfirmModal from '../components/ConfirmModal'
import { businessDateInMontevideo } from '../utils/businessDate'
import { mergePreVentaItem, transactionTotal } from './preVentaTransactionUtils'

const emptyManual = () => ({ descripcion: '', codigoDisco: '', cantidad: '1', precioUnitario: '', notas: '' })
const emptyMeta = () => ({ fecha: businessDateInMontevideo(), notas: '' })

function newKey() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `preventa-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function money(value) {
  return `UYU $${Number(value || 0).toLocaleString('es-UY', { maximumFractionDigits: 2 })}`
}

function fmtDate(value) {
  if (!value) return '—'
  const [year, month, day] = String(value).slice(0, 10).split('-')
  return year && month && day ? `${day}/${month}/${year}` : value
}

function normalize(value) {
  return String(value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase()
}

function quantity(item) {
  const value = Number(item?.cantidad)
  return Number.isInteger(value) && value > 0 ? value : 0
}

function unitPrice(item) {
  const value = Number(item?.precioUnitario)
  return Number.isFinite(value) && value > 0 ? value : 0
}

function lineTotal(item) {
  return Number((quantity(item) * unitPrice(item)).toFixed(2))
}

function catalogItem(disco) {
  return {
    uid: newKey(), mode: 'catalogo', idDisco: disco.idDisco,
    titulo: [disco.artista, disco.album].filter(Boolean).join(' — ') || 'Disco de catálogo',
    descripcion: null, codigoDisco: disco.codigoInterno || '', cantidad: '1',
    precioUnitario: disco.precioVenta != null ? String(Number(disco.precioVenta)) : '',
    notas: '', imagenUrl: disco.imagenUrl || '',
  }
}

function manualItem(form) {
  return {
    uid: newKey(), mode: 'manual', idDisco: null, titulo: form.descripcion.trim(),
    descripcion: form.descripcion.trim(), codigoDisco: form.codigoDisco.trim(),
    cantidad: String(form.cantidad), precioUnitario: String(form.precioUnitario),
    notas: form.notas.trim(), imagenUrl: '',
  }
}

function itemTitle(item) {
  return item.descripcion || [item.artista, item.album].filter(Boolean).join(' — ') || item.titulo || 'Ítem manual'
}

function paymentLabel(status) {
  if (status === 'PAGADA') return 'Pagada'
  if (status === 'PARCIALMENTE_PAGADA') return 'Parcialmente pagada'
  return 'Pendiente'
}

function paymentClasses(status) {
  if (status === 'PAGADA') return 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-300'
  if (status === 'PARCIALMENTE_PAGADA') return 'bg-slate-100 text-slate-700 dark:bg-stone-800 dark:text-stone-200'
  return 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300'
}

export default function PreVentas() {
  const [transactions, setTransactions] = useState([])
  const [clientes, setClientes] = useState([])
  const [discos, setDiscos] = useState([])
  const [showCreate, setShowCreate] = useState(false)
  const [selectedCliente, setSelectedCliente] = useState(null)
  const [clienteQuery, setClienteQuery] = useState('')
  const [discoQuery, setDiscoQuery] = useState('')
  const [items, setItems] = useState([])
  const [manual, setManual] = useState(emptyManual)
  const [showManual, setShowManual] = useState(false)
  const [meta, setMeta] = useState(emptyMeta)
  const [idempotencyKey, setIdempotencyKey] = useState(newKey)
  const [selectedId, setSelectedId] = useState(null)
  const [deleting, setDeleting] = useState(null)
  const [payingAll, setPayingAll] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [showClienteModal, setShowClienteModal] = useState(false)

  const loadTransactions = () => api.preVentaTransacciones.listar().then(setTransactions)

  useEffect(() => {
    loadTransactions().catch(err => setError(err.message))
    api.clientes.todos().then(setClientes).catch(() => setClientes([]))
    api.discos.todos().then(setDiscos).catch(() => setDiscos([]))
  }, [])

  const hasDraft = Boolean(selectedCliente || items.length || meta.notas.trim())
  useEffect(() => {
    const warn = event => {
      if (!hasDraft) return
      event.preventDefault()
      event.returnValue = ''
    }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [hasDraft])

  const filteredClients = useMemo(() => {
    const q = normalize(clienteQuery)
    return clientes.filter(cliente => normalize([
      cliente.nombre, cliente.apellido, cliente.cedula, cliente.instagramUsuario, cliente.telefono,
    ].filter(Boolean).join(' ')).includes(q)).slice(0, 8)
  }, [clientes, clienteQuery])

  const filteredDiscos = useMemo(() => {
    const q = normalize(discoQuery)
    return discos.filter(disco => normalize([
      disco.artista, disco.album, disco.codigoInterno, disco.selloDiscografico,
    ].filter(Boolean).join(' ')).includes(q)).slice(0, 12)
  }, [discos, discoQuery])

  const selectedTransaction = transactions.find(tx => String(tx.idPreVentaTransaccion) === String(selectedId)) || null
  const total = transactionTotal(items)

  function addCatalog(disco) {
    const incoming = catalogItem(disco)
    if (!unitPrice(incoming)) {
      setError('Ingresá un precio válido después de agregar el disco.')
    } else {
      setError('')
    }
    setItems(current => mergePreVentaItem(current, incoming))
  }

  function addManual() {
    const incoming = manualItem(manual)
    if (!incoming.descripcion || !quantity(incoming) || !unitPrice(incoming)) {
      setError('Completá descripción, cantidad y precio para el disco fuera de catálogo.')
      return
    }
    setItems(current => [...current, incoming])
    setManual(emptyManual())
    setShowManual(false)
    setError('')
  }

  function updateDraft(uid, field, value) {
    setItems(current => current.map(item => item.uid === uid ? { ...item, [field]: value } : item))
  }

  function resetDraft() {
    setSelectedCliente(null)
    setClienteQuery('')
    setDiscoQuery('')
    setItems([])
    setManual(emptyManual())
    setShowManual(false)
    setMeta(emptyMeta())
    setIdempotencyKey(newKey())
  }

  async function submit(event) {
    event.preventDefault()
    setError('')
    if (!selectedCliente) return setError('Seleccioná un cliente.')
    if (!items.length) return setError('Agregá al menos un disco.')
    if (items.some(item => !quantity(item) || !unitPrice(item))) {
      return setError('Todos los ítems deben tener cantidad y precio positivos.')
    }
    setBusy(true)
    try {
      const created = await api.preVentaTransacciones.crear({
        idCliente: Number(selectedCliente.idCliente), fecha: meta.fecha,
        notas: meta.notas.trim() || null, idempotencyKey,
        items: items.map(item => ({
          idDisco: item.idDisco ? Number(item.idDisco) : null,
          descripcion: item.idDisco ? null : item.descripcion,
          codigoDisco: item.codigoDisco || null,
          cantidad: quantity(item), precio: lineTotal(item), notas: item.notas || null,
        })),
      })
      setTransactions(current => [created, ...current.filter(tx => tx.idPreVentaTransaccion !== created.idPreVentaTransaccion)])
      resetDraft()
      setShowCreate(false)
    } catch (err) {
      setError(err.message || 'No se pudo registrar la pre-venta')
    } finally {
      setBusy(false)
    }
  }

  function toggleCreate() {
    if (showCreate && hasDraft && !window.confirm('Hay cambios sin guardar. ¿Cerrar la nueva pre-venta?')) return
    setShowCreate(value => !value)
  }

  function replaceTransaction(updated) {
    setTransactions(current => current.map(tx =>
      tx.idPreVentaTransaccion === updated.idPreVentaTransaccion ? updated : tx))
  }

  async function markPaid(item) {
    setBusy(true)
    setError('')
    try {
      await api.preVentas.marcarPagada(item.idPreVenta)
      const updated = await api.preVentaTransacciones.obtener(selectedTransaction.idPreVentaTransaccion)
      replaceTransaction(updated)
      window.dispatchEvent(new Event(FINANCIAL_DATA_CHANGED_EVENT))
    } catch (err) {
      setError(err.message || 'No se pudo registrar el pago')
    } finally {
      setBusy(false)
    }
  }

  async function payRemaining() {
    if (!payingAll) return
    setBusy(true)
    setError('')
    try {
      const updated = await api.preVentaTransacciones.pagarPendientes(payingAll.idPreVentaTransaccion)
      replaceTransaction(updated)
      setPayingAll(null)
      window.dispatchEvent(new Event(FINANCIAL_DATA_CHANGED_EVENT))
    } catch (err) {
      setError(err.message || 'No se pudo cobrar el saldo pendiente')
    } finally {
      setBusy(false)
    }
  }

  async function deleteItem() {
    if (!deleting) return
    setBusy(true)
    setError('')
    try {
      const updated = await api.preVentaTransacciones.eliminarItem(
        deleting.transactionId, deleting.item.idPreVenta)
      replaceTransaction(updated)
      setDeleting(null)
    } catch (err) {
      setError(err.message || 'No se pudo eliminar el ítem')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 py-6 space-y-5">
      <header>
        <h1 className="text-xl font-bold text-slate-900 dark:text-white">Pre-ventas</h1>
        <p className="text-sm text-slate-400 dark:text-stone-500 mt-1">Transacciones con uno o varios discos, sin modificar el stock físico.</p>
      </header>

      {error && <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-900 dark:bg-red-950/40 dark:text-red-300">{error}</div>}

      <section className="card overflow-hidden">
        <button type="button" onClick={toggleCreate} aria-expanded={showCreate} className="w-full p-5 flex items-center justify-between text-left hover:bg-slate-50 dark:hover:bg-stone-900/60">
          <div><h2 className="font-semibold text-slate-900 dark:text-white">Nueva pre-venta</h2><p className="text-sm text-slate-400 mt-1">Seleccioná un cliente y agregá todos los discos de esta reserva.</p></div>
          <span className="text-xl text-slate-400">{showCreate ? '−' : '+'}</span>
        </button>

        {showCreate && (
          <form onSubmit={submit} className="border-t border-slate-100 dark:border-stone-800 p-5 grid gap-5 xl:grid-cols-[minmax(0,1fr)_360px]">
            <div className="space-y-5 min-w-0">
              <section className="space-y-3">
                <div className="flex items-center justify-between gap-3"><h3 className="text-xs font-semibold uppercase tracking-wider text-slate-500">Cliente</h3><button type="button" className="text-xs text-[#5C7D87]" onClick={() => setShowClienteModal(true)}>+ Nuevo cliente</button></div>
                {selectedCliente ? (
                  <div className="rounded-xl border border-[#5C7D87]/30 bg-[#5C7D87]/5 p-3 flex items-center justify-between">
                    <span className="font-medium text-slate-900 dark:text-white">{[selectedCliente.nombre, selectedCliente.apellido].filter(Boolean).join(' ')}</span>
                    <button type="button" className="text-xs text-slate-500 hover:text-red-500" onClick={() => setSelectedCliente(null)}>Cambiar</button>
                  </div>
                ) : <>
                  <input className="input" value={clienteQuery} onChange={e => setClienteQuery(e.target.value)} placeholder="Buscar cliente por nombre, cédula o teléfono…" />
                  <div className="grid gap-2 sm:grid-cols-2">
                    {filteredClients.map(cliente => <button type="button" key={cliente.idCliente} onClick={() => setSelectedCliente(cliente)} className="rounded-xl border border-slate-200 dark:border-stone-700 p-3 text-left hover:border-[#5C7D87]">
                      <span className="block text-sm font-medium text-slate-900 dark:text-white">{[cliente.nombre, cliente.apellido].filter(Boolean).join(' ')}</span>
                      <span className="text-xs text-slate-400">{cliente.cedula || cliente.telefono || 'Sin documento'}</span>
                    </button>)}
                  </div>
                </>}
              </section>

              <section className="space-y-3">
                <div className="flex items-center justify-between"><h3 className="text-xs font-semibold uppercase tracking-wider text-slate-500">Discos de la transacción</h3><span className="text-xs text-slate-400">{items.length} {items.length === 1 ? 'ítem' : 'ítems'}</span></div>
                {items.map(item => <DraftItem key={item.uid} item={item} onChange={updateDraft} onRemove={() => setItems(current => current.filter(candidate => candidate.uid !== item.uid))} />)}
                {!items.length && <div className="rounded-xl border border-dashed border-slate-200 dark:border-stone-700 p-5 text-sm text-slate-400">Todavía no agregaste discos.</div>}

                <input className="input" value={discoQuery} onChange={e => setDiscoQuery(e.target.value)} placeholder="Buscar disco por artista, álbum o código…" />
                <div className="grid gap-2 sm:grid-cols-2 max-h-72 overflow-y-auto">
                  {filteredDiscos.map(disco => <button type="button" key={disco.idDisco} onClick={() => addCatalog(disco)} className="rounded-xl border border-slate-200 dark:border-stone-700 p-3 flex items-center gap-3 text-left hover:border-[#5C7D87]">
                    {disco.imagenUrl ? <img src={resolveApiUrl(disco.imagenUrl)} alt="" className="w-10 h-10 rounded object-cover" /> : <span className="w-10 h-10 rounded bg-slate-100 dark:bg-stone-800 flex items-center justify-center text-[9px] text-slate-400">Sin tapa</span>}
                    <span className="min-w-0"><span className="block text-sm font-medium truncate text-slate-900 dark:text-white">{[disco.artista, disco.album].filter(Boolean).join(' — ')}</span><span className="block text-xs text-slate-400">{money(disco.precioVenta)}</span></span>
                  </button>)}
                </div>

                <button type="button" className="btn-secondary text-sm" onClick={() => setShowManual(value => !value)}>Agregar disco fuera de catálogo</button>
                {showManual && <ManualItemForm form={manual} setForm={setManual} onAdd={addManual} onCancel={() => setShowManual(false)} />}
              </section>
            </div>

            <aside className="card p-5 h-fit space-y-4">
              <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-500">Resumen</h3>
              <Field label="Fecha"><input required type="date" className="input" value={meta.fecha} onChange={e => setMeta(current => ({ ...current, fecha: e.target.value }))} /></Field>
              <Field label="Notas generales"><textarea className="input min-h-24 resize-y" value={meta.notas} onChange={e => setMeta(current => ({ ...current, notas: e.target.value }))} /></Field>
              <div className="rounded-xl bg-slate-50 dark:bg-stone-900 p-4 flex justify-between"><span className="text-sm text-slate-500">Total</span><strong className="text-slate-900 dark:text-white">{money(total)}</strong></div>
              <button className="btn-primary w-full" disabled={busy}>{busy ? 'Guardando…' : 'Guardar pre-venta'}</button>
            </aside>
          </form>
        )}
      </section>

      <TransactionList transactions={transactions} onOpen={tx => setSelectedId(tx.idPreVentaTransaccion)} />

      {selectedTransaction && <TransactionPanel
        transaction={selectedTransaction} discos={discos} busy={busy}
        onClose={() => setSelectedId(null)} onUpdated={replaceTransaction}
        onMarkPaid={markPaid} onDelete={item => setDeleting({ transactionId: selectedTransaction.idPreVentaTransaccion, item })}
        onPayAll={() => setPayingAll(selectedTransaction)} onError={setError}
      />}

      {showClienteModal && <NuevoClienteModal onCerrar={() => setShowClienteModal(false)} onCreado={cliente => {
        setClientes(current => [cliente, ...current]); setSelectedCliente(cliente); setShowClienteModal(false)
      }} />}

      {deleting && <ConfirmModal titulo="Eliminar ítem pendiente" mensaje={`¿Eliminar “${itemTitle(deleting.item)}” de esta pre-venta?`} onConfirmar={deleteItem} onCancelar={() => setDeleting(null)} cargando={busy} />}
      {payingAll && <ConfirmModal titulo="Pagar saldo pendiente" mensaje={`Se registrarán cobros individuales por ${money(payingAll.pendiente)}. Los ítems ya pagados no se cobrarán nuevamente.`} onConfirmar={payRemaining} onCancelar={() => setPayingAll(null)} cargando={busy} />}
    </div>
  )
}

function DraftItem({ item, onChange, onRemove }) {
  return <article className="rounded-xl border border-slate-200 dark:border-stone-700 p-4 space-y-3">
    <div className="flex items-start justify-between gap-3"><div><h4 className="font-medium text-slate-900 dark:text-white">{item.titulo}</h4>{item.codigoDisco && <p className="text-xs text-slate-400 mt-1">Código: {item.codigoDisco}</p>}</div><button type="button" className="text-xs text-red-500" onClick={onRemove}>Quitar</button></div>
    <div className="grid gap-3 sm:grid-cols-3">
      <Field label="Cantidad"><input className="input" type="number" min="1" step="1" value={item.cantidad} onChange={e => onChange(item.uid, 'cantidad', e.target.value)} /></Field>
      <Field label="Precio por unidad"><input className="input" type="number" min="0.01" step="0.01" value={item.precioUnitario} onChange={e => onChange(item.uid, 'precioUnitario', e.target.value)} /></Field>
      <Field label="Subtotal"><div className="input flex items-center font-semibold">{money(lineTotal(item))}</div></Field>
    </div>
  </article>
}

function ManualItemForm({ form, setForm, onAdd, onCancel, disabled = false }) {
  const set = (field, value) => setForm(current => ({ ...current, [field]: value }))
  return <div className="rounded-xl border border-slate-200 dark:border-stone-700 bg-slate-50 dark:bg-stone-950 p-4 grid gap-3 sm:grid-cols-2">
    <Field label="Descripción"><input className="input" value={form.descripcion} onChange={e => set('descripcion', e.target.value)} /></Field>
    <Field label="Código"><input className="input" value={form.codigoDisco} onChange={e => set('codigoDisco', e.target.value)} /></Field>
    <Field label="Cantidad"><input className="input" type="number" min="1" step="1" value={form.cantidad} onChange={e => set('cantidad', e.target.value)} /></Field>
    <Field label="Precio por unidad"><input className="input" type="number" min="0.01" step="0.01" value={form.precioUnitario} onChange={e => set('precioUnitario', e.target.value)} /></Field>
    <Field label="Notas" className="sm:col-span-2"><textarea className="input" value={form.notas} onChange={e => set('notas', e.target.value)} /></Field>
    <div className="sm:col-span-2 flex justify-end gap-2"><button type="button" className="btn-secondary text-sm" disabled={disabled} onClick={onCancel}>Cancelar</button><button type="button" className="btn-primary text-sm" disabled={disabled} onClick={onAdd}>{disabled ? 'Agregando…' : 'Agregar ítem'}</button></div>
  </div>
}

function TransactionList({ transactions, onOpen }) {
  return <section className="card overflow-hidden">
    <div className="p-5 border-b border-slate-100 dark:border-stone-800"><h2 className="font-semibold text-slate-900 dark:text-white">Transacciones</h2><p className="text-sm text-slate-400 mt-1">Cada fila representa una reserva independiente.</p></div>
    <div className="hidden md:block overflow-x-auto"><table className="w-full text-sm" data-testid="preventa-transaction-table"><thead><tr className="border-b border-slate-100 dark:border-stone-800">{['Pre-venta', 'Fecha', 'Cliente', 'Discos', 'Total', 'Pagado', 'Pendiente', 'Estado', ''].map(label => <th key={label} className="px-3 py-2.5 text-left text-xs uppercase tracking-wider text-slate-500">{label}</th>)}</tr></thead><tbody className="divide-y divide-slate-100 dark:divide-stone-800">
      {transactions.map(tx => <tr key={tx.idPreVentaTransaccion}><td className="px-3 py-3 font-semibold">#{tx.idPreVentaTransaccion}</td><td className="px-3 py-3 whitespace-nowrap">{fmtDate(tx.fecha)}</td><td className="px-3 py-3">{tx.clienteNombre}</td><td className="px-3 py-3">{tx.cantidadItems} · {tx.cantidadUnidades} u.</td><td className="px-3 py-3 whitespace-nowrap">{money(tx.total)}</td><td className="px-3 py-3 whitespace-nowrap">{money(tx.pagado)}</td><td className="px-3 py-3 whitespace-nowrap">{money(tx.pendiente)}</td><td className="px-3 py-3"><PaymentBadge status={tx.estadoPago} /></td><td className="px-3 py-3"><button type="button" className="btn-secondary text-xs" onClick={() => onOpen(tx)}>Ver detalle</button></td></tr>)}
      {!transactions.length && <tr><td colSpan={9} className="p-10 text-center text-slate-400">No hay pre-ventas registradas.</td></tr>}
    </tbody></table></div>
    <div className="md:hidden divide-y divide-slate-100 dark:divide-stone-800" data-testid="preventa-transaction-cards">{transactions.map(tx => <article key={tx.idPreVentaTransaccion} className="p-4 space-y-3"><div className="flex items-start justify-between"><div><p className="text-xs text-slate-400">Pre-venta #{tx.idPreVentaTransaccion} · {fmtDate(tx.fecha)}</p><h3 className="font-semibold text-slate-900 dark:text-white">{tx.clienteNombre}</h3></div><PaymentBadge status={tx.estadoPago} /></div><div className="grid grid-cols-3 gap-2"><Metric label="Total" value={money(tx.total)} /><Metric label="Pagado" value={money(tx.pagado)} /><Metric label="Pendiente" value={money(tx.pendiente)} /></div><button type="button" className="btn-secondary w-full text-sm" onClick={() => onOpen(tx)}>Ver {tx.cantidadItems} {tx.cantidadItems === 1 ? 'disco' : 'discos'}</button></article>)}</div>
  </section>
}

function TransactionPanel({ transaction, discos, busy, onClose, onUpdated, onMarkPaid, onDelete, onPayAll, onError }) {
  const [drafts, setDrafts] = useState({})
  const [query, setQuery] = useState('')
  const [manual, setManual] = useState(emptyManual)
  const [showManual, setShowManual] = useState(false)
  const [actionBusy, setActionBusy] = useState('')
  const actionInFlight = useRef(false)
  const addRetry = useRef(null)
  const dirty = Object.keys(drafts).length > 0
  const panelBusy = busy || Boolean(actionBusy)
  const matches = useMemo(() => {
    const q = normalize(query)
    return discos.filter(disco => normalize([disco.artista, disco.album, disco.codigoInterno].filter(Boolean).join(' ')).includes(q)).slice(0, 6)
  }, [discos, query])

  function close() {
    if (dirty && !window.confirm('Hay cambios de ítems sin guardar. ¿Cerrar el detalle?')) return
    onClose()
  }

  function draftFor(item) {
    return drafts[item.idPreVenta] || {
      cantidad: String(item.cantidad),
      precioUnitario: String(Number(item.precio || 0) / Number(item.cantidad || 1)),
      notas: item.notas || '',
    }
  }

  function change(item, field, value) {
    setDrafts(current => ({ ...current, [item.idPreVenta]: { ...draftFor(item), [field]: value } }))
  }

  async function saveItem(item) {
    if (actionInFlight.current) return
    const draft = draftFor(item)
    if (!quantity(draft) || !unitPrice(draft)) return onError('Cantidad y precio deben ser positivos.')
    actionInFlight.current = true
    setActionBusy(`save-${item.idPreVenta}`)
    try {
      const updated = await api.preVentaTransacciones.actualizarItem(transaction.idPreVentaTransaccion, item.idPreVenta, {
        cantidad: quantity(draft), precio: lineTotal(draft), notas: draft.notas || null,
      })
      onUpdated(updated)
      setDrafts(current => { const next = { ...current }; delete next[item.idPreVenta]; return next })
      onError('')
    } catch (err) { onError(err.message) }
    finally { actionInFlight.current = false; setActionBusy('') }
  }

  async function addItem(item) {
    if (actionInFlight.current) return
    const payload = {
      idDisco: item.idDisco || null, descripcion: item.descripcion || null,
      codigoDisco: item.codigoDisco || null, cantidad: quantity(item),
      precio: lineTotal(item), notas: item.notas || null,
    }
    const signature = JSON.stringify(payload)
    const idempotencyKey = addRetry.current?.signature === signature
      ? addRetry.current.idempotencyKey : newKey()
    addRetry.current = { signature, idempotencyKey }
    actionInFlight.current = true
    setActionBusy('add')
    try {
      const updated = await api.preVentaTransacciones.agregarItem(transaction.idPreVentaTransaccion, {
        ...payload, idempotencyKey,
      })
      addRetry.current = null
      onUpdated(updated); setQuery(''); setManual(emptyManual()); setShowManual(false); onError('')
    } catch (err) { onError(err.message) }
    finally { actionInFlight.current = false; setActionBusy('') }
  }

  return <><div className="fixed inset-0 z-40 bg-black/30" onClick={close} /><aside className="fixed inset-y-0 right-0 z-50 w-full max-w-2xl bg-white dark:bg-stone-950 border-l border-slate-200 dark:border-stone-800 shadow-2xl overflow-y-auto" role="dialog" aria-modal="true">
    <div className="sticky top-0 z-10 bg-white/95 dark:bg-stone-950/95 backdrop-blur p-5 border-b border-slate-100 dark:border-stone-800 flex justify-between gap-3"><div><p className="text-xs uppercase tracking-wider text-slate-400">Pre-venta #{transaction.idPreVentaTransaccion}</p><h2 className="font-bold text-slate-900 dark:text-white">{transaction.clienteNombre}</h2><p className="text-sm text-slate-400">{fmtDate(transaction.fecha)} · {transaction.cantidadItems} ítems</p></div><button type="button" onClick={close} aria-label="Cerrar">✕</button></div>
    <div className="p-5 space-y-5">
      <section className="grid grid-cols-2 sm:grid-cols-4 gap-3"><Metric label="Total" value={money(transaction.total)} /><Metric label="Pagado" value={money(transaction.pagado)} /><Metric label="Pendiente" value={money(transaction.pendiente)} /><div className="rounded-lg bg-slate-50 dark:bg-stone-900 p-3"><p className="text-[10px] uppercase tracking-wider text-slate-400">Estado</p><div className="mt-1"><PaymentBadge status={transaction.estadoPago} /></div></div></section>
      {actionBusy && <p role="status" className="text-sm text-slate-500">{actionBusy === 'add' ? 'Agregando ítem…' : 'Guardando cambios…'}</p>}
      {Number(transaction.pendiente) > 0 && <button type="button" className="w-full rounded-xl bg-emerald-600 px-4 py-3 font-semibold text-white hover:bg-emerald-700 disabled:opacity-50" disabled={panelBusy} onClick={onPayAll}>Pagar saldo pendiente · {money(transaction.pendiente)}</button>}
      <section className="space-y-3">{transaction.items.map(item => {
        const paid = item.estado === 'PAGADA'
        const draft = draftFor(item)
        return <article key={item.idPreVenta} className="rounded-xl border border-slate-200 dark:border-stone-800 p-4 space-y-3"><div className="flex items-start justify-between gap-3"><div><h3 className="font-semibold text-slate-900 dark:text-white">{itemTitle(item)}</h3><p className="text-xs text-slate-400">Ítem #{item.idPreVenta}{item.codigoDisco ? ` · ${item.codigoDisco}` : ''}</p></div><PaymentBadge status={paid ? 'PAGADA' : 'PENDIENTE'} /></div>
          {paid ? <div className="grid grid-cols-3 gap-2"><Metric label="Cantidad" value={item.cantidad} /><Metric label="Total" value={money(item.precio)} /><Metric label="Pago" value={item.fechaPago ? fmtDate(item.fechaPago) : 'Registrado'} /></div> : <><div className="grid gap-3 sm:grid-cols-3"><Field label="Cantidad"><input className="input" type="number" min="1" step="1" value={draft.cantidad} onChange={e => change(item, 'cantidad', e.target.value)} /></Field><Field label="Precio por unidad"><input className="input" type="number" min="0.01" step="0.01" value={draft.precioUnitario} onChange={e => change(item, 'precioUnitario', e.target.value)} /></Field><Field label="Subtotal"><div className="input flex items-center font-semibold">{money(lineTotal(draft))}</div></Field></div><div className="flex flex-wrap gap-2"><button type="button" className="btn-secondary text-xs" disabled={panelBusy || !drafts[item.idPreVenta]} onClick={() => saveItem(item)}>{actionBusy === `save-${item.idPreVenta}` ? 'Guardando…' : 'Guardar cambios'}</button><button type="button" className="rounded-lg bg-emerald-600 px-3 py-2 text-xs text-white" disabled={panelBusy} onClick={() => onMarkPaid(item)}>Marcar pago</button><button type="button" className="rounded-lg border border-red-200 px-3 py-2 text-xs text-red-600" disabled={panelBusy} onClick={() => onDelete(item)}>Eliminar</button></div></>}
        </article>
      })}</section>
      <section className="border-t border-slate-100 dark:border-stone-800 pt-5 space-y-3"><h3 className="text-xs font-semibold uppercase tracking-wider text-slate-500">Agregar ítem</h3><input className="input" value={query} onChange={e => setQuery(e.target.value)} placeholder="Buscar catálogo…" /><div className="grid gap-2 sm:grid-cols-2">{matches.map(disco => <button type="button" key={disco.idDisco} disabled={panelBusy} className="rounded-lg border p-3 text-left disabled:opacity-50" onClick={() => addItem(catalogItem(disco))}><span className="block text-sm font-medium">{[disco.artista, disco.album].filter(Boolean).join(' — ')}</span><span className="text-xs text-slate-400">{money(disco.precioVenta)}</span></button>)}</div><button type="button" className="btn-secondary text-sm" disabled={panelBusy} onClick={() => setShowManual(value => !value)}>Agregar manual</button>{showManual && <ManualItemForm form={manual} setForm={setManual} disabled={panelBusy} onAdd={() => { const item = manualItem(manual); if (!item.descripcion || !quantity(item) || !unitPrice(item)) return onError('Completá descripción, cantidad y precio.'); addItem(item) }} onCancel={() => setShowManual(false)} />}</section>
    </div>
  </aside></>
}

function PaymentBadge({ status }) {
  return <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium whitespace-nowrap ${paymentClasses(status)}`}>{paymentLabel(status)}</span>
}

function Metric({ label, value }) {
  return <div className="rounded-lg bg-slate-50 dark:bg-stone-900 p-3"><p className="text-[10px] uppercase tracking-wider text-slate-400">{label}</p><p className="text-sm font-semibold text-slate-800 dark:text-stone-200 tabular-nums">{value}</p></div>
}

function Field({ label, children, className = '' }) {
  return <div className={className}><label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1.5">{label}</label>{children}</div>
}

function NuevoClienteModal({ onCerrar, onCreado }) {
  const [form, setForm] = useState({ nombre: '', apellido: '', cedula: '', telefono: '', instagramUsuario: '', email: '', direccion: '', observaciones: '' })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const set = (field, value) => setForm(current => ({ ...current, [field]: value }))
  async function submit(event) {
    event.preventDefault()
    if (!form.nombre.trim()) return setError('El nombre es obligatorio')
    setLoading(true); setError('')
    try { onCreado(await api.clientes.crear(form)) } catch (err) { setError(err.message) } finally { setLoading(false) }
  }
  return <div className="fixed inset-0 z-[60] bg-black/60 flex items-center justify-center p-4" onClick={onCerrar}><div className="bg-white dark:bg-stone-900 rounded-2xl shadow-2xl w-full max-w-2xl p-6" onClick={e => e.stopPropagation()}><div className="flex justify-between mb-4"><div><h3 className="font-bold text-slate-900 dark:text-white">Nuevo cliente</h3><p className="text-sm text-slate-400">Crealo sin salir de la pre-venta.</p></div><button type="button" onClick={onCerrar}>✕</button></div><form onSubmit={submit} className="space-y-4"><div className="grid gap-3 sm:grid-cols-2"><Field label="Nombre"><input className="input" value={form.nombre} onChange={e => set('nombre', e.target.value)} /></Field><Field label="Apellido"><input className="input" value={form.apellido} onChange={e => set('apellido', e.target.value)} /></Field><Field label="Cédula"><input className="input" value={form.cedula} onChange={e => set('cedula', e.target.value)} /></Field><Field label="Teléfono"><input className="input" value={form.telefono} onChange={e => set('telefono', e.target.value)} /></Field><Field label="Instagram"><input className="input" value={form.instagramUsuario} onChange={e => set('instagramUsuario', e.target.value)} /></Field><Field label="Email"><input className="input" value={form.email} onChange={e => set('email', e.target.value)} /></Field><Field label="Dirección" className="sm:col-span-2"><input className="input" value={form.direccion} onChange={e => set('direccion', e.target.value)} /></Field><Field label="Observaciones" className="sm:col-span-2"><textarea className="input" value={form.observaciones} onChange={e => set('observaciones', e.target.value)} /></Field></div>{error && <p className="text-sm text-red-500">{error}</p>}<div className="flex gap-3"><button type="button" className="btn-secondary flex-1" onClick={onCerrar}>Cancelar</button><button className="btn-primary flex-1" disabled={loading}>{loading ? 'Guardando…' : 'Crear cliente'}</button></div></form></div></div>
}

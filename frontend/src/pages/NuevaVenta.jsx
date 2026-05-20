import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { api } from '../api/sonograma'

const DEPARTAMENTOS = [
  'Artigas','Canelones','Cerro Largo','Colonia','Durazno','Flores','Florida',
  'Lavalleja','Maldonado','Montevideo','Paysandú','Río Negro','Rivera','Rocha',
  'Salto','San José','Soriano','Tacuarembó','Treinta y Tres',
]

function DiscoCard({ disco }) {
  return (
    <div className="bg-slate-50 dark:bg-stone-900 border border-slate-200 dark:border-stone-700 rounded-xl p-4">
      <div className="font-bold text-slate-900 dark:text-white">{disco.artista}</div>
      <div className="text-slate-600 dark:text-stone-300 text-sm">{disco.album}</div>
      <div className="flex items-center gap-3 mt-2 text-xs text-slate-400 dark:text-stone-500">
        {disco.anio && <span>{disco.anio}</span>}
        {disco.genero && <span>· {disco.genero}</span>}
        {disco.tipoDisco && <span>· {disco.tipoDisco}</span>}
      </div>
      {disco.precioVenta && (
        <div className="mt-2 font-bold text-slate-900 dark:text-white tabular-nums">
          ${Number(disco.precioVenta).toLocaleString('es-AR', { maximumFractionDigits: 0 })}
          <span className="text-xs font-normal text-slate-400 dark:text-stone-500 ml-1">precio sugerido</span>
        </div>
      )}
    </div>
  )
}

export default function NuevaVenta() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const debounceRef = useRef(null)

  const idDiscoParam = searchParams.get('idDisco')

  const [disco, setDisco] = useState(null)
  const [discosDisponibles, setDiscosDisponibles] = useState([])
  const [busquedaDisco, setBusquedaDisco] = useState('')

  const [clienteSeleccionado, setClienteSeleccionado] = useState(null)
  const [busquedaCliente, setBusquedaCliente] = useState('')
  const [sugerenciasCliente, setSugerenciasCliente] = useState([])
  const [mostrarSugerencias, setMostrarSugerencias] = useState(false)

  const [canalVenta, setCanalVenta] = useState('LOCAL')
  const [tipoEntrega, setTipoEntrega] = useState('RETIRO')
  const [departamento, setDepartamento] = useState('')
  const [direccionEnvio, setDireccionEnvio] = useState('')
  const [total, setTotal] = useState('')
  const [observaciones, setObservaciones] = useState('')

  const [errores, setErrores] = useState({})
  const [enviando, setEnviando] = useState(false)
  const [mostrarModalCliente, setMostrarModalCliente] = useState(false)

  // Cargar disco por query param
  useEffect(() => {
    if (idDiscoParam) {
      api.discos.todos()
        .then(discos => {
          const d = discos.find(x => String(x.idDisco) === idDiscoParam)
          if (d) {
            setDisco(d)
            setTotal(d.precioVenta ? String(Math.round(Number(d.precioVenta))) : '')
          }
        })
    } else {
      api.discos.todos()
        .then(ds => setDiscosDisponibles(ds.filter(d => d.estado === 'DISPONIBLE' || d.estado === 'RESERVADO')))
    }
  }, [idDiscoParam])

  function onBusquedaDiscoChange(e) {
    const q = e.target.value
    setBusquedaDisco(q)
    if (!q.trim()) {
      api.discos.todos().then(ds =>
        setDiscosDisponibles(ds.filter(d => d.estado === 'DISPONIBLE' || d.estado === 'RESERVADO'))
      )
      return
    }
    api.discos.buscar(q).then(ds =>
      setDiscosDisponibles(ds.filter(d => d.estado === 'DISPONIBLE' || d.estado === 'RESERVADO'))
    )
  }

  function seleccionarDisco(d) {
    setDisco(d)
    setTotal(d.precioVenta ? String(Math.round(Number(d.precioVenta))) : '')
    setBusquedaDisco('')
  }

  function onBusquedaClienteChange(e) {
    const q = e.target.value
    setBusquedaCliente(q)
    setMostrarSugerencias(true)
    clearTimeout(debounceRef.current)
    if (!q.trim()) { setSugerenciasCliente([]); return }
    debounceRef.current = setTimeout(async () => {
      try {
        const data = await api.clientes.buscar(q.trim())
        setSugerenciasCliente(data)
      } catch { setSugerenciasCliente([]) }
    }, 300)
  }

  function seleccionarCliente(c) {
    setClienteSeleccionado(c)
    setBusquedaCliente('')
    setSugerenciasCliente([])
    setMostrarSugerencias(false)
  }

  function validar() {
    const e = {}
    if (!disco) e.disco = 'Seleccioná un disco'
    if (!clienteSeleccionado) e.cliente = 'Seleccioná un cliente'
    if (!total || parseFloat(total) <= 0) e.total = 'Ingresá un total válido'
    if (tipoEntrega === 'ENVIO') {
      if (!departamento) e.departamento = 'Seleccioná el departamento'
      if (!direccionEnvio.trim()) e.direccionEnvio = 'Ingresá la dirección de envío'
    }
    return e
  }

  async function handleSubmit(e) {
    e.preventDefault()
    const e2 = validar()
    if (Object.keys(e2).length > 0) { setErrores(e2); return }
    setErrores({})
    setEnviando(true)
    try {
      await api.ventas.registrar({
        idCliente: clienteSeleccionado.idCliente,
        idDisco: disco.idDisco,
        canalVenta,
        total: parseFloat(total),
        tipoEntrega,
        observaciones: observaciones || null,
        ...(tipoEntrega === 'ENVIO' && { direccionEnvio, departamento }),
      })
      navigate('/')
    } catch (err) {
      setErrores({ general: err.message })
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div className="max-w-2xl mx-auto px-4 sm:px-6 py-6 space-y-5">

      <div>
        <h1 className="text-xl font-bold text-slate-900 dark:text-white">Registrar nueva venta</h1>
        <p className="text-slate-400 dark:text-stone-500 text-sm mt-0.5">Completá los datos para registrar la venta</p>
      </div>

      <form onSubmit={handleSubmit} className="space-y-5">

        {/* Disco */}
        <div className="card p-5 space-y-3">
          <h2 className="text-sm font-semibold text-slate-700 dark:text-stone-300 uppercase tracking-wider">Disco</h2>
          {disco ? (
            <div className="space-y-2">
              <DiscoCard disco={disco} />
              {!idDiscoParam && (
                <button type="button" onClick={() => setDisco(null)} className="text-xs text-slate-400 hover:text-red-500 transition-colors">
                  Cambiar disco
                </button>
              )}
            </div>
          ) : (
            <div className="space-y-3">
              <div className="relative">
                <svg className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 dark:text-stone-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="m21 21-5.197-5.197m0 0A7.5 7.5 0 1 0 5.196 5.196a7.5 7.5 0 0 0 10.607 10.607Z" />
                </svg>
                <input
                  value={busquedaDisco}
                  onChange={onBusquedaDiscoChange}
                  placeholder="Buscar disco disponible..."
                  className="input pl-9"
                />
              </div>
              <div className="space-y-2 max-h-60 overflow-y-auto">
                {discosDisponibles.map(d => (
                  <button
                    key={d.idDisco}
                    type="button"
                    onClick={() => seleccionarDisco(d)}
                    className="w-full text-left px-4 py-3 rounded-lg bg-slate-50 dark:bg-stone-900 hover:bg-[#7E9FA8]/10 dark:hover:bg-[#7E9FA8]/10 border border-transparent hover:border-[#7E9FA8]/30 transition-all"
                  >
                    <div className="font-medium text-slate-800 dark:text-stone-200 text-sm">{d.artista} — {d.album}</div>
                    <div className="text-xs text-slate-400 dark:text-stone-500 mt-0.5">
                      {d.anio && <span>{d.anio} · </span>}
                      {d.estado} · {d.precioVenta ? `$${Number(d.precioVenta).toLocaleString('es-AR', { maximumFractionDigits: 0 })}` : '—'}
                    </div>
                  </button>
                ))}
                {discosDisponibles.length === 0 && (
                  <p className="text-slate-400 dark:text-stone-600 text-sm text-center py-4">Sin discos disponibles</p>
                )}
              </div>
            </div>
          )}
          {errores.disco && <p className="text-red-500 text-xs mt-1">{errores.disco}</p>}
        </div>

        {/* Cliente */}
        <div className="card p-5 space-y-3">
          <h2 className="text-sm font-semibold text-slate-700 dark:text-stone-300 uppercase tracking-wider">Cliente</h2>
          {clienteSeleccionado ? (
            <div className="flex items-center justify-between bg-slate-50 dark:bg-stone-900 border border-slate-200 dark:border-stone-700 rounded-xl px-4 py-3">
              <div>
                <div className="font-semibold text-slate-900 dark:text-white text-sm">
                  {clienteSeleccionado.nombre} {clienteSeleccionado.apellido}
                </div>
                {clienteSeleccionado.cedula && (
                  <div className="text-xs text-slate-400 dark:text-stone-500 mt-0.5">CI {clienteSeleccionado.cedula}</div>
                )}
              </div>
              <button type="button" onClick={() => setClienteSeleccionado(null)} className="text-xs text-slate-400 hover:text-red-500 transition-colors">Cambiar</button>
            </div>
          ) : (
            <div className="space-y-2">
              <div className="relative">
                <input
                  value={busquedaCliente}
                  onChange={onBusquedaClienteChange}
                  onFocus={() => setMostrarSugerencias(true)}
                  placeholder="Buscar cliente por nombre..."
                  className="input"
                  autoComplete="off"
                />
                {mostrarSugerencias && sugerenciasCliente.length > 0 && (
                  <div className="absolute z-10 w-full mt-1 bg-white dark:bg-stone-900 border border-slate-200 dark:border-stone-700 rounded-xl shadow-lg overflow-hidden">
                    {sugerenciasCliente.map(c => (
                      <button
                        key={c.idCliente}
                        type="button"
                        onClick={() => seleccionarCliente(c)}
                        className="w-full text-left px-4 py-2.5 hover:bg-slate-50 dark:hover:bg-stone-800 transition-colors"
                      >
                        <div className="font-medium text-slate-800 dark:text-stone-200 text-sm">{c.nombre} {c.apellido}</div>
                        {c.cedula && <div className="text-xs text-slate-400 dark:text-stone-500">CI {c.cedula}</div>}
                      </button>
                    ))}
                  </div>
                )}
              </div>
              <button
                type="button"
                onClick={() => setMostrarModalCliente(true)}
                className="text-xs text-[#5C7D87] dark:text-[#7E9FA8] hover:underline"
              >
                + Nuevo cliente
              </button>
            </div>
          )}
          {errores.cliente && <p className="text-red-500 text-xs mt-1">{errores.cliente}</p>}
        </div>

        {/* Canal y entrega */}
        <div className="card p-5 space-y-4">
          <h2 className="text-sm font-semibold text-slate-700 dark:text-stone-300 uppercase tracking-wider">Detalles de la venta</h2>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Canal</label>
              <select value={canalVenta} onChange={e => setCanalVenta(e.target.value)} className="input">
                <option value="LOCAL">Local</option>
                <option value="INSTAGRAM">Instagram</option>
              </select>
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Entrega</label>
              <select value={tipoEntrega} onChange={e => setTipoEntrega(e.target.value)} className="input">
                <option value="RETIRO">Retiro</option>
                <option value="ENVIO">Envío</option>
              </select>
            </div>
          </div>

          {tipoEntrega === 'ENVIO' && (
            <div className="space-y-3 pt-1">
              <div>
                <label className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Departamento</label>
                <select value={departamento} onChange={e => setDepartamento(e.target.value)} className="input">
                  <option value="">Seleccioná departamento...</option>
                  {DEPARTAMENTOS.map(d => <option key={d} value={d}>{d}</option>)}
                </select>
                {errores.departamento && <p className="text-red-500 text-xs mt-1">{errores.departamento}</p>}
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Dirección de envío</label>
                <input
                  value={direccionEnvio}
                  onChange={e => setDireccionEnvio(e.target.value)}
                  placeholder="Calle y número..."
                  className="input"
                />
                {errores.direccionEnvio && <p className="text-red-500 text-xs mt-1">{errores.direccionEnvio}</p>}
              </div>
            </div>
          )}

          <div>
            <label className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Total (UYU)</label>
            <input
              type="number"
              step="0.01"
              min="0"
              value={total}
              onChange={e => setTotal(e.target.value)}
              placeholder="0.00"
              className="input"
            />
            {errores.total && <p className="text-red-500 text-xs mt-1">{errores.total}</p>}
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Observaciones</label>
            <textarea
              value={observaciones}
              onChange={e => setObservaciones(e.target.value)}
              placeholder="Notas opcionales..."
              rows={2}
              className="input resize-none"
            />
          </div>
        </div>

        {errores.general && (
          <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 text-red-600 dark:text-red-300 text-sm rounded-xl px-4 py-3">
            {errores.general}
          </div>
        )}

        <div className="flex gap-3">
          <button type="button" onClick={() => navigate('/')} className="btn-secondary flex-1">Cancelar</button>
          <button type="submit" disabled={enviando} className="btn-primary flex-1">
            {enviando ? 'Registrando...' : 'Registrar venta'}
          </button>
        </div>
      </form>

      {/* Modal nuevo cliente */}
      {mostrarModalCliente && (
        <NuevoClienteModal
          onCerrar={() => setMostrarModalCliente(false)}
          onCreado={(c) => { setClienteSeleccionado(c); setMostrarModalCliente(false) }}
        />
      )}
    </div>
  )
}

function NuevoClienteModal({ onCerrar, onCreado }) {
  const [form, setForm] = useState({
    nombre: '', apellido: '', cedula: '', telefono: '',
    instagramUsuario: '', email: '', direccion: '', observaciones: ''
  })
  const [error, setError] = useState('')
  const [cargando, setCargando] = useState(false)

  function set(field, value) {
    setForm(f => ({ ...f, [field]: value }))
  }

  async function handleSubmit(e) {
    e.preventDefault()
    if (!form.nombre.trim()) { setError('El nombre es obligatorio'); return }
    if (!form.apellido.trim()) { setError('El apellido es obligatorio'); return }
    setError('')
    setCargando(true)
    try {
      const nuevo = await api.clientes.crear(form)
      onCreado(nuevo)
    } catch (err) {
      setError(err.message)
    } finally {
      setCargando(false)
    }
  }

  return (
    <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 px-4 py-6 overflow-y-auto" onClick={onCerrar}>
      <div className="bg-white dark:bg-stone-900 rounded-2xl shadow-2xl border border-slate-200 dark:border-stone-700 w-full max-w-md my-auto" onClick={e => e.stopPropagation()}>
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 dark:border-stone-800">
          <h2 className="font-bold text-slate-900 dark:text-white text-base">Nuevo cliente</h2>
          <button onClick={onCerrar} className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-stone-800 transition-colors">
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18 18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
        <form onSubmit={handleSubmit} className="px-6 py-5 space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Nombre <span className="text-red-400">*</span></label>
              <input value={form.nombre} onChange={e => set('nombre', e.target.value)} className="input" placeholder="Martín" />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Apellido <span className="text-red-400">*</span></label>
              <input value={form.apellido} onChange={e => set('apellido', e.target.value)} className="input" placeholder="Rodríguez" />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Cédula</label>
              <input value={form.cedula} onChange={e => set('cedula', e.target.value)} className="input" placeholder="41234567" />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Teléfono</label>
              <input value={form.telefono} onChange={e => set('telefono', e.target.value)} className="input" placeholder="099111222" />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Instagram</label>
              <input value={form.instagramUsuario} onChange={e => set('instagramUsuario', e.target.value)} className="input" placeholder="@usuario" />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Email</label>
              <input value={form.email} onChange={e => set('email', e.target.value)} className="input" placeholder="mail@gmail.com" type="email" />
            </div>
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Dirección</label>
            <input value={form.direccion} onChange={e => set('direccion', e.target.value)} className="input" placeholder="Av. 18 de Julio 1234, Montevideo" />
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Observaciones</label>
            <input value={form.observaciones} onChange={e => set('observaciones', e.target.value)} className="input" placeholder="Notas opcionales..." />
          </div>
          {error && (
            <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 text-red-600 dark:text-red-300 text-sm rounded-lg px-4 py-3">{error}</div>
          )}
          <div className="flex gap-3 pt-1">
            <button type="button" onClick={onCerrar} className="btn-secondary flex-1">Cancelar</button>
            <button type="submit" disabled={cargando} className="btn-primary flex-1">{cargando ? 'Guardando...' : 'Crear cliente'}</button>
          </div>
        </form>
      </div>
    </div>
  )
}

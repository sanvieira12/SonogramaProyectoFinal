import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { api } from '../api/sonograma'

const DEPARTAMENTOS_FALLBACK = [
  'Artigas','Canelones','Cerro Largo','Colonia','Durazno','Flores','Florida',
  'Lavalleja','Maldonado','Montevideo','Paysandú','Río Negro','Rivera','Rocha',
  'Salto','San José','Soriano','Tacuarembó','Treinta y Tres',
]

const ESTADO_LABELS = {
  DISPONIBLE: 'Disponible',
  RESERVADO: 'Reservado',
  VENDIDO: 'Vendido',
  FUERA_STOCK: 'Fuera de stock',
  DESCONTINUADO: 'Descontinuado',
}

function money(valor) {
  return `$${Number(valor || 0).toLocaleString('es-UY', { maximumFractionDigits: 0 })}`
}

function toNumber(valor) {
  const n = Number(valor)
  return Number.isFinite(n) ? n : 0
}

function DiscoResumen({ disco }) {
  return (
    <div className="rounded-lg border border-slate-200 dark:border-stone-700 p-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <div className="font-bold text-slate-900 dark:text-white">{disco.artista}</div>
          <div className="text-slate-600 dark:text-stone-300 text-sm">{disco.album}</div>
          <div className="flex items-center gap-1.5 mt-2 text-xs text-slate-400 dark:text-stone-500 flex-wrap">
            {disco.codigoInterno && <span>{disco.codigoInterno}</span>}
            {disco.genero && <span>· {disco.genero}</span>}
            {disco.anio && <span>· {disco.anio}</span>}
            {disco.selloDiscografico && <span>· {disco.selloDiscografico}</span>}
          </div>
        </div>
        <span className="text-xs px-2.5 py-1 rounded-full bg-slate-100 dark:bg-stone-800 text-slate-600 dark:text-stone-300 whitespace-nowrap">
          {ESTADO_LABELS[disco.estado] || disco.estado}
        </span>
      </div>
      {disco.precioVenta && (
        <div className="mt-3 font-bold text-slate-900 dark:text-white tabular-nums">
          {money(disco.precioVenta)}
          <span className="text-xs font-normal text-slate-400 dark:text-stone-500 ml-1">precio sugerido</span>
        </div>
      )}
    </div>
  )
}

function ResumenLinea({ label, value, strong }) {
  return (
    <div className="flex items-center justify-between gap-3 text-sm">
      <span className="text-slate-500 dark:text-stone-400">{label}</span>
      <span className={`${strong ? 'text-base font-bold text-slate-900 dark:text-white' : 'font-semibold text-slate-700 dark:text-stone-200'} tabular-nums`}>
        {value}
      </span>
    </div>
  )
}

export default function NuevaVenta() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const debounceRef = useRef(null)

  const idDiscoParam = searchParams.get('idDisco')

  const [departamentos, setDepartamentos] = useState(DEPARTAMENTOS_FALLBACK)
  const [disco, setDisco] = useState(null)
  const [discosDisponibles, setDiscosDisponibles] = useState([])
  const [busquedaDisco, setBusquedaDisco] = useState('')

  const [clienteSeleccionado, setClienteSeleccionado] = useState(null)
  const [busquedaCliente, setBusquedaCliente] = useState('')
  const [sugerenciasCliente, setSugerenciasCliente] = useState([])
  const [mostrarSugerencias, setMostrarSugerencias] = useState(false)
  const [direccionesCliente, setDireccionesCliente] = useState([])
  const [direccionSeleccionadaId, setDireccionSeleccionadaId] = useState('')
  const [direccionModo, setDireccionModo] = useState('NUEVA')

  const [canalVenta, setCanalVenta] = useState('LOCAL')
  const [tipoEntrega, setTipoEntrega] = useState('RETIRO')
  const [departamento, setDepartamento] = useState('')
  const [direccionEnvio, setDireccionEnvio] = useState('')
  const [sucursalesDac, setSucursalesDac] = useState([])
  const [sucursalDacCodigo, setSucursalDacCodigo] = useState('')
  const [sucursalDacNombre, setSucursalDacNombre] = useState('')
  const [cargandoSucursales, setCargandoSucursales] = useState(false)
  const [costoEnvio, setCostoEnvio] = useState('')

  const [precioVenta, setPrecioVenta] = useState('')
  const [porcentajeImpuesto, setPorcentajeImpuesto] = useState('0')
  const [otrosCostos, setOtrosCostos] = useState('0')
  const [observaciones, setObservaciones] = useState('')

  const [errores, setErrores] = useState({})
  const [enviando, setEnviando] = useState(false)
  const [mostrarModalCliente, setMostrarModalCliente] = useState(false)

  useEffect(() => {
    api.envios.departamentosDac()
      .then(ds => ds.length && setDepartamentos(ds))
      .catch(() => setDepartamentos(DEPARTAMENTOS_FALLBACK))

    api.ventas.configuracionCostos()
      .then(cfg => {
        setPorcentajeImpuesto(String(cfg.porcentajeImpuesto ?? 0))
        setOtrosCostos(String(cfg.otrosCostos ?? 0))
      })
      .catch(() => {})
  }, [])

  useEffect(() => {
    if (idDiscoParam) {
      api.discos.porId(idDiscoParam)
        .then(d => {
          setDisco(d)
          setPrecioVenta(d.precioVenta ? String(Math.round(Number(d.precioVenta))) : '')
        })
        .catch(() => setErrores({ disco: 'No se pudo cargar el disco del link' }))
    } else {
      api.discos.todos()
        .then(ds => setDiscosDisponibles(ds.filter(d => d.estado === 'DISPONIBLE' || d.estado === 'RESERVADO')))
        .catch(() => setDiscosDisponibles([]))
    }
  }, [idDiscoParam])

  useEffect(() => {
    if (tipoEntrega !== 'ENVIO' || !departamento) return

    let cancelado = false
    api.envios.sucursalesDac(departamento)
      .then(data => { if (!cancelado) setSucursalesDac(data) })
      .catch(() => { if (!cancelado) setSucursalesDac([]) })
      .finally(() => { if (!cancelado) setCargandoSucursales(false) })
    return () => { cancelado = true }
  }, [departamento, tipoEntrega])

  const totales = useMemo(() => {
    const base = toNumber(precioVenta)
    const envio = tipoEntrega === 'ENVIO' ? toNumber(costoEnvio) : 0
    const otros = toNumber(otrosCostos)
    const impuestoPct = toNumber(porcentajeImpuesto)
    const impuesto = (base + envio + otros) * impuestoPct / 100
    const totalFinal = base + envio + otros + impuesto
    const ganancia = totalFinal - toNumber(disco?.costo) - envio - otros - impuesto
    return { base, envio, otros, impuestoPct, impuesto, totalFinal, ganancia }
  }, [precioVenta, costoEnvio, tipoEntrega, otrosCostos, porcentajeImpuesto, disco])

  async function cargarDiscosVendibles() {
    const ds = await api.discos.todos()
    setDiscosDisponibles(ds.filter(d => d.estado === 'DISPONIBLE' || d.estado === 'RESERVADO'))
  }

  function cambiarTipoEntrega(valor) {
    setTipoEntrega(valor)
    if (valor !== 'ENVIO') {
      setSucursalesDac([])
      setSucursalDacCodigo('')
      setSucursalDacNombre('')
      setCostoEnvio('')
    } else if (departamento) {
      setCargandoSucursales(true)
    }
  }

  function cambiarDepartamento(valor) {
    setDepartamento(valor)
    setSucursalesDac([])
    setSucursalDacCodigo('')
    setSucursalDacNombre('')
    setCostoEnvio('')
    setCargandoSucursales(Boolean(valor && tipoEntrega === 'ENVIO'))
  }

  function onBusquedaDiscoChange(e) {
    const q = e.target.value
    setBusquedaDisco(q)
    if (!q.trim()) {
      cargarDiscosVendibles()
      return
    }
    api.discos.buscar(q).then(ds =>
      setDiscosDisponibles(ds.filter(d => d.estado === 'DISPONIBLE' || d.estado === 'RESERVADO'))
    )
  }

  function seleccionarDisco(d) {
    setDisco(d)
    setPrecioVenta(d.precioVenta ? String(Math.round(Number(d.precioVenta))) : '')
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

  async function seleccionarCliente(c) {
    setClienteSeleccionado(c)
    setBusquedaCliente('')
    setSugerenciasCliente([])
    setMostrarSugerencias(false)
    setDireccionesCliente([])
    setDireccionSeleccionadaId('')
    setDireccionEnvio('')
    setDireccionModo('NUEVA')

    try {
      const direcciones = await api.clientes.direcciones(c.idCliente)
      setDireccionesCliente(direcciones)
      if (direcciones.length > 0) aplicarDireccion(direcciones[0])
    } catch {
      setDireccionesCliente([])
    }
  }

  function aplicarDireccion(direccion) {
    setDireccionModo(direccion.idDireccion ? 'EXISTENTE' : 'NUEVA')
    setDireccionSeleccionadaId(direccion.idDireccion || '')
    setDireccionEnvio(direccion.direccion || '')
    if (direccion.departamento) cambiarDepartamento(direccion.departamento)
  }

  function nuevaDireccion() {
    setDireccionModo('NUEVA')
    setDireccionSeleccionadaId('')
    setDireccionEnvio('')
  }

  async function seleccionarSucursal(codigo) {
    setSucursalDacCodigo(codigo)
    const sucursal = sucursalesDac.find(s => s.codigo === codigo)
    setSucursalDacNombre(sucursal?.nombre || '')
    setCostoEnvio('')
    if (!codigo || !departamento) return

    try {
      const cotizacion = await api.envios.cotizarDac(departamento, codigo)
      setCostoEnvio(String(cotizacion.costoEstimado ?? ''))
      if (cotizacion.sucursalNombre) setSucursalDacNombre(cotizacion.sucursalNombre)
    } catch {
      setCostoEnvio('')
    }
  }

  function validar() {
    const e = {}
    if (!disco) e.disco = 'Seleccioná un disco'
    if (disco && !['DISPONIBLE', 'RESERVADO'].includes(disco.estado)) {
      e.disco = `El disco está ${ESTADO_LABELS[disco.estado]?.toLowerCase() || disco.estado} y no puede venderse`
    }
    if (!clienteSeleccionado) e.cliente = 'Seleccioná o registrá un cliente'
    if (totales.base <= 0) e.total = 'Ingresá un precio de venta válido'
    if (tipoEntrega === 'ENVIO') {
      if (!departamento) e.departamento = 'Seleccioná el departamento'
      if (!direccionEnvio.trim()) e.direccionEnvio = 'Ingresá la dirección de envío'
      if (sucursalesDac.length > 0 && !sucursalDacCodigo) e.sucursal = 'Seleccioná una sucursal DAC'
    }
    return e
  }

  async function handleSubmit(e) {
    e.preventDefault()
    const nuevosErrores = validar()
    if (Object.keys(nuevosErrores).length > 0) { setErrores(nuevosErrores); return }
    setErrores({})
    setEnviando(true)
    try {
      await api.ventas.registrar({
        idCliente: clienteSeleccionado.idCliente,
        idDisco: disco.idDisco,
        canalVenta,
        total: Number(totales.totalFinal.toFixed(2)),
        precioVenta: Number(totales.base.toFixed(2)),
        costoEnvio: Number(totales.envio.toFixed(2)),
        porcentajeImpuesto: Number(totales.impuestoPct.toFixed(2)),
        otrosCostos: Number(totales.otros.toFixed(2)),
        tipoEntrega,
        observaciones: observaciones || null,
        ...(tipoEntrega === 'ENVIO' && {
          idDireccionCliente: direccionSeleccionadaId ? Number(direccionSeleccionadaId) : null,
          direccionEnvio,
          departamento,
          guardarNuevaDireccion: direccionModo === 'NUEVA' || !direccionSeleccionadaId,
          sucursalDacCodigo: sucursalDacCodigo || null,
          sucursalDacNombre: sucursalDacNombre || null,
        }),
      })
      navigate('/')
    } catch (err) {
      setErrores({ general: err.message })
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 py-6 space-y-5">
      <div>
        <h1 className="text-xl font-bold text-slate-900 dark:text-white">Registrar nueva venta</h1>
        <p className="text-slate-400 dark:text-stone-500 text-sm mt-0.5">QR/link de disco, cliente, envío DAC y total final</p>
      </div>

      <form onSubmit={handleSubmit} className="space-y-5">
        <div className="card p-5 space-y-3">
          <h2 className="text-sm font-semibold text-slate-700 dark:text-stone-300 uppercase tracking-wider">Disco</h2>
          {disco ? (
            <div className="space-y-2">
              <DiscoResumen disco={disco} />
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
                    className="w-full text-left px-4 py-3 rounded-lg bg-slate-50 dark:bg-stone-950 hover:bg-[#7E9FA8]/10 dark:hover:bg-[#7E9FA8]/10 border border-transparent hover:border-[#7E9FA8]/30 transition-all"
                  >
                    <div className="font-medium text-slate-800 dark:text-stone-200 text-sm">{d.artista} — {d.album}</div>
                    <div className="text-xs text-slate-400 dark:text-stone-500 mt-0.5">
                      {d.codigoInterno && <span>{d.codigoInterno} · </span>}
                      {d.genero && <span>{d.genero} · </span>}
                      {ESTADO_LABELS[d.estado] || d.estado} · {d.precioVenta ? money(d.precioVenta) : '—'}
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

        <div className="card p-5 space-y-3">
          <h2 className="text-sm font-semibold text-slate-700 dark:text-stone-300 uppercase tracking-wider">Cliente</h2>
          {clienteSeleccionado ? (
            <div className="flex items-center justify-between bg-slate-50 dark:bg-stone-950 border border-slate-200 dark:border-stone-700 rounded-lg px-4 py-3">
              <div>
                <div className="font-semibold text-slate-900 dark:text-white text-sm">
                  {clienteSeleccionado.nombre} {clienteSeleccionado.apellido}
                </div>
                <div className="text-xs text-slate-400 dark:text-stone-500 mt-0.5">
                  {[clienteSeleccionado.cedula && `CI ${clienteSeleccionado.cedula}`, clienteSeleccionado.telefono, clienteSeleccionado.instagramUsuario].filter(Boolean).join(' · ') || 'Cliente seleccionado'}
                </div>
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
                  placeholder="Buscar por nombre, cédula, Instagram, teléfono o dirección..."
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
                        <div className="text-xs text-slate-400 dark:text-stone-500">
                          {[c.cedula && `CI ${c.cedula}`, c.instagramUsuario, c.direccion].filter(Boolean).join(' · ') || 'Sin datos adicionales'}
                        </div>
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

        <div className="card p-5 space-y-4">
          <h2 className="text-sm font-semibold text-slate-700 dark:text-stone-300 uppercase tracking-wider">Entrega y costos</h2>

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
              <select value={tipoEntrega} onChange={e => cambiarTipoEntrega(e.target.value)} className="input">
                <option value="RETIRO">Retiro</option>
                <option value="ENVIO">Envío DAC</option>
              </select>
            </div>
          </div>

          {tipoEntrega === 'ENVIO' && (
            <div className="space-y-4 pt-1">
              {clienteSeleccionado && direccionesCliente.length > 0 && (
                <div>
                  <p className="text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-2">Direcciones anteriores</p>
                  <div className="flex flex-wrap gap-2">
                    {direccionesCliente.map((d, i) => (
                      <button
                        key={d.idDireccion || i}
                        type="button"
                        onClick={() => aplicarDireccion(d)}
                        className={`text-xs px-3 py-2 rounded-lg border transition-colors ${
                          direccionSeleccionadaId === (d.idDireccion || '')
                            ? 'border-[#7E9FA8] bg-[#7E9FA8]/10 text-[#5C7D87] dark:text-[#7E9FA8]'
                            : 'border-slate-200 dark:border-stone-700 text-slate-500 dark:text-stone-400 hover:bg-slate-50 dark:hover:bg-stone-800'
                        }`}
                      >
                        {d.direccion}{d.departamento ? `, ${d.departamento}` : ''}
                      </button>
                    ))}
                    <button type="button" onClick={nuevaDireccion} className="text-xs px-3 py-2 rounded-lg bg-slate-100 dark:bg-stone-800 text-slate-600 dark:text-stone-300">
                      Nueva dirección
                    </button>
                  </div>
                </div>
              )}

              {!clienteSeleccionado && (
                <p className="text-slate-400 dark:text-stone-600 text-sm">Seleccioná un cliente para ver direcciones usadas anteriormente.</p>
              )}

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">
                    Dirección {direccionModo === 'NUEVA' ? 'nueva' : 'seleccionada'}
                  </label>
                  <input
                    value={direccionEnvio}
                    onChange={e => { setDireccionEnvio(e.target.value); setDireccionModo('NUEVA'); setDireccionSeleccionadaId('') }}
                    placeholder="Calle y número..."
                    className="input"
                  />
                  {errores.direccionEnvio && <p className="text-red-500 text-xs mt-1">{errores.direccionEnvio}</p>}
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Departamento</label>
                  <select value={departamento} onChange={e => cambiarDepartamento(e.target.value)} className="input">
                    <option value="">Seleccioná departamento...</option>
                    {departamentos.map(d => <option key={d} value={d}>{d}</option>)}
                  </select>
                  {errores.departamento && <p className="text-red-500 text-xs mt-1">{errores.departamento}</p>}
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Sucursal DAC</label>
                <select
                  value={sucursalDacCodigo}
                  onChange={e => seleccionarSucursal(e.target.value)}
                  className="input"
                  disabled={!departamento || cargandoSucursales || sucursalesDac.length === 0}
                >
                  <option value="">{cargandoSucursales ? 'Cargando sucursales...' : 'Seleccioná sucursal...'}</option>
                  {sucursalesDac.map(s => <option key={s.codigo} value={s.codigo}>{s.nombre} · {s.direccion}</option>)}
                </select>
                {departamento && !cargandoSucursales && sucursalesDac.length === 0 && (
                  <p className="text-amber-600 dark:text-amber-400 text-xs mt-1">No hay sucursales DAC cargadas para este departamento.</p>
                )}
                {errores.sucursal && <p className="text-red-500 text-xs mt-1">{errores.sucursal}</p>}
              </div>
            </div>
          )}

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Precio disco (UYU)</label>
              <input type="number" step="0.01" min="0" value={precioVenta} onChange={e => setPrecioVenta(e.target.value)} className="input" />
              {errores.total && <p className="text-red-500 text-xs mt-1">{errores.total}</p>}
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Impuesto %</label>
              <input type="number" step="0.01" min="0" value={porcentajeImpuesto} onChange={e => setPorcentajeImpuesto(e.target.value)} className="input" />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Otros costos</label>
              <input type="number" step="0.01" min="0" value={otrosCostos} onChange={e => setOtrosCostos(e.target.value)} className="input" />
            </div>
          </div>

          <div className="rounded-lg bg-slate-50 dark:bg-stone-950 border border-slate-100 dark:border-stone-800 p-4 space-y-2">
            <ResumenLinea label="Precio de venta" value={money(totales.base)} />
            <ResumenLinea label="Costo de envío" value={money(totales.envio)} />
            <ResumenLinea label="Impuestos" value={money(totales.impuesto)} />
            <ResumenLinea label="Otros costos" value={money(totales.otros)} />
            <div className="border-t border-slate-200 dark:border-stone-800 pt-2">
              <ResumenLinea label="Total a cobrar" value={money(totales.totalFinal)} strong />
            </div>
            {disco && <ResumenLinea label="Ganancia estimada" value={money(totales.ganancia)} />}
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
            {enviando ? 'Registrando...' : 'Confirmar venta'}
          </button>
        </div>
      </form>

      {mostrarModalCliente && (
        <NuevoClienteModal
          onCerrar={() => setMostrarModalCliente(false)}
          onCreado={(c) => { seleccionarCliente(c); setMostrarModalCliente(false) }}
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
              <label className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Apellido</label>
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

import { useEffect, useRef, useState } from 'react'
import QRCode from 'react-qr-code'
import { discoService } from '../services/discoService'
import DiscoForm from '../components/DiscoForm'
import ConfirmModal from '../components/ConfirmModal'
import Paginacion from '../components/Paginacion'

const FILTROS = ['TODOS', 'DISPONIBLE', 'RESERVADO', 'VENDIDO', 'SIN_STOCK']

const ESTADO_LABELS = {
  DISPONIBLE: 'Disponible',
  RESERVADO:  'Reservado',
  VENDIDO:    'Vendido',
  SIN_STOCK:  'Sin stock',
}

const ESTADO_STYLE = {
  DISPONIBLE: { bg: 'bg-emerald-50 dark:bg-emerald-900/20', text: 'text-emerald-700 dark:text-emerald-400', dot: 'bg-[#5B8C7D]' },
  RESERVADO:  { bg: 'bg-amber-50 dark:bg-amber-900/20',     text: 'text-amber-700 dark:text-amber-400',     dot: 'bg-[#B8975E]' },
  VENDIDO:    { bg: 'bg-slate-100 dark:bg-slate-800/60',    text: 'text-slate-600 dark:text-slate-400',     dot: 'bg-[#6B7280]' },
  SIN_STOCK:  { bg: 'bg-slate-100 dark:bg-slate-800/50',    text: 'text-slate-500 dark:text-slate-400',     dot: 'bg-slate-400' },
}

function EstadoBadge({ estado }) {
  const s = ESTADO_STYLE[estado] || ESTADO_STYLE.SIN_STOCK
  return (
    <span className={`inline-flex items-center gap-1.5 text-xs px-2.5 py-1 rounded-full font-medium ${s.bg} ${s.text}`}>
      <span className={`w-1.5 h-1.5 rounded-full ${s.dot}`} />
      {ESTADO_LABELS[estado] || estado}
    </span>
  )
}

function Spinner() {
  return (
    <div className="flex items-center justify-center py-24 gap-3">
      <svg className="animate-spin w-5 h-5 text-[#7E9FA8]" viewBox="0 0 24 24" fill="none">
        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
      </svg>
      <span className="text-slate-500 dark:text-stone-400 text-sm">Cargando catálogo...</span>
    </div>
  )
}

function EmptyState({ hayFiltro }) {
  return (
    <div className="text-center py-20">
      <div className="w-16 h-16 rounded-2xl bg-slate-100 dark:bg-stone-900 flex items-center justify-center mx-auto mb-4">
        <svg className="w-8 h-8 text-slate-400 dark:text-stone-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 9l10.5-3m0 6.553v3.75a2.25 2.25 0 01-1.632 2.163l-1.32.377a1.803 1.803 0 11-.99-3.467l2.31-.66a2.25 2.25 0 001.632-2.163zm0 0V2.25L9 5.25v10.303m0 0v3.75a2.25 2.25 0 01-1.632 2.163l-1.32.377a1.803 1.803 0 01-.99-3.467l2.31-.66A2.25 2.25 0 009 15.553z" />
        </svg>
      </div>
      <p className="text-slate-500 dark:text-stone-400 font-medium">
        {hayFiltro ? 'No hay discos con ese criterio' : 'No hay discos en el catálogo'}
      </p>
      <p className="text-slate-400 dark:text-stone-600 text-sm mt-1">
        {hayFiltro ? 'Probá con otro filtro o búsqueda' : 'Agregá el primero usando el botón de arriba'}
      </p>
    </div>
  )
}

/* Panel flotante que aparece al hacer hover sobre una fila.
   Usa position:fixed para no ser afectado por overflow del contenedor.
   pointer-events:none porque es solo informativo, no interactivo. */
function HoverPanel({ disco, rect }) {
  if (!disco || !rect) return null

  // Si la fila está cerca del tope del viewport, mostrar el panel debajo
  const panelH = 280
  const showAbove = rect.top > panelH + 16
  const top    = showAbove ? rect.top - 8 : rect.bottom + 8
  const left   = Math.min(rect.left, window.innerWidth - 420)

  return (
    <div
      style={{
        position: 'fixed',
        top,
        left,
        zIndex: 9999,
        width: 400,
        transform: showAbove ? 'translateY(-100%)' : 'translateY(0)',
        animation: 'fadeScale 130ms ease forwards',
        pointerEvents: 'none',
      }}
      className="bg-white dark:bg-stone-900 border border-slate-200 dark:border-stone-700 rounded-xl shadow-2xl p-4"
    >
      <div className="flex gap-4">
        {/* Placeholder de portada */}
        <div className="w-20 h-20 flex-shrink-0 rounded-lg bg-slate-100 dark:bg-stone-800 flex items-center justify-center text-[10px] text-slate-400 dark:text-stone-600 text-center leading-tight px-1">
          Tapa próximamente
        </div>
        <div className="flex-1 min-w-0">
          <p className="font-bold text-slate-900 dark:text-white text-sm leading-tight">{disco.artista}</p>
          <p className="text-slate-500 dark:text-stone-400 text-xs mt-0.5 leading-tight">{disco.album}</p>
          <div className="mt-1.5"><EstadoBadge estado={disco.estado} /></div>
        </div>
      </div>

      {/* Campos del disco en grilla compacta */}
      <div className="mt-3 grid grid-cols-2 gap-x-4 gap-y-1.5 text-xs">
        {[
          ['Año',          disco.anio],
          ['Género',       disco.genero],
          ['Sello',        disco.selloDiscografico],
          ['Condición',    disco.condicion],
          ['Precio compra',  disco.precioCompra  ? `$${Number(disco.precioCompra).toLocaleString('es-AR')}` : null],
          ['Precio venta',   disco.precioVenta   ? `$${Number(disco.precioVenta).toLocaleString('es-AR')}`  : null],
        ].map(([label, value]) => (
          <div key={label}>
            <span className="text-slate-400 dark:text-stone-500">{label}: </span>
            <span className="text-slate-700 dark:text-stone-300">{value || '—'}</span>
          </div>
        ))}
      </div>

      {disco.observaciones && (
        <p className="mt-2 text-xs text-slate-500 dark:text-stone-400 italic border-t border-slate-100 dark:border-stone-800 pt-2 line-clamp-2">
          {disco.observaciones}
        </p>
      )}
    </div>
  )
}

/* Panel lateral derecho con el detalle completo del disco.
   Se abre al hacer clic en una fila. */
function SlideOver({ disco, onCerrar, onEditar }) {
  if (!disco) return null

  return (
    <>
      {/* Overlay oscuro */}
      <div className="fixed inset-0 bg-black/40 z-40" onClick={onCerrar} />

      {/* Panel */}
      <div className="fixed right-0 top-0 h-full w-full max-w-md bg-white dark:bg-stone-900 border-l border-slate-200 dark:border-stone-800 z-50 overflow-y-auto shadow-2xl flex flex-col">
        <div className="p-6 space-y-5 flex-1">

          {/* Header */}
          <div className="flex items-start justify-between gap-3">
            <div>
              <h2 className="text-lg font-bold text-slate-900 dark:text-white">{disco.artista}</h2>
              <p className="text-slate-500 dark:text-stone-400 text-sm">{disco.album}</p>
            </div>
            <button
              onClick={onCerrar}
              className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-stone-800 transition-colors flex-shrink-0"
            >
              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M6 18 18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          {/* Portada o imagen */}
          {disco.imagenUrl ? (
            <img
              src={disco.imagenUrl}
              alt={`${disco.artista} - ${disco.album}`}
              className="w-full aspect-square max-w-[200px] mx-auto rounded-xl object-cover bg-slate-100 dark:bg-stone-800"
            />
          ) : (
            <div className="w-full aspect-square max-w-[200px] mx-auto bg-slate-100 dark:bg-stone-800 rounded-xl flex items-center justify-center">
              <p className="text-slate-400 dark:text-stone-600 text-sm">Sin portada</p>
            </div>
          )}

          {/* Audio preview */}
          {disco.previewUrl && (
            <div>
              <p className="text-xs uppercase tracking-wider text-slate-400 dark:text-stone-500 mb-1">Preview</p>
              <audio controls src={disco.previewUrl} className="w-full h-9" />
            </div>
          )}

          {/* QR code */}
          {disco.codigoQr && (
            <div className="flex flex-col items-center gap-2">
              <p className="text-xs uppercase tracking-wider text-slate-400 dark:text-stone-500">Código QR</p>
              <div className="bg-white p-2 rounded-lg inline-block">
                <QRCode value={disco.codigoQr} size={120} />
              </div>
            </div>
          )}

          {/* Estado */}
          <EstadoBadge estado={disco.estado} />

          {/* Campos en grid */}
          <div className="grid grid-cols-2 gap-3">
            {[
              ['Año',           disco.anio],
              ['Género',        disco.genero],
              ['Sello',         disco.selloDiscografico],
              ['Condición',     disco.condicion],
              ['Precio compra', disco.precioCompra ? `$${Number(disco.precioCompra).toLocaleString('es-AR')}` : null],
              ['Precio venta',  disco.precioVenta  ? `$${Number(disco.precioVenta).toLocaleString('es-AR')}`  : null],
              ['Código interno', disco.codigoInterno],
            ].map(([label, value]) => (
              <div key={label} className="bg-slate-50 dark:bg-stone-950 border border-slate-100 dark:border-stone-800 rounded-lg px-3 py-2">
                <p className="text-xs uppercase tracking-wider text-slate-400 dark:text-stone-500 mb-0.5">{label}</p>
                <p className="text-sm font-medium text-slate-700 dark:text-stone-300">{value || '—'}</p>
              </div>
            ))}
          </div>

          {disco.observaciones && (
            <div>
              <p className="text-xs uppercase tracking-wider text-slate-400 dark:text-stone-500 mb-1">Observaciones</p>
              <p className="text-slate-600 dark:text-stone-400 text-sm italic">{disco.observaciones}</p>
            </div>
          )}
        </div>

        {/* Acciones fijas al fondo */}
        <div className="p-6 pt-0 space-y-2">
          <button
            onClick={() => { onEditar(disco); onCerrar() }}
            className="btn-primary w-full"
          >
            Editar disco
          </button>
          {/* Espacio para acciones futuras */}
        </div>
      </div>
    </>
  )
}

export default function DiscosCatalogo() {
  const [discos, setDiscos] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [busqueda, setBusqueda] = useState('')
  const [filtroEstado, setFiltroEstado] = useState('TODOS')
  const [discoForm, setDiscoForm] = useState(null)
  const [discoEliminar, setDiscoEliminar] = useState(null)
  const [eliminando, setEliminando] = useState(false)
  const [hoverDisco, setHoverDisco] = useState(null)
  const [hoverRect, setHoverRect] = useState(null)
  const [slideOverDisco, setSlideOverDisco] = useState(null)
  const [pagina, setPagina] = useState(1)
  const [porPagina, setPorPagina] = useState(20)
  const debounceRef = useRef(null)

  useEffect(() => { cargarTodos() }, [])

  async function cargarTodos() {
    setLoading(true)
    setError('')
    try {
      const data = await discoService.getAll()
      setDiscos(data)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  function onBusquedaChange(e) {
    const q = e.target.value
    setBusqueda(q)
    setPagina(1)
    clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(async () => {
      if (!q.trim()) { cargarTodos(); return }
      setLoading(true)
      try {
        const data = await discoService.buscar(q.trim())
        setDiscos(data)
      } catch (err) {
        setError(err.message)
      } finally {
        setLoading(false)
      }
    }, 300)
  }

  function cambiarFiltro(estado) {
    setFiltroEstado(estado)
    setPagina(1)
  }

  async function handleGuardar(payload) {
    if (discoForm && discoForm.idDisco) {
      const actualizado = await discoService.actualizar(discoForm.idDisco, payload)
      setDiscos(prev => prev.map(d => d.idDisco === discoForm.idDisco ? actualizado : d))
    } else {
      const nuevo = await discoService.crear(payload)
      setDiscos(prev => [nuevo, ...prev])
    }
    setDiscoForm(null)
  }

  async function handleEliminar() {
    setEliminando(true)
    try {
      await discoService.eliminar(discoEliminar.idDisco)
      setDiscos(prev => prev.filter(d => d.idDisco !== discoEliminar.idDisco))
      setDiscoEliminar(null)
    } catch (err) {
      alert(err.message)
    } finally {
      setEliminando(false)
    }
  }

  // Hover handlers: el panel se muestra mientras el mouse está sobre la fila
  function openHover(e, disco) {
    setHoverDisco(disco)
    setHoverRect(e.currentTarget.getBoundingClientRect())
  }

  function closeHover() {
    setHoverDisco(null)
    setHoverRect(null)
  }

  const discosFiltrados = discos.filter(d =>
    filtroEstado === 'TODOS' || d.estado === filtroEstado
  )
  const discosPagina = discosFiltrados.slice((pagina - 1) * porPagina, pagina * porPagina)
  const hayFiltro = filtroEstado !== 'TODOS' || busqueda.trim() !== ''

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 py-6 space-y-5">

      {/* Header */}
      <div className="flex items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-900 dark:text-white">Catálogo de discos</h1>
          <p className="text-slate-400 dark:text-stone-500 text-sm mt-0.5">
            {!loading && `${discosFiltrados.length} ${discosFiltrados.length === 1 ? 'disco' : 'discos'} mostrados`}
          </p>
        </div>
        <button
          onClick={() => setDiscoForm(false)}
          className="btn-primary flex items-center gap-2 whitespace-nowrap"
        >
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
          </svg>
          Agregar disco
        </button>
      </div>

      {/* Barra de búsqueda (ancho completo) + filtros de estado */}
      <div className="flex flex-col sm:flex-row gap-3">
        <div className="relative w-full">
          <svg className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 dark:text-stone-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="m21 21-5.197-5.197m0 0A7.5 7.5 0 1 0 5.196 5.196a7.5 7.5 0 0 0 10.607 10.607Z" />
          </svg>
          <input
            value={busqueda}
            onChange={onBusquedaChange}
            placeholder="Buscar por disco, artista, género, año, sello, estado, código..."
            className="input pl-9"
          />
        </div>
        <div className="flex gap-2 flex-wrap sm:flex-nowrap overflow-x-auto">
          {FILTROS.map(estado => (
            <button
              key={estado}
              onClick={() => cambiarFiltro(estado)}
              className={`text-xs px-3 py-2 rounded-lg font-medium transition-colors whitespace-nowrap ${
                filtroEstado === estado
                  ? 'bg-[#7E9FA8] text-white'
                  : 'bg-slate-100 dark:bg-stone-900 text-slate-600 dark:text-stone-400 hover:bg-slate-200 dark:hover:bg-stone-700'
              }`}
            >
              {estado === 'TODOS' ? 'Todos' : ESTADO_LABELS[estado]}
              {estado !== 'TODOS' && (
                <span className="ml-1.5 opacity-70">
                  {discos.filter(d => d.estado === estado).length}
                </span>
              )}
            </button>
          ))}
        </div>
      </div>

      {/* Error */}
      {error && (
        <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 text-red-600 dark:text-red-300 text-sm rounded-xl px-4 py-3">
          {error}
          <button onClick={cargarTodos} className="ml-3 underline hover:no-underline">Reintentar</button>
        </div>
      )}

      {/* Tabla principal */}
      <div className="card overflow-hidden">
        {loading ? (
          <Spinner />
        ) : discosFiltrados.length === 0 ? (
          <EmptyState hayFiltro={hayFiltro} />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-slate-100 dark:border-stone-800">
                    <th className="text-left px-5 py-3 text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider">Artista / Álbum</th>
                    <th className="text-left px-5 py-3 text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider hidden sm:table-cell">Condición</th>
                    <th className="text-left px-5 py-3 text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider">Precio</th>
                    <th className="text-left px-5 py-3 text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider">Estado</th>
                    <th className="px-5 py-3 text-right text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider">Acciones</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-stone-800/60">
                  {discosPagina.map(d => (
                    <tr
                      key={d.idDisco}
                      onMouseEnter={e => openHover(e, d)}
                      onMouseLeave={closeHover}
                      onClick={() => setSlideOverDisco(d)}
                      className="hover:bg-slate-50 dark:hover:bg-stone-900/40 transition-colors cursor-pointer"
                    >
                      <td className="px-5 py-4">
                        <div className="font-semibold text-slate-900 dark:text-white">{d.artista}</div>
                        <div className="text-slate-500 dark:text-stone-400 text-xs mt-0.5">
                          {d.album}
                          {d.anio ? <span className="ml-1.5 text-slate-400 dark:text-stone-600">· {d.anio}</span> : null}
                        </div>
                      </td>
                      <td className="px-5 py-4 text-slate-600 dark:text-stone-400 hidden sm:table-cell">
                        {d.condicion || <span className="text-slate-300 dark:text-stone-600">—</span>}
                      </td>
                      <td className="px-5 py-4 font-semibold text-slate-900 dark:text-white tabular-nums">
                        {d.precioVenta
                          ? `$${Number(d.precioVenta).toLocaleString('es-AR')}`
                          : <span className="text-slate-400 dark:text-stone-600 font-normal">—</span>}
                      </td>
                      <td className="px-5 py-4" onClick={e => e.stopPropagation()}>
                        <select
                          value={d.estado}
                          onChange={async (e) => {
                            const nuevoEstado = e.target.value
                            try {
                              const actualizado = await discoService.cambiarEstado(d.idDisco, nuevoEstado)
                              setDiscos(prev => prev.map(x => x.idDisco === d.idDisco ? actualizado : x))
                            } catch (err) {
                              alert('Error al cambiar estado: ' + err.message)
                            }
                          }}
                          className="text-xs rounded-lg border border-slate-200 dark:border-stone-700 bg-white dark:bg-stone-900 text-slate-700 dark:text-stone-300 px-2 py-1 cursor-pointer"
                        >
                          <option value="DISPONIBLE">Disponible</option>
                          <option value="RESERVADO">Reservado</option>
                          <option value="SIN_STOCK">Sin stock</option>
                          <option value="VENDIDO">Vendido</option>
                        </select>
                      </td>
                      {/* stopPropagation para que los botones no abran el slide-over */}
                      <td className="px-5 py-4 text-right" onClick={e => e.stopPropagation()}>
                        <div className="flex items-center justify-end gap-2">
                          <div className="flex items-center gap-1 text-xs">
                            <button
                              onClick={async () => {
                                const nuevaCantidad = Math.max(0, (d.cantidadCopias ?? 1) - 1)
                                try {
                                  const actualizado = await discoService.actualizarCopias(d.idDisco, nuevaCantidad)
                                  setDiscos(prev => prev.map(x => x.idDisco === d.idDisco ? actualizado : x))
                                } catch (err) { alert(err.message) }
                              }}
                              className="w-6 h-6 rounded bg-slate-100 dark:bg-stone-800 hover:bg-slate-200 dark:hover:bg-stone-700 text-slate-600 dark:text-stone-400 flex items-center justify-center font-bold transition-colors"
                            >−</button>
                            <span className="w-6 text-center font-mono text-slate-700 dark:text-stone-300">
                              {d.cantidadCopias ?? 1}
                            </span>
                            <button
                              onClick={async () => {
                                const nuevaCantidad = (d.cantidadCopias ?? 1) + 1
                                try {
                                  const actualizado = await discoService.actualizarCopias(d.idDisco, nuevaCantidad)
                                  setDiscos(prev => prev.map(x => x.idDisco === d.idDisco ? actualizado : x))
                                } catch (err) { alert(err.message) }
                              }}
                              className="w-6 h-6 rounded bg-slate-100 dark:bg-stone-800 hover:bg-slate-200 dark:hover:bg-stone-700 text-slate-600 dark:text-stone-400 flex items-center justify-center font-bold transition-colors"
                            >+</button>
                          </div>
                          <button
                            onClick={() => setDiscoForm(d)}
                            className="text-xs bg-slate-100 dark:bg-stone-800 hover:bg-slate-200 dark:hover:bg-stone-700 text-slate-600 dark:text-stone-400 px-2.5 py-1.5 rounded-lg transition-colors font-medium"
                          >
                            Editar
                          </button>
                          <button
                            onClick={() => setDiscoEliminar(d)}
                            className="text-xs bg-red-50 dark:bg-red-900/20 hover:bg-red-100 dark:hover:bg-red-900/40 text-red-600 dark:text-red-400 px-2.5 py-1.5 rounded-lg transition-colors font-medium"
                          >
                            Dar de baja
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Paginación */}
            <div className="px-5 py-3 border-t border-slate-100 dark:border-stone-800">
              <Paginacion
                total={discosFiltrados.length}
                porPagina={porPagina}
                pagina={pagina}
                onPagina={setPagina}
                onPorPagina={n => { setPorPagina(n); setPagina(1) }}
              />
            </div>
          </>
        )}
      </div>

      {/* Panel hover flotante (position:fixed, pointer-events:none) */}
      <HoverPanel disco={hoverDisco} rect={hoverRect} />

      {/* Slide-over de detalle al hacer clic en una fila */}
      <SlideOver
        disco={slideOverDisco}
        onCerrar={() => setSlideOverDisco(null)}
        onEditar={d => { setDiscoForm(d); setSlideOverDisco(null) }}
      />

      {/* Formulario de edición / creación */}
      {discoForm !== null && (
        <DiscoForm
          disco={discoForm || null}
          onGuardar={handleGuardar}
          onCancelar={() => setDiscoForm(null)}
        />
      )}

      {/* Confirmación de baja */}
      {discoEliminar && (
        <ConfirmModal
          titulo="Dar de baja disco"
          mensaje={`¿Seguro que querés dar de baja "${discoEliminar.artista} – ${discoEliminar.album}"? El disco pasará a estado SIN_STOCK.`}
          onConfirmar={handleEliminar}
          onCancelar={() => setDiscoEliminar(null)}
          cargando={eliminando}
        />
      )}
    </div>
  )
}

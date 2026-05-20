import { useEffect, useRef, useState } from 'react'
import { discoService } from '../services/discoService'
import DiscoCard from '../components/DiscoCard'
import DiscoForm from '../components/DiscoForm'
import ConfirmModal from '../components/ConfirmModal'

const FILTROS = ['TODOS', 'DISPONIBLE', 'RESERVADO', 'VENDIDO', 'DESCONTINUADO']

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

export default function DiscosCatalogo() {
  const [discos, setDiscos] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [busqueda, setBusqueda] = useState('')
  const [filtroEstado, setFiltroEstado] = useState('TODOS')
  const [discoForm, setDiscoForm] = useState(null)   // null=cerrado, false=nuevo, objeto=editar
  const [discoEliminar, setDiscoEliminar] = useState(null)
  const [eliminando, setEliminando] = useState(false)
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

  async function handleCambiarEstado(id, nuevoEstado) {
    try {
      const actualizado = await discoService.cambiarEstado(id, nuevoEstado)
      setDiscos(prev => prev.map(d => d.idDisco === id ? actualizado : d))
    } catch (err) {
      alert(err.message)
    }
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

  const discosFiltrados = discos.filter(d =>
    filtroEstado === 'TODOS' || d.estado === filtroEstado
  )

  const hayFiltroActivo = filtroEstado !== 'TODOS' || busqueda.trim() !== ''

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

      {/* Barra búsqueda + filtros */}
      <div className="flex flex-col sm:flex-row gap-3">
        <div className="relative flex-1">
          <svg className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 dark:text-stone-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="m21 21-5.197-5.197m0 0A7.5 7.5 0 1 0 5.196 5.196a7.5 7.5 0 0 0 10.607 10.607Z" />
          </svg>
          <input
            value={busqueda}
            onChange={onBusquedaChange}
            placeholder="Buscar por artista o álbum..."
            className="input pl-9"
          />
        </div>

        <div className="flex gap-2 flex-wrap sm:flex-nowrap overflow-x-auto">
          {FILTROS.map(estado => (
            <button
              key={estado}
              onClick={() => setFiltroEstado(estado)}
              className={`text-xs px-3 py-2 rounded-lg font-medium transition-colors whitespace-nowrap ${
                filtroEstado === estado
                  ? 'bg-[#7E9FA8] text-white'
                  : 'bg-slate-100 dark:bg-stone-900 text-slate-600 dark:text-stone-400 hover:bg-slate-200 dark:hover:bg-stone-700'
              }`}
            >
              {estado === 'TODOS' ? 'Todos' : estado.charAt(0) + estado.slice(1).toLowerCase()}
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

      {/* Grid de cards */}
      {loading ? (
        <Spinner />
      ) : discosFiltrados.length === 0 ? (
        <EmptyState hayFiltro={hayFiltroActivo} />
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {discosFiltrados.map(d => (
            <DiscoCard
              key={d.idDisco}
              disco={d}
              onEditar={setDiscoForm}
              onCambiarEstado={handleCambiarEstado}
              onEliminar={setDiscoEliminar}
            />
          ))}
        </div>
      )}

      {/* Modales */}
      {discoForm !== null && (
        <DiscoForm
          disco={discoForm || null}
          onGuardar={handleGuardar}
          onCancelar={() => setDiscoForm(null)}
        />
      )}

      {discoEliminar && (
        <ConfirmModal
          titulo="Eliminar disco"
          mensaje={`¿Seguro que querés dar de baja "${discoEliminar.artista} – ${discoEliminar.album}"? El disco pasará a estado DESCONTINUADO.`}
          onConfirmar={handleEliminar}
          onCancelar={() => setDiscoEliminar(null)}
          cargando={eliminando}
        />
      )}
    </div>
  )
}

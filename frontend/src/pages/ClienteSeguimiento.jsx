import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, resolveApiUrl } from '../api/sonograma'

const INTEREST_TYPES = [
  ['LIBRE', 'Búsqueda libre'], ['ARTISTA', 'Artista'], ['ALBUM', 'Álbum'],
  ['GENERO', 'Género'], ['ESTILO', 'Estilo'], ['SELLO', 'Sello'],
  ['PERIODO', 'Período'], ['FORMATO', 'Formato'], ['CONDICION', 'Condición'], ['PAIS', 'País'],
]

const TASTE_SECTIONS = [
  ['artistas', 'Artistas'], ['generos', 'Géneros'], ['estilos', 'Estilos'],
  ['sellos', 'Sellos'], ['decadas', 'Períodos'], ['anios', 'Años'],
  ['formatos', 'Formatos'], ['condiciones', 'Condición'],
]

function money(value) {
  if (value == null) return '—'
  return `UYU $${Number(value).toLocaleString('es-UY', { maximumFractionDigits: 0 })}`
}

function date(value) {
  if (!value) return '—'
  return new Date(value).toLocaleDateString('es-UY', { day: '2-digit', month: '2-digit', year: 'numeric' })
}

function contactValue(value) {
  return value?.trim?.() || '—'
}

function LoadingBlock({ label = 'Cargando…' }) {
  return (
    <div className="card flex items-center justify-center gap-3 py-16 text-sm text-slate-400 dark:text-stone-500">
      <span className="h-4 w-4 animate-spin rounded-full border-2 border-[#7E9FA8] border-t-transparent" />
      {label}
    </div>
  )
}

function ErrorBlock({ message, onRetry }) {
  return (
    <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-800 dark:bg-red-900/20 dark:text-red-300">
      {message}
      {onRetry ? <button type="button" onClick={onRetry} className="ml-3 underline">Reintentar</button> : null}
    </div>
  )
}

function MetricCard({ label, value, detail }) {
  return (
    <div className="card p-4">
      <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-400 dark:text-stone-500">{label}</p>
      <p className="mt-1 text-lg font-bold tabular-nums text-slate-900 dark:text-white">{value}</p>
      {detail ? <p className="mt-0.5 text-xs text-slate-400 dark:text-stone-500">{detail}</p> : null}
    </div>
  )
}

function TasteSection({ title, items }) {
  if (!items?.length) return null
  const max = Math.max(...items.map(item => Number(item.porcentaje || 0)), 1)
  return (
    <section className="card p-4">
      <h3 className="text-xs font-bold uppercase tracking-wider text-slate-500 dark:text-stone-400">{title}</h3>
      <div className="mt-3 space-y-2.5">
        {items.slice(0, 8).map(item => (
          <div key={item.valor}>
            <div className="mb-1 flex items-center justify-between gap-3 text-sm">
              <span className="truncate font-medium text-slate-700 dark:text-stone-200" title={item.valor}>{item.valor}</span>
              <span className="whitespace-nowrap text-xs tabular-nums text-slate-400 dark:text-stone-500">
                {item.cantidad} · {Number(item.porcentaje).toLocaleString('es-UY', { maximumFractionDigits: 1 })}%
              </span>
            </div>
            <div className="h-1.5 overflow-hidden rounded-full bg-slate-100 dark:bg-stone-800">
              <div className="h-full rounded-full bg-[#7E9FA8]" style={{ width: `${Math.max(4, Number(item.porcentaje) / max * 100)}%` }} />
            </div>
          </div>
        ))}
      </div>
    </section>
  )
}

function AffinityBadge({ level }) {
  const styles = {
    ALTA: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-900/20 dark:text-emerald-400',
    MEDIA: 'bg-amber-50 text-amber-700 dark:bg-amber-900/20 dark:text-amber-400',
    BAJA: 'bg-slate-100 text-slate-600 dark:bg-stone-800 dark:text-stone-400',
  }
  const labels = { ALTA: 'Afinidad alta', MEDIA: 'Afinidad media', BAJA: 'Afinidad baja' }
  return <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${styles[level] || styles.BAJA}`}>{labels[level] || level}</span>
}

function Interests({ clienteId, items, loading, error, onChanged }) {
  const [type, setType] = useState('LIBRE')
  const [text, setText] = useState('')
  const [saving, setSaving] = useState(false)
  const [actionError, setActionError] = useState('')

  async function add(event) {
    event.preventDefault()
    if (!text.trim()) return
    setSaving(true)
    setActionError('')
    try {
      await api.crm.crearInteres(clienteId, { tipo: type, texto: text.trim() })
      setText('')
      await onChanged()
    } catch (err) {
      setActionError(err.message || 'No se pudo guardar el interés')
    } finally {
      setSaving(false)
    }
  }

  async function toggle(item) {
    setActionError('')
    try {
      await api.crm.cambiarEstadoInteres(clienteId, item.idInteres, !item.activo)
      await onChanged()
    } catch (err) {
      setActionError(err.message || 'No se pudo actualizar el interés')
    }
  }

  return (
    <section className="card p-5">
      <div>
        <h2 className="font-bold text-slate-900 dark:text-white">Intereses expresados</h2>
        <p className="mt-0.5 text-xs text-slate-400 dark:text-stone-500">Preferencias que el cliente comentó directamente.</p>
      </div>
      <form onSubmit={add} className="mt-4 grid gap-2 sm:grid-cols-[150px_minmax(0,1fr)_auto]">
        <select className="input" value={type} onChange={event => setType(event.target.value)} aria-label="Tipo de interés">
          {INTEREST_TYPES.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
        <input className="input" value={text} onChange={event => setText(event.target.value)} maxLength={500}
          placeholder="Ej: Detroit techno de los 90" aria-label="Interés del cliente" />
        <button type="submit" className="btn-primary" disabled={saving || !text.trim()}>{saving ? 'Guardando…' : 'Agregar'}</button>
      </form>
      {actionError ? <div className="mt-3"><ErrorBlock message={actionError} /></div> : null}
      {loading ? <p className="mt-4 text-sm text-slate-400">Cargando intereses…</p> : null}
      {error ? <div className="mt-4"><ErrorBlock message={error} /></div> : null}
      {!loading && !error ? (
        <div className="mt-4 space-y-2">
          {items.length === 0 ? <p className="text-sm text-slate-400 dark:text-stone-500">Todavía no hay intereses registrados.</p> : null}
          {items.map(item => (
            <div key={item.idInteres} className={`flex items-center justify-between gap-3 rounded-lg border px-3 py-2 ${item.activo ? 'border-slate-100 dark:border-stone-800' : 'border-slate-100 bg-slate-50 opacity-60 dark:border-stone-800 dark:bg-stone-950'}`}>
              <div className="min-w-0">
                <span className="mr-2 text-[10px] font-semibold uppercase tracking-wide text-[#5C7D87] dark:text-[#7E9FA8]">{item.tipo}</span>
                <span className="text-sm text-slate-700 dark:text-stone-300">{item.texto}</span>
              </div>
              <button type="button" onClick={() => toggle(item)} className="text-xs font-medium text-[#5C7D87] hover:underline dark:text-[#7E9FA8]">
                {item.activo ? 'Desactivar' : 'Reactivar'}
              </button>
            </div>
          ))}
        </div>
      ) : null}
    </section>
  )
}

function Recommendations({ items, loading, error, onRetry }) {
  return (
    <section>
      <div className="mb-3 flex items-end justify-between gap-4">
        <div>
          <h2 className="font-bold text-slate-900 dark:text-white">Discos para recomendar</h2>
          <p className="mt-0.5 text-xs text-slate-400 dark:text-stone-500">Stock disponible ordenado por afinidad explicable.</p>
        </div>
      </div>
      {loading ? <LoadingBlock label="Calculando recomendaciones…" /> : null}
      {error ? <ErrorBlock message={error} onRetry={onRetry} /> : null}
      {!loading && !error && items.length === 0 ? (
        <div className="card py-12 text-center text-sm text-slate-400 dark:text-stone-500">No hay coincidencias musicales suficientes con el stock disponible.</div>
      ) : null}
      {!loading && !error ? (
        <div className="grid gap-4 lg:grid-cols-2">
          {items.map(item => (
            <article key={item.idDisco} className="card flex gap-4 p-4">
              {item.imagenUrl ? (
                <img src={resolveApiUrl(item.imagenUrl)} alt={`${item.artista} - ${item.album}`} className="h-24 w-24 flex-shrink-0 rounded-lg object-cover bg-slate-100 dark:bg-stone-800" />
              ) : <div className="h-24 w-24 flex-shrink-0 rounded-lg bg-slate-100 dark:bg-stone-800" />}
              <div className="min-w-0 flex-1">
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <h3 className="truncate font-bold text-slate-900 dark:text-white">{item.artista}</h3>
                    <p className="truncate text-sm text-slate-600 dark:text-stone-300">{item.album}</p>
                  </div>
                  <AffinityBadge level={item.nivelAfinidad} />
                </div>
                <p className="mt-1 text-xs text-slate-400 dark:text-stone-500">
                  {[item.selloDiscografico, item.anio, item.formato].filter(Boolean).join(' · ') || 'Sin metadatos adicionales'}
                </p>
                <div className="mt-2 flex items-center justify-between text-sm">
                  <span className="font-bold tabular-nums text-slate-900 dark:text-white">{money(item.precio)}</span>
                  <span className="text-xs text-slate-400">{item.cantidadDisponible} disponible{item.cantidadDisponible === 1 ? '' : 's'}</span>
                </div>
                <ul className="mt-2 space-y-1 text-xs text-slate-500 dark:text-stone-400">
                  {item.razones.map(reason => <li key={reason}>• {reason}</li>)}
                </ul>
              </div>
            </article>
          ))}
        </div>
      ) : null}
    </section>
  )
}

export default function ClienteSeguimiento() {
  const { id } = useParams()
  const [profile, setProfile] = useState(null)
  const [profileLoading, setProfileLoading] = useState(true)
  const [profileError, setProfileError] = useState('')
  const [interests, setInterests] = useState([])
  const [interestsLoading, setInterestsLoading] = useState(true)
  const [interestsError, setInterestsError] = useState('')
  const [recommendations, setRecommendations] = useState([])
  const [recommendationsLoading, setRecommendationsLoading] = useState(true)
  const [recommendationsError, setRecommendationsError] = useState('')
  const [tastePeriod, setTastePeriod] = useState('historico')

  const loadProfile = useCallback(async () => {
    setProfileLoading(true)
    setProfileError('')
    try { setProfile(await api.crm.perfil(id)) } catch (err) { setProfileError(err.message || 'No se pudo cargar el perfil') }
    finally { setProfileLoading(false) }
  }, [id])

  const loadInterests = useCallback(async () => {
    setInterestsLoading(true)
    setInterestsError('')
    try { setInterests(await api.crm.intereses(id)) } catch (err) { setInterestsError(err.message || 'No se pudieron cargar los intereses') }
    finally { setInterestsLoading(false) }
  }, [id])

  const loadRecommendations = useCallback(async () => {
    setRecommendationsLoading(true)
    setRecommendationsError('')
    try { setRecommendations(await api.crm.recomendaciones(id)) } catch (err) { setRecommendationsError(err.message || 'No se pudieron calcular las recomendaciones') }
    finally { setRecommendationsLoading(false) }
  }, [id])

  useEffect(() => {
    let cancelled = false
    Promise.allSettled([api.crm.perfil(id), api.crm.intereses(id), api.crm.recomendaciones(id)])
      .then(([profileResult, interestsResult, recommendationsResult]) => {
        if (cancelled) return
        if (profileResult.status === 'fulfilled') setProfile(profileResult.value)
        else setProfileError(profileResult.reason?.message || 'No se pudo cargar el perfil')
        if (interestsResult.status === 'fulfilled') setInterests(interestsResult.value)
        else setInterestsError(interestsResult.reason?.message || 'No se pudieron cargar los intereses')
        if (recommendationsResult.status === 'fulfilled') setRecommendations(recommendationsResult.value)
        else setRecommendationsError(recommendationsResult.reason?.message || 'No se pudieron calcular las recomendaciones')
      })
      .finally(() => {
        if (cancelled) return
        setProfileLoading(false)
        setInterestsLoading(false)
        setRecommendationsLoading(false)
      })
    return () => { cancelled = true }
  }, [id])

  async function refreshInterestDrivenData() {
    await Promise.all([loadInterests(), loadRecommendations()])
  }

  if (profileLoading) return <div className="mx-auto max-w-6xl px-4 py-8 sm:px-6"><LoadingBlock label="Armando el perfil de compra…" /></div>
  if (profileError || !profile) return <div className="mx-auto max-w-6xl px-4 py-8 sm:px-6"><ErrorBlock message={profileError || 'Perfil no disponible'} onRetry={loadProfile} /></div>

  const { cliente, metricas } = profile
  const taste = tastePeriod === 'historico' ? profile.perfilHistorico : profile.perfilReciente
  const typicalRange = metricas.rangoTipicoMinimo == null ? '—' : `${money(metricas.rangoTipicoMinimo)} – ${money(metricas.rangoTipicoMaximo)}`

  return (
    <main className="mx-auto max-w-6xl space-y-6 px-4 py-6 sm:px-6">
      <header className="card p-5 sm:p-6">
        <Link to="/clientes" className="text-xs font-medium text-[#5C7D87] hover:underline dark:text-[#7E9FA8]">← Volver a Clientes</Link>
        <div className="mt-3 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <p className="text-[10px] font-semibold uppercase tracking-[0.18em] text-slate-400 dark:text-stone-500">Seguimiento musical</p>
            <h1 className="mt-1 text-2xl font-bold text-slate-900 dark:text-white">{cliente.nombre} {cliente.apellido}</h1>
          </div>
          <div className="grid grid-cols-1 gap-x-6 gap-y-1 text-sm text-slate-500 dark:text-stone-400 sm:grid-cols-2">
            <span>Instagram: {contactValue(cliente.instagramUsuario)}</span>
            <span>Teléfono: {contactValue(cliente.telefono)}</span>
            <span className="sm:col-span-2">Email: {contactValue(cliente.email)}</span>
          </div>
        </div>
      </header>

      <section className="grid grid-cols-2 gap-3 md:grid-cols-4">
        <MetricCard label="Discos comprados" value={metricas.cantidadDiscos} detail={`${metricas.cantidadCompras} compras`} />
        <MetricCard label="Gastado en discos" value={money(metricas.totalGastado)} detail={`Ticket medio ${money(metricas.promedioPorCompra)}`} />
        <MetricCard label="Promedio por disco" value={money(metricas.precioPromedioPorDisco)} detail={`Mediana ${money(metricas.precioMedianoPorDisco)}`} />
        <MetricCard label="Rango habitual" value={typicalRange} detail={`Máximo ${money(metricas.precioMaximoPorDisco)}`} />
        <MetricCard label="Primera compra" value={date(metricas.primeraCompra)} />
        <MetricCard label="Última compra" value={date(metricas.ultimaCompra)} />
        <MetricCard label="Frecuencia" value={metricas.frecuenciaPromedioDias == null ? '—' : `Cada ${Number(metricas.frecuenciaPromedioDias).toLocaleString('es-UY', { maximumFractionDigits: 1 })} días`} />
        <MetricCard label="Últimos 12 meses" value={`${metricas.comprasUltimos12Meses} compras`} />
      </section>

      <section>
        <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 className="font-bold text-slate-900 dark:text-white">Perfil de gusto</h2>
            <p className="mt-0.5 text-xs text-slate-400 dark:text-stone-500">Preferencias calculadas desde el historial de ventas registrado.</p>
          </div>
          <div className="flex rounded-lg bg-slate-100 p-1 dark:bg-stone-900">
            {[['historico', 'Histórico'], ['reciente', 'Últimos 12 meses']].map(([value, label]) => (
              <button key={value} type="button" onClick={() => setTastePeriod(value)}
                className={`rounded-md px-3 py-1.5 text-xs font-medium ${tastePeriod === value ? 'bg-white text-slate-900 shadow-sm dark:bg-stone-800 dark:text-white' : 'text-slate-500 dark:text-stone-400'}`}>
                {label}
              </button>
            ))}
          </div>
        </div>
        {TASTE_SECTIONS.every(([key]) => !taste?.[key]?.length) ? (
          <div className="card py-12 text-center text-sm text-slate-400 dark:text-stone-500">No hay metadatos suficientes para este período.</div>
        ) : (
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {TASTE_SECTIONS.map(([key, title]) => <TasteSection key={key} title={title} items={taste?.[key]} />)}
          </div>
        )}
      </section>

      <Interests clienteId={id} items={interests} loading={interestsLoading} error={interestsError} onChanged={refreshInterestDrivenData} />
      <Recommendations items={recommendations} loading={recommendationsLoading} error={recommendationsError} onRetry={loadRecommendations} />

      <section className="card p-5">
        <h2 className="font-bold text-slate-900 dark:text-white">Historial de discos</h2>
        <div className="mt-4 divide-y divide-slate-100 dark:divide-stone-800">
          {profile.historialCompras.length === 0 ? <p className="py-8 text-center text-sm text-slate-400">Sin compras completadas.</p> : null}
          {profile.historialCompras.map((item, index) => (
            <div key={item.idDetalle || `${item.idVenta}-${index}`} className="flex items-center gap-3 py-3">
              {item.imagenUrl ? <img src={resolveApiUrl(item.imagenUrl)} alt="" className="h-12 w-12 rounded-lg object-cover bg-slate-100 dark:bg-stone-800" />
                : <div className="h-12 w-12 rounded-lg bg-slate-100 dark:bg-stone-800" />}
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-semibold text-slate-800 dark:text-stone-200">{item.artista || 'Ítem manual'} — {item.album || 'Sin título'}</p>
                <p className="text-xs text-slate-400 dark:text-stone-500">{date(item.fechaCompra)} · {item.cantidad} unidad{item.cantidad === 1 ? '' : 'es'}</p>
              </div>
              <div className="text-right">
                <p className="text-sm font-bold tabular-nums text-slate-900 dark:text-white">{money(item.precioUnitarioPagado)}</p>
                <p className="text-[10px] uppercase tracking-wide text-slate-400">por disco</p>
              </div>
            </div>
          ))}
        </div>
      </section>
    </main>
  )
}

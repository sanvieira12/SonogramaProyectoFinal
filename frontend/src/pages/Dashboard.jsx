import { useEffect, useState } from 'react'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  PieChart, Pie, Cell, Legend
} from 'recharts'
import { api } from '../api/sonograma'
import AddDiscoModal from '../components/AddDiscoModal'
import { useTheme } from '../context/ThemeContext'

const ESTADO_STYLE = {
  DISPONIBLE: { bg: 'bg-emerald-100 dark:bg-emerald-900/30', text: 'text-emerald-700 dark:text-emerald-400', dot: 'bg-emerald-500' },
  RESERVADO:  { bg: 'bg-amber-100 dark:bg-amber-900/30',   text: 'text-amber-700 dark:text-amber-400',   dot: 'bg-amber-500' },
  VENDIDO:    { bg: 'bg-blue-100 dark:bg-blue-900/30',     text: 'text-blue-700 dark:text-blue-400',     dot: 'bg-blue-500' },
  DESCONTINUADO: { bg: 'bg-red-100 dark:bg-red-900/30',   text: 'text-red-700 dark:text-red-400',       dot: 'bg-red-500' },
}

const CONDICION_STYLE = {
  NUEVO:       'text-emerald-600 dark:text-emerald-400 font-semibold',
  USADO:       'text-blue-600 dark:text-blue-400',
  CONSIGNACION:'text-orange-600 dark:text-orange-400',
  CATALOGO:    'text-purple-600 dark:text-purple-400',
}

const ESTADO_COLORS = {
  DISPONIBLE: '#10b981',
  RESERVADO: '#f59e0b',
  VENDIDO: '#3b82f6',
  DESCONTINUADO: '#ef4444',
}

const CONDICION_COLORS = ['#10b981', '#3b82f6', '#f97316', '#8b5cf6']

function StatCard({ label, value, sublabel, color, icon }) {
  return (
    <div className="card p-5 flex items-start gap-4">
      <div className={`w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0 ${color}`}>
        {icon}
      </div>
      <div>
        <div className="text-2xl font-bold text-slate-900 dark:text-white tabular-nums">{value}</div>
        <div className="text-slate-600 dark:text-gray-400 text-sm font-medium mt-0.5">{label}</div>
        {sublabel && <div className="text-slate-400 dark:text-gray-600 text-xs mt-0.5">{sublabel}</div>}
      </div>
    </div>
  )
}

function CustomBarTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null
  return (
    <div className="bg-white dark:bg-gray-800 border border-slate-200 dark:border-gray-700 rounded-lg px-3 py-2 shadow-lg text-sm">
      <div className="font-medium text-slate-700 dark:text-gray-300">{label}</div>
      <div className="text-indigo-600 dark:text-indigo-400 font-bold">{payload[0].value} discos</div>
    </div>
  )
}

function CustomPieTooltip({ active, payload }) {
  if (!active || !payload?.length) return null
  return (
    <div className="bg-white dark:bg-gray-800 border border-slate-200 dark:border-gray-700 rounded-lg px-3 py-2 shadow-lg text-sm">
      <div className="font-medium text-slate-700 dark:text-gray-300">{payload[0].name}</div>
      <div className="font-bold" style={{ color: payload[0].payload.fill }}>{payload[0].value} discos</div>
    </div>
  )
}

function EmptyState() {
  return (
    <div className="text-center py-16">
      <div className="w-16 h-16 rounded-2xl bg-slate-100 dark:bg-gray-800 flex items-center justify-center mx-auto mb-4">
        <svg className="w-8 h-8 text-slate-400 dark:text-gray-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M20.25 7.5l-.625 10.632a2.25 2.25 0 01-2.247 2.118H6.622a2.25 2.25 0 01-2.247-2.118L3.75 7.5M10 11.25h4M3.375 7.5h17.25c.621 0 1.125-.504 1.125-1.125v-1.5c0-.621-.504-1.125-1.125-1.125H3.375c-.621 0-1.125.504-1.125 1.125v1.5c0 .621.504 1.125 1.125 1.125z" />
        </svg>
      </div>
      <p className="text-slate-500 dark:text-gray-400 font-medium">No hay discos en el inventario</p>
      <p className="text-slate-400 dark:text-gray-600 text-sm mt-1">Usá el botón "Agregar disco" para comenzar</p>
    </div>
  )
}

const ESTADOS_FILTRO = ['TODOS', 'DISPONIBLE', 'RESERVADO', 'VENDIDO', 'DESCONTINUADO']

export default function Dashboard() {
  const { dark } = useTheme()
  const [discos, setDiscos] = useState([])
  const [loading, setLoading] = useState(true)
  const [busqueda, setBusqueda] = useState('')
  const [filtroEstado, setFiltroEstado] = useState('TODOS')
  const [mostrarModal, setMostrarModal] = useState(false)
  const [qrDisco, setQrDisco] = useState(null)

  useEffect(() => { cargarDiscos() }, [])

  async function cargarDiscos() {
    setLoading(true)
    try {
      const data = await api.discos.todos()
      setDiscos(data)
    } catch {
      setDiscos([])
    } finally {
      setLoading(false)
    }
  }

  async function buscar(e) {
    const q = e.target.value
    setBusqueda(q)
    if (q.length === 0) { cargarDiscos(); return }
    if (q.length < 2) return
    try {
      const data = await api.discos.buscar(q)
      setDiscos(data)
    } catch { /* ignore */ }
  }

  async function cambiarEstado(id, estado) {
    try {
      const actualizado = await api.discos.cambiarEstado(id, estado)
      setDiscos(prev => prev.map(d => d.idDisco === id ? actualizado : d))
    } catch (err) {
      alert(err.message)
    }
  }

  const stats = {
    total: discos.length,
    disponibles: discos.filter(d => d.estado === 'DISPONIBLE').length,
    reservados: discos.filter(d => d.estado === 'RESERVADO').length,
    vendidos: discos.filter(d => d.estado === 'VENDIDO').length,
  }

  const barData = Object.entries(ESTADO_COLORS).map(([estado, fill]) => ({
    estado: estado.charAt(0) + estado.slice(1).toLowerCase(),
    cantidad: discos.filter(d => d.estado === estado).length,
    fill,
  })).filter(d => d.cantidad > 0)

  const condiciones = ['NUEVO', 'USADO', 'CONSIGNACION', 'CATALOGO']
  const pieData = condiciones.map((c, i) => ({
    name: c.charAt(0) + c.slice(1).toLowerCase(),
    value: discos.filter(d => d.condicion === c).length,
    fill: CONDICION_COLORS[i],
  })).filter(d => d.value > 0)

  const valorTotal = discos
    .filter(d => d.estado === 'DISPONIBLE' && d.precioVenta)
    .reduce((sum, d) => sum + Number(d.precioVenta), 0)

  const discosFiltrados = discos.filter(d => {
    if (filtroEstado !== 'TODOS' && d.estado !== filtroEstado) return false
    return true
  })

  const axisColor = dark ? '#6b7280' : '#94a3b8'
  const gridColor = dark ? '#1f2937' : '#f1f5f9'

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 py-6 space-y-6">

      {/* Stats */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <StatCard
          label="Total inventario"
          value={stats.total}
          sublabel={`$${valorTotal.toLocaleString('es-AR', { minimumFractionDigits: 0 })} en stock`}
          color="bg-indigo-100 dark:bg-indigo-900/30"
          icon={
            <svg className="w-5 h-5 text-indigo-600 dark:text-indigo-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M20.25 7.5l-.625 10.632a2.25 2.25 0 01-2.247 2.118H6.622a2.25 2.25 0 01-2.247-2.118L3.75 7.5M10 11.25h4M3.375 7.5h17.25c.621 0 1.125-.504 1.125-1.125v-1.5c0-.621-.504-1.125-1.125-1.125H3.375c-.621 0-1.125.504-1.125 1.125v1.5c0 .621.504 1.125 1.125 1.125z" />
            </svg>
          }
        />
        <StatCard
          label="Disponibles"
          value={stats.disponibles}
          sublabel={stats.total ? `${Math.round(stats.disponibles / stats.total * 100)}% del total` : '—'}
          color="bg-emerald-100 dark:bg-emerald-900/30"
          icon={
            <svg className="w-5 h-5 text-emerald-600 dark:text-emerald-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75 11.25 15 15 9.75M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z" />
            </svg>
          }
        />
        <StatCard
          label="Reservados"
          value={stats.reservados}
          color="bg-amber-100 dark:bg-amber-900/30"
          icon={
            <svg className="w-5 h-5 text-amber-600 dark:text-amber-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z" />
            </svg>
          }
        />
        <StatCard
          label="Vendidos"
          value={stats.vendidos}
          color="bg-blue-100 dark:bg-blue-900/30"
          icon={
            <svg className="w-5 h-5 text-blue-600 dark:text-blue-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 18.75a60.07 60.07 0 0 1 15.797 2.101c.727.198 1.453-.342 1.453-1.096V18.75M3.75 4.5v.75A.75.75 0 0 1 3 6h-.75m0 0v-.375c0-.621.504-1.125 1.125-1.125H20.25M2.25 6v9m18-10.5v.75c0 .414.336.75.75.75h.75m-1.5-1.5h.375c.621 0 1.125.504 1.125 1.125v9.75c0 .621-.504 1.125-1.125 1.125h-.375m1.5-1.5H21a.75.75 0 0 0-.75.75v.75m0 0H3.75m0 0h-.375a1.125 1.125 0 0 1-1.125-1.125V15m1.5 1.5v-.75A.75.75 0 0 0 3 15h-.75M15 10.5a3 3 0 1 1-6 0 3 3 0 0 1 6 0Zm3 0h.008v.008H18V10.5Zm-12 0h.008v.008H6V10.5Z" />
            </svg>
          }
        />
      </div>

      {/* Charts */}
      {discos.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">

          <div className="card p-5">
            <h3 className="text-sm font-semibold text-slate-700 dark:text-gray-300 mb-4">Inventario por estado</h3>
            <ResponsiveContainer width="100%" height={180}>
              <BarChart data={barData} margin={{ top: 0, right: 0, left: -20, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke={gridColor} vertical={false} />
                <XAxis dataKey="estado" tick={{ fontSize: 11, fill: axisColor }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fontSize: 11, fill: axisColor }} axisLine={false} tickLine={false} allowDecimals={false} />
                <Tooltip content={<CustomBarTooltip />} cursor={{ fill: dark ? '#1f2937' : '#f8fafc' }} />
                <Bar dataKey="cantidad" radius={[4, 4, 0, 0]} maxBarSize={48}>
                  {barData.map((entry, i) => (
                    <Cell key={i} fill={entry.fill} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>

          <div className="card p-5">
            <h3 className="text-sm font-semibold text-slate-700 dark:text-gray-300 mb-4">Distribución por condición</h3>
            {pieData.length > 0 ? (
              <ResponsiveContainer width="100%" height={180}>
                <PieChart>
                  <Pie
                    data={pieData}
                    cx="50%"
                    cy="50%"
                    innerRadius={45}
                    outerRadius={72}
                    paddingAngle={3}
                    dataKey="value"
                  >
                    {pieData.map((entry, i) => (
                      <Cell key={i} fill={entry.fill} />
                    ))}
                  </Pie>
                  <Tooltip content={<CustomPieTooltip />} />
                  <Legend
                    iconType="circle"
                    iconSize={8}
                    formatter={(value) => (
                      <span style={{ fontSize: 11, color: axisColor }}>{value}</span>
                    )}
                  />
                </PieChart>
              </ResponsiveContainer>
            ) : (
              <div className="h-[180px] flex items-center justify-center text-slate-400 dark:text-gray-600 text-sm">
                Sin datos de condición
              </div>
            )}
          </div>
        </div>
      )}

      {/* Toolbar */}
      <div className="flex flex-col sm:flex-row gap-3">
        <div className="relative flex-1">
          <svg className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 dark:text-gray-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="m21 21-5.197-5.197m0 0A7.5 7.5 0 1 0 5.196 5.196a7.5 7.5 0 0 0 10.607 10.607Z" />
          </svg>
          <input
            value={busqueda}
            onChange={buscar}
            placeholder="Buscar por artista..."
            className="input pl-9"
          />
        </div>

        <div className="flex gap-2 flex-wrap sm:flex-nowrap">
          {ESTADOS_FILTRO.map(estado => (
            <button
              key={estado}
              onClick={() => setFiltroEstado(estado)}
              className={`text-xs px-3 py-2 rounded-lg font-medium transition-colors whitespace-nowrap ${
                filtroEstado === estado
                  ? 'bg-indigo-600 text-white'
                  : 'bg-slate-100 dark:bg-gray-800 text-slate-600 dark:text-gray-400 hover:bg-slate-200 dark:hover:bg-gray-700'
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

        <button
          onClick={() => setMostrarModal(true)}
          className="btn-primary flex items-center gap-2 whitespace-nowrap"
        >
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
          </svg>
          Agregar disco
        </button>
      </div>

      {/* Table */}
      <div className="card overflow-hidden">
        {loading ? (
          <div className="flex items-center justify-center py-16 gap-3">
            <svg className="animate-spin w-5 h-5 text-indigo-500" viewBox="0 0 24 24" fill="none">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/>
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/>
            </svg>
            <span className="text-slate-500 dark:text-gray-400 text-sm">Cargando inventario...</span>
          </div>
        ) : discosFiltrados.length === 0 ? (
          <EmptyState />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-100 dark:border-gray-800">
                  <th className="text-left px-5 py-3 text-xs font-semibold text-slate-500 dark:text-gray-500 uppercase tracking-wider">Artista / Álbum</th>
                  <th className="text-left px-5 py-3 text-xs font-semibold text-slate-500 dark:text-gray-500 uppercase tracking-wider hidden sm:table-cell">Tipo</th>
                  <th className="text-left px-5 py-3 text-xs font-semibold text-slate-500 dark:text-gray-500 uppercase tracking-wider hidden md:table-cell">Condición</th>
                  <th className="text-left px-5 py-3 text-xs font-semibold text-slate-500 dark:text-gray-500 uppercase tracking-wider">Precio</th>
                  <th className="text-left px-5 py-3 text-xs font-semibold text-slate-500 dark:text-gray-500 uppercase tracking-wider">Estado</th>
                  <th className="text-left px-5 py-3 text-xs font-semibold text-slate-500 dark:text-gray-500 uppercase tracking-wider hidden lg:table-cell">QR</th>
                  <th className="px-5 py-3"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-gray-800/60">
                {discosFiltrados.map(d => {
                  const estilo = ESTADO_STYLE[d.estado] || ESTADO_STYLE.DESCONTINUADO
                  return (
                    <tr key={d.idDisco} className="hover:bg-slate-50 dark:hover:bg-gray-800/40 transition-colors">
                      <td className="px-5 py-4">
                        <div className="font-semibold text-slate-900 dark:text-white">{d.artista}</div>
                        <div className="text-slate-500 dark:text-gray-400 text-xs mt-0.5">
                          {d.album}{d.anio ? ` · ${d.anio}` : ''}
                          {d.genero ? <span className="ml-1.5 text-slate-400 dark:text-gray-600">· {d.genero}</span> : null}
                        </div>
                      </td>
                      <td className="px-5 py-4 text-slate-600 dark:text-gray-400 hidden sm:table-cell">{d.tipoDisco}</td>
                      <td className="px-5 py-4 hidden md:table-cell">
                        <span className={`text-xs ${CONDICION_STYLE[d.condicion] || 'text-slate-500'}`}>
                          {d.condicion}
                        </span>
                      </td>
                      <td className="px-5 py-4 font-semibold text-slate-900 dark:text-white tabular-nums">
                        {d.precioVenta ? `$${Number(d.precioVenta).toLocaleString('es-AR')}` : <span className="text-slate-400 dark:text-gray-600 font-normal">—</span>}
                      </td>
                      <td className="px-5 py-4">
                        <span className={`inline-flex items-center gap-1.5 text-xs px-2.5 py-1 rounded-full font-medium ${estilo.bg} ${estilo.text}`}>
                          <span className={`w-1.5 h-1.5 rounded-full ${estilo.dot}`} />
                          {d.estado.charAt(0) + d.estado.slice(1).toLowerCase()}
                        </span>
                      </td>
                      <td className="px-5 py-4 hidden lg:table-cell">
                        {d.codigoQr && (
                          <button
                            onClick={() => setQrDisco(d)}
                            className="text-xs text-indigo-600 dark:text-indigo-400 hover:text-indigo-800 dark:hover:text-indigo-300 font-medium flex items-center gap-1 transition-colors"
                          >
                            <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                              <path strokeLinecap="round" strokeLinejoin="round" d="M3.75 4.875c0-.621.504-1.125 1.125-1.125h4.5c.621 0 1.125.504 1.125 1.125v4.5c0 .621-.504 1.125-1.125 1.125h-4.5A1.125 1.125 0 0 1 3.75 9.375v-4.5ZM3.75 14.625c0-.621.504-1.125 1.125-1.125h4.5c.621 0 1.125.504 1.125 1.125v4.5c0 .621-.504 1.125-1.125 1.125h-4.5a1.125 1.125 0 0 1-1.125-1.125v-4.5ZM13.5 4.875c0-.621.504-1.125 1.125-1.125h4.5c.621 0 1.125.504 1.125 1.125v4.5c0 .621-.504 1.125-1.125 1.125h-4.5A1.125 1.125 0 0 1 13.5 9.375v-4.5Z" />
                            </svg>
                            Ver QR
                          </button>
                        )}
                      </td>
                      <td className="px-5 py-4">
                        <div className="flex items-center gap-2">
                          {d.estado === 'DISPONIBLE' && (
                            <button
                              onClick={() => cambiarEstado(d.idDisco, 'RESERVADO')}
                              className="text-xs bg-amber-50 dark:bg-amber-900/20 hover:bg-amber-100 dark:hover:bg-amber-900/40 text-amber-700 dark:text-amber-400 px-2.5 py-1.5 rounded-lg transition-colors font-medium"
                            >
                              Reservar
                            </button>
                          )}
                          {(d.estado === 'DISPONIBLE' || d.estado === 'RESERVADO') && (
                            <button
                              onClick={() => cambiarEstado(d.idDisco, 'VENDIDO')}
                              className="text-xs bg-slate-100 dark:bg-gray-800 hover:bg-slate-200 dark:hover:bg-gray-700 text-slate-600 dark:text-gray-400 px-2.5 py-1.5 rounded-lg transition-colors"
                            >
                              Vendido
                            </button>
                          )}
                          {d.estado === 'VENDIDO' && (
                            <button
                              onClick={() => cambiarEstado(d.idDisco, 'DISPONIBLE')}
                              className="text-xs bg-emerald-50 dark:bg-emerald-900/20 hover:bg-emerald-100 dark:hover:bg-emerald-900/40 text-emerald-700 dark:text-emerald-400 px-2.5 py-1.5 rounded-lg transition-colors font-medium"
                            >
                              Disponible
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}

        {!loading && discosFiltrados.length > 0 && (
          <div className="px-5 py-3 border-t border-slate-100 dark:border-gray-800 text-xs text-slate-400 dark:text-gray-600">
            {discosFiltrados.length} {discosFiltrados.length === 1 ? 'disco' : 'discos'} mostrados
            {filtroEstado !== 'TODOS' && ` · filtro: ${filtroEstado}`}
          </div>
        )}
      </div>

      {/* QR Modal */}
      {qrDisco && (
        <div
          className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 px-4"
          onClick={() => setQrDisco(null)}
        >
          <div
            className="card p-6 text-center w-full max-w-xs shadow-2xl"
            onClick={e => e.stopPropagation()}
          >
            <div className="mb-4">
              <div className="font-bold text-slate-900 dark:text-white text-base">{qrDisco.artista}</div>
              <div className="text-slate-500 dark:text-gray-400 text-sm">{qrDisco.album}</div>
            </div>
            <div className="bg-white rounded-xl p-3 inline-block mb-4 shadow-sm">
              <img
                src={`/api/qr/descargar/${qrDisco.idDisco}`}
                alt="Código QR"
                className="w-44 h-44 block"
              />
            </div>
            <p className="text-slate-400 dark:text-gray-600 text-xs font-mono mb-4 break-all">{qrDisco.codigoQr}</p>
            <div className="flex gap-3">
              <button
                onClick={() => setQrDisco(null)}
                className="btn-secondary flex-1"
              >
                Cerrar
              </button>
              <a
                href={`/api/qr/descargar/${qrDisco.idDisco}`}
                download={`qr-${qrDisco.artista}-${qrDisco.album}.png`}
                className="btn-primary flex-1 text-center"
              >
                Descargar
              </a>
            </div>
          </div>
        </div>
      )}

      {mostrarModal && (
        <AddDiscoModal
          onClose={() => setMostrarModal(false)}
          onCreado={(disco) => setDiscos(prev => [disco, ...prev])}
        />
      )}
    </div>
  )
}

import EstadoBadge from './EstadoBadge'

const ESTADOS_SIGUIENTES = {
  DISPONIBLE:    ['RESERVADO', 'VENDIDO'],
  RESERVADO:     ['DISPONIBLE', 'VENDIDO'],
  VENDIDO:       [],
  DESCONTINUADO: ['DISPONIBLE'],
}

export default function DiscoCard({ disco, onEditar, onCambiarEstado, onEliminar }) {
  const precio = disco.precioVenta
    ? `$ ${Math.round(Number(disco.precioVenta)).toLocaleString('es-AR')}`
    : null

  const estadosSiguientes = ESTADOS_SIGUIENTES[disco.estado] || []

  return (
    <div className="bg-white dark:bg-gray-800 border border-slate-200 dark:border-gray-700 rounded-xl shadow-sm hover:shadow-md dark:hover:shadow-none dark:hover:border-gray-600 transition-all duration-200 p-4 flex flex-col gap-3">

      {/* Header: código + estado */}
      <div className="flex items-start justify-between gap-2">
        {disco.codigoInterno && (
          <span className="text-xs text-slate-400 dark:text-gray-500 font-mono">{disco.codigoInterno}</span>
        )}
        <div className="ml-auto">
          <EstadoBadge estado={disco.estado} />
        </div>
      </div>

      {/* Contenido principal */}
      <div className="flex-1">
        <div className="font-bold text-slate-900 dark:text-white text-base leading-tight">{disco.artista}</div>
        <div className="text-slate-600 dark:text-gray-300 text-sm mt-0.5">{disco.album}</div>
        {(disco.anio || disco.genero) && (
          <div className="text-slate-400 dark:text-gray-500 text-xs mt-1.5 flex items-center gap-1.5 flex-wrap">
            {disco.anio && <span>{disco.anio}</span>}
            {disco.anio && disco.genero && <span>·</span>}
            {disco.genero && <span>{disco.genero}</span>}
          </div>
        )}
      </div>

      {/* Precio */}
      {precio && (
        <div className="font-bold text-slate-900 dark:text-white tabular-nums text-lg">
          {precio}
        </div>
      )}

      {/* Acciones */}
      <div className="flex gap-2 pt-1 border-t border-slate-100 dark:border-gray-700/50 flex-wrap">
        <button
          onClick={() => onEditar(disco)}
          className="text-xs px-2.5 py-1.5 rounded-lg bg-[#7E9FA8]/10 dark:bg-[#7E9FA8]/10 text-[#5C7D87] dark:text-[#7E9FA8] hover:bg-[#7E9FA8]/20 dark:hover:bg-[#7E9FA8]/20 font-medium transition-colors"
        >
          Editar
        </button>

        {estadosSiguientes.map(nuevoEstado => (
          <button
            key={nuevoEstado}
            onClick={() => onCambiarEstado(disco.idDisco, nuevoEstado)}
            className="text-xs px-2.5 py-1.5 rounded-lg bg-slate-100 dark:bg-gray-700 text-slate-600 dark:text-gray-300 hover:bg-slate-200 dark:hover:bg-gray-600 font-medium transition-colors"
          >
            {nuevoEstado.charAt(0) + nuevoEstado.slice(1).toLowerCase()}
          </button>
        ))}

        <button
          onClick={() => onEliminar(disco)}
          className="text-xs px-2.5 py-1.5 rounded-lg bg-red-50 dark:bg-red-900/20 text-red-500 dark:text-red-400 hover:bg-red-100 dark:hover:bg-red-900/40 font-medium transition-colors ml-auto"
        >
          Eliminar
        </button>
      </div>
    </div>
  )
}

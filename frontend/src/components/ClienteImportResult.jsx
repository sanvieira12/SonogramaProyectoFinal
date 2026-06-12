const STATUS_STYLES = {
  created: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400',
  updated: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
  skipped: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400',
  error: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
}

const STATUS_LABELS = {
  created: 'Creado',
  updated: 'Actualizado',
  skipped: 'Omitido',
  error: 'Error',
}

function Metric({ label, value }) {
  return (
    <div className="rounded-lg border border-slate-200 dark:border-stone-700 px-3 py-2">
      <p className="text-[11px] uppercase tracking-wider text-slate-400 dark:text-stone-500">{label}</p>
      <p className="mt-1 text-lg font-bold text-slate-900 dark:text-white">{value ?? 0}</p>
    </div>
  )
}

export default function ClienteImportResult({ result, onClose }) {
  if (!result) return null

  if (result.error) {
    return (
      <div className="p-4 rounded-xl bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 text-sm text-red-700 dark:text-red-400 flex items-start justify-between gap-4">
        <span><strong>No se pudo importar el Excel.</strong> {result.error}</span>
        <button onClick={onClose} className="text-xs hover:underline">Cerrar</button>
      </div>
    )
  }

  return (
    <section className="card p-4 space-y-4" aria-label="Resultado de importación">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="font-bold text-slate-900 dark:text-white">Importación completada</h2>
          <p className="text-xs text-slate-500 dark:text-stone-400 mt-1">
            {result.creados ?? 0} creados • {result.actualizados ?? 0} actualizados • {result.omitidos ?? 0} omitidos
          </p>
          <p className="text-xs text-slate-400 dark:text-stone-500 mt-0.5">
            Hoja: {result.hoja || 'Primera hoja'}
          </p>
        </div>
        <button onClick={onClose} className="text-xs text-slate-500 dark:text-stone-400 hover:underline">
          Cerrar
        </button>
      </div>

      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-2">
        <Metric label="Filas leídas" value={result.totalFilasLeidas} />
        <Metric label="Clientes válidos" value={result.clientesValidos} />
        <Metric label="Nuevos" value={result.creados} />
        <Metric label="Actualizados" value={result.actualizados} />
        <Metric label="Omitidos" value={result.omitidos} />
        <Metric label="Con incidencias" value={result.filasConIncidencias} />
      </div>

      {result.filas?.length > 0 && (
        <div className="overflow-x-auto rounded-lg border border-slate-200 dark:border-stone-800">
          <table className="w-full text-xs">
            <thead className="bg-slate-50 dark:bg-stone-950">
              <tr>
                <th className="text-left px-3 py-2">Fila</th>
                <th className="text-left px-3 py-2">Cliente</th>
                <th className="text-left px-3 py-2">Cédula</th>
                <th className="text-left px-3 py-2">Teléfono</th>
                <th className="text-left px-3 py-2">Estado</th>
                <th className="text-left px-3 py-2">Detalle</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-stone-800">
              {result.filas.map((fila, index) => (
                <tr key={`${fila.filaExcel}-${index}`}>
                  <td className="px-3 py-2 font-mono">{fila.filaExcel}</td>
                  <td className="px-3 py-2">{fila.nombre || '—'}</td>
                  <td className="px-3 py-2 font-mono">{fila.cedula || '—'}</td>
                  <td className="px-3 py-2 font-mono">{fila.telefono || '—'}</td>
                  <td className="px-3 py-2">
                    <span className={`inline-flex rounded-full px-2 py-0.5 font-semibold ${STATUS_STYLES[fila.estado] || STATUS_STYLES.error}`}>
                      {STATUS_LABELS[fila.estado] || fila.estado}
                    </span>
                  </td>
                  <td className="px-3 py-2 text-slate-500 dark:text-stone-400">{fila.mensaje}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

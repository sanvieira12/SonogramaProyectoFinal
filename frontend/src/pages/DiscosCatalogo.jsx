import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { discoService } from '../services/discoService'
import DiscoForm from '../components/DiscoForm'
import ConfirmModal from '../components/ConfirmModal'
import Paginacion from '../components/Paginacion'
import CompactPlayer from '../components/CompactPlayer'
import { stopAllPreviews } from '../components/audioPreviewPlayback'
import { api, FINANCIAL_DATA_CHANGED_EVENT, resolveApiUrl } from '../api/sonograma'
import { downloadBlob } from '../utils/downloadBlob'

const FILTROS = ['TODOS', 'DISPONIBLE', 'RESERVADO', 'VENDIDO', 'SIN_STOCK']
const FILTROS_CONDICION = [
  { value: 'TODOS', label: 'Todos' },
  { value: 'NUEVO', label: 'Nuevos' },
  { value: 'USADO', label: 'Usados' },
]

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

function SortHeader({ label, sortKey, activeKey, direction, onSort }) {
  const active = activeKey === sortKey
  const arrow = active ? (direction === 'asc' ? '↑' : '↓') : '↕'
  return (
    <button
      type="button"
      onClick={() => onSort(sortKey)}
      className={`inline-flex items-center gap-1 text-xs font-semibold uppercase tracking-wider transition-colors ${
        active ? 'text-[#5C7D87] dark:text-[#7E9FA8]' : 'text-slate-500 dark:text-stone-500 hover:text-slate-700 dark:hover:text-stone-300'
      }`}
    >
      <span>{label}</span>
      <span className="text-[11px] leading-none">{arrow}</span>
    </button>
  )
}

function parseSortValue(disco, sortKey) {
  if (sortKey === 'price') {
    const value = Number(catalogPrice(disco))
    return Number.isFinite(value) ? value : null
  }
  if (sortKey === 'importDate') {
    const effectiveDate = disco.fechaActualizacion || disco.fechaIngreso
    const time = effectiveDate ? new Date(effectiveDate).getTime() : Number.NaN
    return Number.isFinite(time) ? time : null
  }
  return null
}

function formatImportDate(value) {
  if (!value) return null
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return null
  return date.toLocaleDateString('es-UY', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  })
}

function sellingPriceLabel(value) {
  return value != null ? `UYU $${Number(value).toLocaleString('es-UY')}` : 'Sin precio'
}

function normalizedCategory(disco) {
  return String(disco?.condicion || '').trim().toUpperCase()
}

function isNewCatalogProduct(disco) {
  return normalizedCategory(disco) === 'NUEVO'
}

function physicalCopyPriceLabel(value) {
  return value != null ? `UYU $${Number(value).toLocaleString('es-UY')}` : 'Sin precio específico'
}

function physicalCopyCount(disco) {
  const explicit = disco?.totalCopias == null ? Number.NaN : Number(disco.totalCopias)
  if (Number.isFinite(explicit)) return explicit
  if (Array.isArray(disco?.qrCopies) && disco.qrCopies.length > 0) return disco.qrCopies.length
  const available = disco?.cantidadCopias == null ? Number.NaN : Number(disco.cantidadCopias)
  return Number.isFinite(available) ? available : 0
}

function availableCommercialCopies(copyState) {
  return (copyState?.copies || []).filter(copy => copy.estado === 'DISPONIBLE')
}

function availableCopyCount(disco) {
  const available = disco?.cantidadCopias == null ? Number.NaN : Number(disco.cantidadCopias)
  return Number.isFinite(available) ? Math.max(0, available) : 0
}

function pendingUsedCopySummary(disco) {
  const count = availableCopyCount(disco)
  if (count === 0) return 'Sin copias disponibles'
  return `${count} ${count === 1 ? 'copia disponible' : 'copias disponibles'} · ver detalle`
}

function copyPriceSummary(disco, copyState) {
  if (isNewCatalogProduct(disco)) return sellingPriceLabel(catalogPrice(disco))
  if (!copyState?.loaded) return pendingUsedCopySummary(disco)
  const copies = availableCommercialCopies(copyState)
  if (copies.length === 0) return 'Sin copias disponibles'
  const values = [...new Set(copies
    .filter(copy => copy.precioVenta != null)
    .map(copy => Number(copy.precioVenta))
    .filter(Number.isFinite))]
    .sort((a, b) => a - b)
  if (values.length === 0) return copies.length === 1 ? 'Sin precio específico' : 'Precios por copia'
  if (values.length === 1 && copies.every(copy => copy.precioVenta != null)) {
    return sellingPriceLabel(values[0])
  }
  if (copies.some(copy => copy.precioVenta == null)) return 'Precios por copia'
  return `UYU $${values[0].toLocaleString('es-UY')}–$${values.at(-1).toLocaleString('es-UY')}`
}

function copyConditionSummary(disco, copyState) {
  if (isNewCatalogProduct(disco)) return normalizedCategory(disco)
  if (!copyState?.loaded) return pendingUsedCopySummary(disco)
  const copies = availableCommercialCopies(copyState)
  if (copies.length === 0) return 'Sin copias disponibles'
  const values = [...new Set(copies.map(copy => copy.condicionFisica).filter(Boolean))]
  if (values.length === 1 && copies.every(copy => copy.condicionFisica)) return values[0]
  if (values.length > 1 && copies.every(copy => copy.condicionFisica)) return `${values.length} condiciones`
  return copies.length === 1 ? 'Sin condición registrada' : 'Condición por copia'
}

const COPY_STATE = {
  DISPONIBLE: {
    label: 'Disponible',
    className: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-900/20 dark:text-emerald-400',
  },
  VENDIDO: {
    label: 'Vendida',
    className: 'bg-slate-100 text-slate-600 dark:bg-stone-800 dark:text-stone-400',
  },
  REMOVED: {
    label: 'Retirada',
    className: 'bg-red-50 text-red-700 dark:bg-red-900/20 dark:text-red-300',
  },
}

const DISPOSITION_LABELS = {
  DATA_ENTRY_CORRECTION: 'Corrección de datos',
  RETURNED_TO_PROVIDER: 'Devuelta al proveedor',
  DAMAGED: 'Dañada',
  REMOVED_FROM_INVENTORY: 'Retirada del inventario',
  OTHER: 'Otro',
}

function normalizedCode(value) {
  return String(value || '').trim().toUpperCase()
}

function physicalCopySource(copy) {
  const source = String(copy?.sourceCustomerCode || '').trim()
  const normalizedSource = String(copy?.normalizedSourceCustomerCode || '').trim()
  return source || normalizedSource || 'Sin procedencia'
}

function copyMatchesSource(copy, sourceCode) {
  const expected = normalizedCode(sourceCode)
  if (!expected) return false
  return normalizedCode(copy?.normalizedSourceCustomerCode || copy?.sourceCustomerCode) === expected
}

function formatCopyDate(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleString('es-UY', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
  })
}

function chooseDefaultCopy(copies, activeSourceCode) {
  const matching = activeSourceCode
    ? copies.filter(copy => copyMatchesSource(copy, activeSourceCode))
    : []
  return matching.find(copy => copy.estado === 'DISPONIBLE')
    || matching[0]
    || copies.find(copy => copy.estado === 'DISPONIBLE')
    || copies[0]
    || null
}

function PhysicalCopiesSection({ copyState, activeSourceCode, onRetry, onViewQr, onRequestRemoval }) {
  const copies = [...(copyState?.copies || [])].sort((a, b) =>
    (Number(a.copyNumber) - Number(b.copyNumber)) || (Number(a.id) - Number(b.id)))
  const [selectedCopyId, setSelectedCopyId] = useState(null)

  const selectedCopy = copies.find(copy => String(copy.id) === String(selectedCopyId))
    || chooseDefaultCopy(copies, activeSourceCode)
  const availableCount = copies.filter(copy => copy.estado === 'DISPONIBLE').length

  return (
    <section data-testid="physical-copy-section" className="border-t border-slate-100 pt-4 dark:border-stone-800">
      <div className="mb-3 flex items-start justify-between gap-3">
        <div>
          <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-400 dark:text-stone-500">Copias físicas</p>
          {!copyState?.loading && !copyState?.error && copies.length > 0 && (
            <p className="mt-0.5 text-xs text-slate-500 dark:text-stone-400">
              {availableCount} {availableCount === 1 ? 'disponible' : 'disponibles'} · {copies.length} {copies.length === 1 ? 'copia física' : 'copias físicas'}
            </p>
          )}
          {activeSourceCode && !copyState?.loading && !copyState?.error && copies.length > 0 && (
            <p className="mt-1 text-[11px] text-slate-400 dark:text-stone-500">
              Se muestran todas las copias; ✓ indica {normalizedCode(activeSourceCode)}.
            </p>
          )}
        </div>
      </div>

      {copyState?.loading && (
        <div role="status" className="rounded-lg bg-slate-50 px-3 py-4 text-center text-xs text-slate-500 dark:bg-stone-950 dark:text-stone-400">
          Cargando copias físicas…
        </div>
      )}

      {copyState?.error && (
        <div role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-3 text-xs text-red-700 dark:border-red-800 dark:bg-red-900/20 dark:text-red-300">
          <p>{copyState.error}</p>
          <button type="button" onClick={onRetry} className="mt-2 font-semibold underline">Reintentar</button>
        </div>
      )}

      {!copyState?.loading && !copyState?.error && copies.length === 0 && (
        <p className="rounded-lg bg-slate-50 px-3 py-4 text-xs text-slate-500 dark:bg-stone-950 dark:text-stone-400">
          No hay copias físicas registradas.
        </p>
      )}

      {!copyState?.loading && !copyState?.error && selectedCopy && (
        <div className="space-y-3">
          {copies.length >= 2 && copies.length <= 4 && (
            <div className="flex flex-wrap gap-2" aria-label="Seleccionar copia física">
              {copies.map(copy => {
                const active = String(copy.id) === String(selectedCopy.id)
                const matches = copyMatchesSource(copy, activeSourceCode)
                return (
                  <button
                    key={copy.id}
                    type="button"
                    aria-label={`Copia ${copy.copyNumber ?? '—'}`}
                    aria-pressed={active}
                    onClick={() => setSelectedCopyId(copy.id)}
                    className={`min-w-0 rounded-full border px-3 py-1.5 text-xs font-medium transition-colors ${active
                      ? 'border-[#7E9FA8] bg-[#7E9FA8] text-white'
                      : 'border-slate-200 bg-slate-50 text-slate-600 hover:bg-slate-100 dark:border-stone-700 dark:bg-stone-950 dark:text-stone-400 dark:hover:bg-stone-800'}`}
                  >
                    Copia {copy.copyNumber ?? '—'} · {physicalCopySource(copy)}{matches ? ' ✓' : ''}
                  </button>
                )
              })}
            </div>
          )}

          {copies.length >= 5 && (
            <label className="block text-xs font-medium text-slate-600 dark:text-stone-400">
              Copia
              <select
                aria-label="Copia física"
                value={String(selectedCopy.id)}
                onChange={event => setSelectedCopyId(event.target.value)}
                className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs text-slate-700 dark:border-stone-700 dark:bg-stone-950 dark:text-stone-300"
              >
                {copies.map(copy => (
                  <option key={copy.id} value={String(copy.id)}>
                    Copia {copy.copyNumber ?? '—'} — {physicalCopySource(copy)} — {(COPY_STATE[copy.estado] || {}).label || copy.estado}
                  </option>
                ))}
              </select>
            </label>
          )}

          <article className="rounded-xl border border-slate-200 bg-slate-50/60 p-3 dark:border-stone-700 dark:bg-stone-950/60">
            <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
              <h3 className="text-sm font-bold text-slate-800 dark:text-stone-200">Copia {selectedCopy.copyNumber ?? '—'}</h3>
              <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${(COPY_STATE[selectedCopy.estado] || COPY_STATE.VENDIDO).className}`}>
                {(COPY_STATE[selectedCopy.estado] || {}).label || selectedCopy.estado}
              </span>
            </div>

            {copyMatchesSource(selectedCopy, activeSourceCode) && (
              <p className="mb-3 inline-flex max-w-full rounded-full bg-[#7E9FA8]/15 px-2.5 py-1 text-[11px] font-semibold text-[#5C7D87] break-all dark:text-[#9BC0C9]">
                Coincide con filtro {normalizedCode(activeSourceCode)}
              </p>
            )}

            <div className="grid grid-cols-2 gap-3">
              {[
                ['Procedencia', physicalCopySource(selectedCopy)],
                ['Condición', selectedCopy.condicionFisica || 'Sin condición registrada'],
                ['Precio', physicalCopyPriceLabel(selectedCopy.precioVenta)],
                ['Estado', (COPY_STATE[selectedCopy.estado] || {}).label || selectedCopy.estado],
              ].map(([label, value]) => (
                <div key={label} className="min-w-0">
                  <p className="text-[10px] uppercase tracking-wider text-slate-400 dark:text-stone-500">{label}</p>
                  <p className="break-words text-xs font-semibold text-slate-700 dark:text-stone-300">{value}</p>
                </div>
              ))}
              <div className="col-span-2 min-w-0">
                <p className="text-[10px] uppercase tracking-wider text-slate-400 dark:text-stone-500">QR</p>
                <p className="break-all text-xs font-semibold text-slate-700 dark:text-stone-300">{selectedCopy.codigoQr || '—'}</p>
              </div>
            </div>

            {selectedCopy.codigoQr && onViewQr && (
              <button
                type="button"
                onClick={() => onViewQr(selectedCopy)}
                className="btn-secondary mt-3 w-full"
              >
                Ver QR de Copia {selectedCopy.copyNumber ?? '—'}
              </button>
            )}

            {selectedCopy.estado === 'DISPONIBLE' && onRequestRemoval && (
              <button
                type="button"
                onClick={() => onRequestRemoval(selectedCopy)}
                className="btn-secondary mt-2 w-full border-red-200 text-red-700 hover:bg-red-50 dark:border-red-900 dark:text-red-300 dark:hover:bg-red-950/30"
              >
                Retirar Copia {selectedCopy.copyNumber ?? '—'}
              </button>
            )}

            {selectedCopy.estado === 'REMOVED' && (selectedCopy.dispositionReason || selectedCopy.dispositionNote || selectedCopy.disposedAt || selectedCopy.disposedBy) && (
              <div className="mt-3 rounded-lg border border-red-100 bg-red-50/70 px-3 py-2 text-xs text-red-800 dark:border-red-900/60 dark:bg-red-950/20 dark:text-red-200">
                {selectedCopy.dispositionReason && <p><span className="font-semibold">Motivo:</span> {DISPOSITION_LABELS[selectedCopy.dispositionReason] || selectedCopy.dispositionReason}</p>}
                {selectedCopy.disposedAt && <p><span className="font-semibold">Fecha de retiro:</span> {formatCopyDate(selectedCopy.disposedAt)}</p>}
                {selectedCopy.disposedBy && <p className="break-words"><span className="font-semibold">Retirada por:</span> {selectedCopy.disposedBy}</p>}
                {selectedCopy.dispositionNote && <p className="break-words"><span className="font-semibold">Nota:</span> {selectedCopy.dispositionNote}</p>}
              </div>
            )}

            <details className="mt-3 text-xs text-slate-500 dark:text-stone-400">
              <summary className="cursor-pointer font-medium">Trazabilidad</summary>
              <dl className="mt-2 grid grid-cols-2 gap-x-3 gap-y-2">
                <div><dt className="text-[10px] uppercase">ID de copia</dt><dd className="break-all">{selectedCopy.id ?? '—'}</dd></div>
                <div><dt className="text-[10px] uppercase">Nº de copia</dt><dd>{selectedCopy.copyNumber ?? '—'}</dd></div>
                <div><dt className="text-[10px] uppercase">Lote técnico</dt><dd className="break-all">{selectedCopy.manualBatchId ?? '—'}</dd></div>
                <div><dt className="text-[10px] uppercase">Fecha de ingreso</dt><dd>{formatCopyDate(selectedCopy.createdAt)}</dd></div>
                <div className="col-span-2"><dt className="text-[10px] uppercase">QR</dt><dd className="break-all">{selectedCopy.codigoQr || '—'}</dd></div>
              </dl>
            </details>
          </article>
        </div>
      )}
    </section>
  )
}

function RetainedCopyRemovalModal({ disco, copy, loading, error, onConfirm, onCancel }) {
  const [reason, setReason] = useState('')
  const [note, setNote] = useState('')
  if (!disco || !copy) return null
  const stateLabel = (COPY_STATE[copy.estado] || {}).label || copy.estado || '—'

  return (
    <ConfirmModal
      titulo={`Retirar Copia ${copy.copyNumber ?? '—'}`}
      mensaje={`Producto: ${disco.artista || '—'} — ${disco.album || '—'}. Estado actual: ${stateLabel}. Esta copia dejará de contar como stock disponible; su QR y su historial se conservarán.`}
      onConfirmar={() => onConfirm({ reason, note: note.trim() || null })}
      onCancelar={onCancel}
      cargando={loading}
      cargandoTexto="Retirando…"
      confirmarTexto="Confirmar retiro"
      confirmarDeshabilitado={!reason}
      error={error}
      contenido={(
        <div className="mb-5 space-y-4">
          <label className="block text-sm text-slate-600 dark:text-white/80">
            Motivo de retiro
            <select
              aria-label="Motivo de retiro"
              value={reason}
              onChange={event => setReason(event.target.value)}
              disabled={loading}
              className="input mt-2 w-full"
              required
            >
              <option value="">Seleccionar motivo</option>
              {Object.entries(DISPOSITION_LABELS).map(([value, label]) => (
                <option key={value} value={value}>{label}</option>
              ))}
            </select>
          </label>
          <label className="block text-sm text-slate-600 dark:text-white/80">
            Nota opcional
            <textarea
              aria-label="Nota de retiro"
              value={note}
              onChange={event => setNote(event.target.value)}
              disabled={loading}
              maxLength={2000}
              rows={3}
              className="input mt-2 w-full resize-y"
              placeholder="Contexto adicional del retiro"
            />
          </label>
        </div>
      )}
    />
  )
}

function NewPhysicalCopiesQrSection({ disco, copyState, onRetry, onViewQr }) {
  const copies = [...(copyState?.copies || [])].sort((a, b) =>
    (Number(a.copyNumber) - Number(b.copyNumber)) || (Number(a.id) - Number(b.id)))
  const availableQuantity = Number.isFinite(Number(disco?.cantidadCopias))
    ? Number(disco.cantidadCopias)
    : 0

  return (
    <section data-testid="new-copy-qr-section" className="border-t border-slate-100 pt-4 dark:border-stone-800">
      <div className="mb-3">
        <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-400 dark:text-stone-500">Copias físicas / QR</p>
        {!copyState?.loading && !copyState?.error && copies.length > 0 && (
          <p className="mt-0.5 text-xs text-slate-500 dark:text-stone-400">
            {availableQuantity} {availableQuantity === 1 ? 'disponible' : 'disponibles'} · {copies.length} QR {copies.length === 1 ? 'retenido' : 'retenidos'}
          </p>
        )}
      </div>

      {copyState?.loading && (
        <div role="status" className="rounded-lg bg-slate-50 px-3 py-4 text-center text-xs text-slate-500 dark:bg-stone-950 dark:text-stone-400">
          Cargando copias físicas…
        </div>
      )}

      {copyState?.error && (
        <div role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-3 text-xs text-red-700 dark:border-red-800 dark:bg-red-900/20 dark:text-red-300">
          <p>{copyState.error}</p>
          <button type="button" onClick={onRetry} className="mt-2 font-semibold underline">Reintentar</button>
        </div>
      )}

      {!copyState?.loading && !copyState?.error && copies.length === 0 && (
        <p className="rounded-lg bg-slate-50 px-3 py-4 text-xs text-slate-500 dark:bg-stone-950 dark:text-stone-400">
          No hay copias físicas registradas.
        </p>
      )}

      {!copyState?.loading && !copyState?.error && copies.length > 0 && (
        <div className="space-y-2">
          {copies.map(copy => {
            const state = COPY_STATE[copy.estado] || COPY_STATE.VENDIDO
            const stateLabel = COPY_STATE[copy.estado]?.label || copy.estado || '—'
            return (
              <article key={copy.id ?? copy.codigoQr ?? copy.copyNumber} className="flex items-center justify-between gap-3 rounded-xl border border-slate-200 bg-slate-50/60 p-3 dark:border-stone-700 dark:bg-stone-950/60">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <h3 className="text-sm font-bold text-slate-800 dark:text-stone-200">Copia {copy.copyNumber ?? '—'}</h3>
                    <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${state.className}`}>
                      {stateLabel}
                    </span>
                  </div>
                  <p className="mt-1 truncate text-[11px] text-slate-400 dark:text-stone-500" title={copy.codigoQr || '—'}>
                    QR: {copy.codigoQr || '—'}
                  </p>
                </div>
                {copy.codigoQr && onViewQr && (
                  <button
                    type="button"
                    onClick={() => onViewQr(copy)}
                    className="btn-secondary flex-shrink-0 px-3 py-1.5 text-xs"
                  >
                    Ver QR de Copia {copy.copyNumber ?? '—'}
                  </button>
                )}
              </article>
            )
          })}
        </div>
      )}
    </section>
  )
}

function CatalogPhysicalCopiesSection({ disco, copyState, activeSourceCode, onRetry, onViewQr, onRequestRemoval }) {
  if (isNewCatalogProduct(disco)) {
    return (
      <NewPhysicalCopiesQrSection
        disco={disco}
        copyState={copyState}
        onRetry={onRetry}
        onViewQr={onViewQr}
      />
    )
  }

  return (
    <PhysicalCopiesSection
      copyState={copyState}
      activeSourceCode={activeSourceCode}
      onRetry={onRetry}
      onViewQr={onViewQr}
      onRequestRemoval={normalizedCategory(disco) === 'USADO'
        ? copy => onRequestRemoval?.(disco, copy)
        : undefined}
    />
  )
}

function catalogPrice(disco) {
  return disco?.manualBatchPrecioVenta ?? disco?.precioVenta
}

function catalogCode(disco) {
  return disco?.codigoInterno
}

function isManualSource(source) {
  return source?.type === 'MANUAL' || String(source?.key || '').toLowerCase().startsWith('manual:')
}

function manualSourceLabel(source) {
  if (!source) return ''
  if (source.label) return source.label
  const status = source.status === 'FINALIZED' ? 'Finalizada' : 'En curso'
  const count = source.copyCount ?? source.productos ?? 0
  const sourceCode = normalizedManualCustomerCode(source) || source.customerCode || ''
  return `${sourceCode} · ${count} copias físicas · ${status}`
}

function reconciliationDifferenceText(summary) {
  if (!summary || summary.expectedCopyCount == null) return 'Cantidad esperada: No definida'
  const difference = Number(summary.difference || 0)
  if (difference === 0) return 'Diferencia: 0 · Las cantidades coinciden'
  return difference < 0
    ? `Diferencia: ${difference} · Faltan demostrar ${Math.abs(difference)} copias`
    : `Diferencia: +${difference} · Hay ${difference} copias más que el total esperado`
}

function normalizedManualCustomerCode(source) {
  const customerCode = String(source?.customerCode || '').trim()
  if (customerCode) return customerCode.toUpperCase()
  const key = String(source?.key || '')
  if (!key.toLowerCase().startsWith('manual:customer:')) return ''
  return key.slice('manual:customer:'.length).trim().toUpperCase()
}

function logicalManualSource(customerCode, sources) {
  const copies = sources.reduce((total, item) =>
    total + Number(item.copyCount ?? item.productos ?? 0), 0)
  const representative = sources.find(item => item.status === 'OPEN') || sources[0]
  return {
    ...representative,
    key: `manual:customer:${customerCode}`,
    type: 'MANUAL',
    label: null,
    customerCode,
    status: sources.some(item => item.status === 'OPEN') ? 'OPEN' : 'FINALIZED',
    batchId: representative?.batchId,
    productos: copies,
    copyCount: copies,
    porcentajeSonograma: representative?.porcentajeSonograma ?? null,
  }
}

/**
 * Keep the selector safe when an older backend still returns technical
 * manual rows. A current logical row wins; otherwise legacy rows with the
 * same customer code are projected into one logical option in the UI.
 */
function deduplicateManualSources(sources) {
  const list = Array.isArray(sources) ? sources : []
  const logicalCustomers = new Set(list
    .filter(source => String(source?.key || '').toLowerCase().startsWith('manual:customer:'))
    .map(normalizedManualCustomerCode)
    .filter(Boolean))
  const renderedLogicalCustomers = new Set()
  const legacyByCustomer = new Map()
  const result = []

  list.forEach(source => {
    if (!isManualSource(source)) {
      result.push(source)
      return
    }
    const customerCode = normalizedManualCustomerCode(source)
    const isLogical = String(source?.key || '').toLowerCase().startsWith('manual:customer:')
    if (!customerCode || logicalCustomers.has(customerCode)) {
      if (isLogical && !renderedLogicalCustomers.has(customerCode)) {
        renderedLogicalCustomers.add(customerCode)
        result.push(source)
      }
      return
    }
    const existing = legacyByCustomer.get(customerCode) || []
    legacyByCustomer.set(customerCode, [...existing, source])
  })

  legacyByCustomer.forEach((customerSources, customerCode) => {
    result.push(customerSources.length === 1
      ? customerSources[0]
      : logicalManualSource(customerCode, customerSources))
  })
  return result
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
        {hayFiltro ? 'Probá con otro filtro o búsqueda' : 'Importá discos para verlos en el catálogo'}
      </p>
    </div>
  )
}

function TrackPreviews({ disco }) {
  const [loaded, setLoaded] = useState({ discoId: null, previews: [] })
  const previews = loaded.discoId === disco?.idDisco
    ? loaded.previews
    : (disco?.audioPreviews || [])

  useEffect(() => {
    stopAllPreviews()
    if (!disco?.idDisco) return undefined
    let cancelled = false
    api.discos.previews.listar(disco.idDisco)
      .then(data => { if (!cancelled) setLoaded({ discoId: disco.idDisco, previews: data }) })
      .catch(() => {})
    return () => {
      cancelled = true
      stopAllPreviews()
    }
  }, [disco?.idDisco])

  if (previews.length === 0) {
    return <p className="text-xs text-slate-400 dark:text-stone-500">Sin previews de audio o video.</p>
  }

  return (
    <div>
      <p className="text-[10px] uppercase tracking-wider text-slate-400 dark:text-stone-500 mb-2">Tracks</p>
      <div className="space-y-1.5 max-h-56 overflow-y-auto pr-1">
        {previews.map(p => (
          <CompactPlayer
            key={p.id}
            audioUrl={p.audioUrl}
            youtubeUrl={p.youtubeUrl}
            trackName={p.trackName}
            trackPosition={p.trackPosition}
          />
        ))}
      </div>
    </div>
  )
}

function buildSaleUrl(disco, codigoQr) {
  if (!disco?.idDisco || !codigoQr) return ''
  const origin = typeof window !== 'undefined' ? window.location.origin : ''
  return `${origin}/ventas/nueva?idDisco=${disco.idDisco}&qr=${encodeURIComponent(codigoQr)}`
}

function qrCopiesForDisco(disco) {
  const copies = Array.isArray(disco?.qrCopies) ? disco.qrCopies : []
  if (copies.length > 0) return copies
  if (!disco?.codigoQr) return []
  return [{
    id: `legacy-${disco.idDisco}`,
    copyNumber: 1,
    codigoQr: disco.codigoQr,
    estado: 'DISPONIBLE',
    content: buildSaleUrl(disco, disco.codigoQr),
  }]
}

function copySelectionKey(copy) {
  return String(copy?.id || copy?.copyNumber || copy?.codigoQr || '')
}

function sanitizeFilenamePart(value, fallback) {
  const clean = String(value || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-zA-Z0-9._-]+/g, '_')
    .replace(/^_+|_+$/g, '')
    .slice(0, 80)
  return clean || fallback
}

function qrFilename(disco, copy) {
  const code = sanitizeFilenamePart(disco?.codigoInterno || `disco_${disco?.idDisco || 'sin_codigo'}`, 'sin_codigo')
  const album = sanitizeFilenamePart(disco?.album || 'album', 'album')
  const copyLabel = copy?.copyNumber ? `_copia_${copy.copyNumber}` : ''
  return `QR_${code}_${album}${copyLabel}.png`
}

function QrModal({ disco, initialCopy, loading, error, onClose, onUpdated }) {
  const [selectedCopyKey, setSelectedCopyKey] = useState(() => copySelectionKey(initialCopy))
  const [imageError, setImageError] = useState('')
  const [downloading, setDownloading] = useState(false)
  const [downloadError, setDownloadError] = useState('')
  const [copyToDelete, setCopyToDelete] = useState(null)
  const [deletingCopy, setDeletingCopy] = useState(false)
  const [deleteError, setDeleteError] = useState('')

  if (!disco) return null
  const copies = qrCopiesForDisco(disco)
  const selectedCopy = copies.find(copy => copySelectionKey(copy) === selectedCopyKey)
    || copies.find(copy => initialCopy?.codigoQr && copy.codigoQr === initialCopy.codigoQr)
    || copies.find(copy => initialCopy?.copyNumber && copy.copyNumber === initialCopy.copyNumber)
    || copies[0]
  const saleUrl = selectedCopy ? (selectedCopy.content || buildSaleUrl(disco, selectedCopy.codigoQr)) : ''
  const imageUrl = selectedCopy?.imageUrl
    ? resolveApiUrl(selectedCopy.imageUrl)
    : (selectedCopy?.copyNumber ? api.qr.urlDescargaCopia(disco.idDisco, selectedCopy.copyNumber) : '')
  const sourceLabel = disco.procedencia === 'DISCOGS'
    ? `${disco.artista} - ${disco.album}`
    : (disco.discogsUrl || disco.codigoInterno || 'Vinyl Future')
  const usedProduct = normalizedCategory(disco) === 'USADO'

  async function handleDownload() {
    if (!selectedCopy?.copyNumber) {
      setDownloadError('No hay una copia QR válida para descargar.')
      return
    }
    setDownloading(true)
    setDownloadError('')
    try {
      const { blob, contentDisposition } = await api.qr.descargarCopia(disco.idDisco, selectedCopy.copyNumber)
      downloadBlob(blob, qrFilename(disco, selectedCopy), contentDisposition)
    } catch (err) {
      setDownloadError(err.message || 'No se pudo descargar el QR')
    } finally {
      setDownloading(false)
    }
  }

  async function handleDeleteCopy() {
    if (!copyToDelete || deletingCopy) return
    setDeletingCopy(true)
    setDeleteError('')
    try {
      const actualizado = await api.discos.eliminarCopia(disco.idDisco, copyToDelete.id)
      setCopyToDelete(null)
      onUpdated(actualizado)
    } catch (err) {
      setDeleteError(err.message || 'No se pudo eliminar la copia. No se realizó ningún cambio.')
    } finally {
      setDeletingCopy(false)
    }
  }

  return (
    <div className="fixed inset-0 z-[70] bg-black/60 backdrop-blur-sm flex items-center justify-center p-4" onClick={onClose}>
      <div className="w-full max-w-2xl max-h-[90vh] overflow-y-auto bg-white dark:bg-stone-900 rounded-2xl border border-slate-200 dark:border-stone-700 shadow-2xl" onClick={e => e.stopPropagation()}>
        <div className="sticky top-0 bg-white/95 dark:bg-stone-900/95 backdrop-blur px-6 py-4 border-b border-slate-100 dark:border-stone-800 flex items-start justify-between gap-4">
          <div>
            <h2 className="font-bold text-slate-900 dark:text-white">{disco.album}</h2>
            <p className="text-sm text-slate-500 dark:text-stone-400">{disco.artista}</p>
            <p className="text-xs text-slate-400 dark:text-stone-500 mt-1">SKU del producto: {catalogCode(disco) || '—'} · Origen del producto: {sourceLabel}</p>
          </div>
          <button type="button" onClick={onClose} className="btn-secondary px-3 py-1.5">Cerrar</button>
        </div>
        <div className="p-6 space-y-5">
          {loading && (
            <div className="border border-slate-200 dark:border-stone-700 rounded-xl p-6 text-center">
              <Spinner />
            </div>
          )}
          {error && (
            <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 text-red-600 dark:text-red-300 text-sm rounded-xl px-4 py-3">
              {error}
            </div>
          )}
          {copies.length === 0 && (
            <p className="text-sm text-slate-500 dark:text-stone-400 text-center py-8">Este disco no tiene copias físicas con QR.</p>
          )}
          {copies.length > 1 && (
            <div>
              <p className="text-[10px] uppercase tracking-wider text-slate-400 dark:text-stone-500 mb-2">Copias físicas</p>
              <div className="flex flex-wrap gap-2">
                {copies.map(copy => {
                  const key = copySelectionKey(copy)
                  const active = key === copySelectionKey(selectedCopy)
                  return (
                    <button
                      key={key}
                      type="button"
                      onClick={() => {
                        setSelectedCopyKey(key)
                        setImageError('')
                        setDownloadError('')
                      }}
                      className={`text-xs px-3 py-2 rounded-lg border font-medium transition-colors ${
                        active
                          ? 'border-[#7E9FA8] bg-[#7E9FA8] text-white'
                          : 'border-slate-200 dark:border-stone-800 bg-slate-50 dark:bg-stone-950 text-slate-600 dark:text-stone-400 hover:bg-slate-100 dark:hover:bg-stone-800'
                      }`}
                    >
                      Copia {copy.copyNumber || '—'} · {copy.estado || '—'}
                    </button>
                  )
                })}
              </div>
            </div>
          )}
          {selectedCopy && (
            <article className="border border-slate-200 dark:border-stone-700 rounded-xl p-5">
              <p className="text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-stone-400 mb-4 text-center">
                Mostrando copia {selectedCopy.copyNumber || '—'} de {copies.length}
              </p>
              <div className="bg-white p-4 rounded-xl w-fit mx-auto border border-slate-100">
                {imageUrl && !imageError ? (
                  <img
                    src={imageUrl}
                    alt={`QR ${disco.album} copia ${selectedCopy.copyNumber || ''}`}
                    className="w-64 h-64 object-contain"
                    onError={() => setImageError('No se pudo cargar la imagen QR.')}
                  />
                ) : (
                  <div className="w-64 h-64 flex items-center justify-center text-center text-sm text-slate-500">
                    {imageError || 'No hay imagen QR disponible.'}
                  </div>
                )}
              </div>
              <div className="mt-5 grid sm:grid-cols-2 gap-3 text-left">
                {[
                  ['Álbum', disco.album],
                  ['Código/SKU', catalogCode(disco)],
                  ['Copia', selectedCopy.copyNumber ? `Copia ${selectedCopy.copyNumber}` : null],
                  ['Estado', selectedCopy.estado || '—'],
                  ['Código QR', selectedCopy.codigoQr],
                  ['URL destino', saleUrl],
                ].map(([label, value]) => (
                  <div key={label} className={label === 'URL destino' || label === 'Código QR' ? 'sm:col-span-2' : ''}>
                    <p className="text-[10px] uppercase tracking-wider text-slate-400 dark:text-stone-500">{label}</p>
                    <p className="text-xs font-medium text-slate-700 dark:text-stone-300 break-all">{value || '—'}</p>
                  </div>
                ))}
              </div>
              {downloadError && (
                <p className="mt-4 text-sm text-red-600 dark:text-red-300">{downloadError}</p>
              )}
              <div className="mt-5 grid sm:grid-cols-2 gap-2">
                <button
                  type="button"
                  onClick={handleDownload}
                  disabled={downloading || Boolean(imageError)}
                  className="btn-primary w-full disabled:opacity-60 disabled:cursor-not-allowed"
                >
                  {downloading ? 'Descargando...' : 'Descargar QR'}
                </button>
                {selectedCopy.id && !String(selectedCopy.id).startsWith('legacy-') && (
                  <>
                    {selectedCopy.estado !== 'REMOVED' && (
                      <button
                        type="button"
                        onClick={async () => {
                          const nuevoEstado = selectedCopy.estado === 'VENDIDO' ? 'DISPONIBLE' : 'VENDIDO'
                          const actualizado = await api.discos.cambiarEstadoCopia(disco.idDisco, selectedCopy.id, nuevoEstado)
                          onUpdated(actualizado)
                        }}
                        className="btn-secondary w-full"
                      >
                        {selectedCopy.estado === 'VENDIDO' ? 'Marcar disponible' : 'Marcar vendida'}
                      </button>
                    )}
                    {!usedProduct && (
                      <button
                        type="button"
                        onClick={() => { setDeleteError(''); setCopyToDelete(selectedCopy) }}
                        className="btn-secondary w-full text-red-600 dark:text-red-400"
                      >
                        Eliminar copia
                      </button>
                    )}
                    {usedProduct && (
                      <p className="sm:col-span-2 rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-500 dark:bg-stone-950 dark:text-stone-400">
                        Las copias USADO se retiran desde su detalle para conservar QR e historial. La eliminación permanente no es la baja operativa normal.
                      </p>
                    )}
                  </>
                )}
              </div>
            </article>
          )}
        </div>
      </div>
      {copyToDelete && (
        <ConfirmModal
          titulo="Eliminar copia física"
          mensaje={`¿Seguro que querés eliminar la copia ${copyToDelete.copyNumber || 'seleccionada'}? El producto y las demás copias se conservarán.`}
          onConfirmar={handleDeleteCopy}
          onCancelar={() => { if (!deletingCopy) { setCopyToDelete(null); setDeleteError('') } }}
          cargando={deletingCopy}
          confirmarTexto="Eliminar copia"
          error={deleteError}
        />
      )}
    </div>
  )
}

/* Panel lateral derecho con el detalle completo del disco.
   Se abre al hacer clic en una fila. */
function CustomerAffinityModal({ disco, onClose }) {
  const [customers, setCustomers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!disco?.idDisco) return
    let cancelled = false
    api.crm.clientesRecomendados(disco.idDisco)
      .then(data => { if (!cancelled) setCustomers(data) })
      .catch(err => { if (!cancelled) setError(err.message || 'No se pudieron calcular los clientes afines') })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [disco?.idDisco])

  if (!disco) return null
  const affinityStyles = {
    ALTA: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-900/20 dark:text-emerald-400',
    MEDIA: 'bg-amber-50 text-amber-700 dark:bg-amber-900/20 dark:text-amber-400',
    BAJA: 'bg-slate-100 text-slate-600 dark:bg-stone-800 dark:text-stone-400',
  }

  return (
    <div className="fixed inset-0 z-[80] flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm" onClick={onClose}>
      <div className="max-h-[88vh] w-full max-w-2xl overflow-y-auto rounded-2xl border border-slate-200 bg-white shadow-2xl dark:border-stone-700 dark:bg-stone-900" onClick={event => event.stopPropagation()}>
        <div className="sticky top-0 z-10 flex items-start justify-between gap-4 border-b border-slate-100 bg-white/95 px-5 py-4 backdrop-blur dark:border-stone-800 dark:bg-stone-900/95">
          <div>
            <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-400 dark:text-stone-500">Clientes afines</p>
            <h2 className="font-bold text-slate-900 dark:text-white">{disco.artista} — {disco.album}</h2>
          </div>
          <button type="button" onClick={onClose} className="btn-secondary px-3 py-1.5">Cerrar</button>
        </div>
        <div className="space-y-3 p-5">
          {loading ? <div className="py-12 text-center text-sm text-slate-400">Analizando perfiles…</div> : null}
          {error ? <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-600 dark:border-red-800 dark:bg-red-900/20 dark:text-red-300">{error}</div> : null}
          {!loading && !error && customers.length === 0 ? <div className="py-12 text-center text-sm text-slate-400">No hay clientes con coincidencias suficientes.</div> : null}
          {!loading && !error ? customers.map(result => (
            <article key={result.cliente.idCliente} className="rounded-xl border border-slate-100 p-4 dark:border-stone-800">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <h3 className="font-semibold text-slate-900 dark:text-white">{result.cliente.nombre} {result.cliente.apellido}</h3>
                  <p className="text-xs text-slate-400 dark:text-stone-500">{result.cliente.instagramUsuario || result.cliente.telefono || result.cliente.email || 'Sin contacto registrado'}</p>
                </div>
                <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${affinityStyles[result.nivelAfinidad] || affinityStyles.BAJA}`}>
                  Afinidad {result.nivelAfinidad.toLowerCase()}
                </span>
              </div>
              <ul className="mt-3 space-y-1 text-xs text-slate-500 dark:text-stone-400">
                {result.razones.map(reason => <li key={reason}>• {reason}</li>)}
              </ul>
              <Link to={`/clientes/${result.cliente.idCliente}/seguimiento`} onClick={onClose}
                className="mt-3 inline-block text-xs font-semibold text-[#5C7D87] hover:underline dark:text-[#7E9FA8]">
                Ver seguimiento →
              </Link>
            </article>
          )) : null}
        </div>
      </div>
    </div>
  )
}

function SlideOver({ disco, copyState, activeSourceCode, onRetryCopies, onCerrar, onEditar, onDarBaja, onViewQr, onViewCustomers, onRequestRemoval }) {
  if (!disco) return null
  const newProduct = isNewCatalogProduct(disco)
  const fields = [
    ['Año', disco.anio],
    ['Género', disco.genero],
    ['Sello', disco.selloDiscografico],
    ['Categoría', disco.condicion],
    ...(!newProduct ? [['Condiciones físicas', copyConditionSummary(disco, copyState)]] : []),
    ['Precio compra', disco.costo ? `UYU $${Number(disco.costo).toLocaleString('es-UY')}` : null],
    [newProduct ? 'Precio venta' : 'Precios de copia', copyPriceSummary(disco, copyState)],
    ['Copias disponibles', disco.cantidadCopias ?? 0],
    ['SKU del producto', catalogCode(disco)],
  ]

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
              src={resolveApiUrl(disco.imagenUrl)}
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
              <audio controls src={resolveApiUrl(disco.previewUrl)} className="w-full h-9" />
            </div>
          )}

          <TrackPreviews disco={disco} />

          {/* Estado */}
          <EstadoBadge estado={disco.estado} />

          {/* Campos en grid */}
          <div className="grid grid-cols-2 gap-3">
            {fields.map(([label, value]) => (
              <div key={label} className="bg-slate-50 dark:bg-stone-950 border border-slate-100 dark:border-stone-800 rounded-lg px-3 py-2">
                <p className="text-xs uppercase tracking-wider text-slate-400 dark:text-stone-500 mb-0.5">{label}</p>
                <p className="text-sm font-medium text-slate-700 dark:text-stone-300">{value ?? '—'}</p>
              </div>
            ))}
          </div>

          <CatalogPhysicalCopiesSection
            key={`${disco.idDisco}:${activeSourceCode}`}
            disco={disco}
            copyState={copyState}
            activeSourceCode={activeSourceCode}
            onRetry={onRetryCopies}
            onViewQr={copy => onViewQr(disco, copy)}
            onRequestRemoval={onRequestRemoval}
          />

          {disco.observaciones && (
            <div>
              <p className="text-xs uppercase tracking-wider text-slate-400 dark:text-stone-500 mb-1">Observaciones</p>
              <p className="text-slate-600 dark:text-stone-400 text-sm italic">{disco.observaciones}</p>
            </div>
          )}
        </div>

        {/* Acciones fijas al fondo */}
        <div className="p-6 pt-0 space-y-2">
          <button type="button" onClick={() => { onViewCustomers(disco); onCerrar() }} className="btn-secondary w-full text-[#5C7D87] dark:text-[#7E9FA8]">
            Clientes afines
          </button>
          <button type="button" onClick={(e) => { e.stopPropagation(); onViewQr(disco) }} className="btn-secondary w-full">
            {physicalCopyCount(disco) > 1 ? `Ver todos los QR (${physicalCopyCount(disco)})` : 'Ver QR'}
          </button>
          <button
            onClick={() => { onEditar(disco); onCerrar() }}
            className="btn-primary w-full"
          >
            Editar disco
          </button>
          <button
            onClick={() => onDarBaja(disco)}
            className="btn-secondary w-full text-red-600 dark:text-red-400"
          >
            Eliminar definitivamente
          </button>
        </div>
      </div>
    </>
  )
}

function CatalogPreview({ disco, pinned, copyState, activeSourceCode, onRetryCopies, onUnpin, onEditar, onDarBaja, onViewQr, onViewCustomers, onRequestRemoval }) {
  const [loaded, setLoaded] = useState({ discoId: null, previews: [] })
  const previews = loaded.discoId === disco?.idDisco
    ? loaded.previews
    : (disco?.audioPreviews || [])

  useEffect(() => {
    stopAllPreviews()
    if (!disco?.idDisco) return
    let cancelled = false
    api.discos.previews.listar(disco.idDisco)
      .then(data => { if (!cancelled) setLoaded({ discoId: disco.idDisco, previews: data }) })
      .catch(() => {})
    return () => { cancelled = true }
  }, [disco?.idDisco])

  // Also stop audio when component unmounts (page leave)
  useEffect(() => () => stopAllPreviews(), [])

  if (!disco) {
    return (
      <aside className="card sticky top-24 min-h-[420px] p-6 hidden lg:flex flex-col items-center justify-center text-center">
        <div className="w-14 h-14 rounded-2xl bg-slate-100 dark:bg-stone-800 flex items-center justify-center mb-4">
          <svg className="w-7 h-7 text-slate-300 dark:text-stone-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 9l10.5-3m0 6.553v3.75a2.25 2.25 0 0 1-1.632 2.163l-1.32.377a1.803 1.803 0 1 1-.99-3.467l2.31-.66a2.25 2.25 0 0 0 1.632-2.163zm0 0V2.25L9 5.25v10.303" />
          </svg>
        </div>
        <p className="text-sm font-medium text-slate-600 dark:text-stone-300">Vista rápida</p>
        <p className="text-xs text-slate-400 dark:text-stone-500 mt-1">Pasá el cursor o seleccioná un disco para ver sus datos</p>
      </aside>
    )
  }

  const newProduct = isNewCatalogProduct(disco)
  const fields = [
    ['SKU del producto', catalogCode(disco)],
    ['Compra', disco.costo != null ? `EUR €${Number(disco.costo).toLocaleString('es-UY')}` : null],
    [newProduct ? 'Precio venta' : 'Precios de copia', copyPriceSummary(disco, copyState)],
    ['Disponibles', `${disco.cantidadCopias ?? 0} ${(disco.cantidadCopias ?? 0) === 1 ? 'copia' : 'copias'}`],
    ['Categoría', disco.condicion],
    ...(!newProduct ? [['Condiciones físicas', copyConditionSummary(disco, copyState)]] : []),
    ['Formato', disco.tipoDisco],
    ['Año', disco.anio],
    ['Sello', disco.selloDiscografico],
    ['Género', disco.genero],
    ['País', disco.pais],
  ]

  return (
    <aside className="card sticky top-24 hidden lg:block overflow-hidden">
      {disco.imagenUrl ? (
        <img src={resolveApiUrl(disco.imagenUrl)} alt={`${disco.artista} - ${disco.album}`}
          className="w-full aspect-square object-cover bg-slate-100 dark:bg-stone-800" />
      ) : (
        <div className="w-full aspect-square bg-slate-100 dark:bg-stone-800 flex items-center justify-center text-sm text-slate-400 dark:text-stone-600">
          Sin portada
        </div>
      )}
      <div className="p-5 space-y-4">
        <div>
          <div className="flex items-start justify-between gap-3">
            <div>
              <h2 className="font-bold text-slate-900 dark:text-white leading-tight">{disco.album || '—'}</h2>
              <p className="text-sm text-slate-500 dark:text-stone-400 mt-1">{disco.artista || '—'}</p>
            </div>
            <div className="flex flex-col items-end gap-2">
              <EstadoBadge estado={disco.estado} />
              {pinned && <button onClick={onUnpin} className="text-xs text-slate-400 hover:text-slate-700 dark:hover:text-white">Desfijar</button>}
            </div>
          </div>
        </div>
        <div className="grid grid-cols-2 gap-x-4 gap-y-3">
          {fields.map(([label, value]) => (
            <div key={label} className={label === 'Sello' || label === 'Género' ? 'col-span-2' : ''}>
              <p className="text-[10px] uppercase tracking-wider text-slate-400 dark:text-stone-500">{label}</p>
              <p className="text-sm font-medium text-slate-700 dark:text-stone-300 truncate" title={String(value ?? '—')}>{value ?? '—'}</p>
            </div>
          ))}
        </div>
        {(disco.notas || disco.descripcion) && (
          <p className="text-xs text-slate-500 dark:text-stone-400 line-clamp-3">{disco.notas || disco.descripcion}</p>
        )}

        <CatalogPhysicalCopiesSection
          key={`${disco.idDisco}:${activeSourceCode}`}
          disco={disco}
          copyState={copyState}
          activeSourceCode={activeSourceCode}
          onRetry={onRetryCopies}
          onViewQr={copy => onViewQr(disco, copy)}
          onRequestRemoval={onRequestRemoval}
        />

        {/* Audio previews */}
        {previews.length > 0 && (
          <div>
            <p className="text-[10px] uppercase tracking-wider text-slate-400 dark:text-stone-500 mb-2">Audio previews</p>
            <div className="space-y-1.5 max-h-52 overflow-y-auto pr-1">
              {previews.map(p => (
                <CompactPlayer
                  key={p.id}
                  audioUrl={p.audioUrl}
                  youtubeUrl={p.youtubeUrl}
                  trackName={p.trackName}
                  trackPosition={p.trackPosition}
                />
              ))}
            </div>
          </div>
        )}

        <div className="grid grid-cols-2 gap-2 pt-1">
          <button onClick={() => onEditar(disco)} className="btn-primary">Editar</button>
          <button type="button" onClick={(e) => { e.stopPropagation(); onViewQr(disco) }} className="btn-secondary">
            {physicalCopyCount(disco) > 1 ? 'Ver todos los QR' : 'Ver QR'}
          </button>
          <button type="button" onClick={() => onViewCustomers(disco)} className="btn-secondary col-span-2 text-[#5C7D87] dark:text-[#7E9FA8]">Clientes afines</button>
          <button onClick={() => onDarBaja(disco)} className="btn-secondary text-red-600 dark:text-red-400 col-span-2">Eliminar definitivamente</button>
        </div>
      </div>
    </aside>
  )
}

export default function DiscosCatalogo() {
  const [discos, setDiscos] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [busqueda, setBusqueda] = useState('')
  const [filtroEstado, setFiltroEstado] = useState('TODOS')
  const [filtroCondicion, setFiltroCondicion] = useState('TODOS')
  const [filtroImportacionDiscogs, setFiltroImportacionDiscogs] = useState('')
  const [fuentesImportacionDiscogs, setFuentesImportacionDiscogs] = useState([])
  const [discoForm, setDiscoForm] = useState(null)
  const [discoEliminar, setDiscoEliminar] = useState(null)
  const [eliminando, setEliminando] = useState(false)
  const [errorEliminacion, setErrorEliminacion] = useState('')
  const [slideOverDisco, setSlideOverDisco] = useState(null)
  const [hoveredDisco, setHoveredDisco] = useState(null)
  const [selectedDisco, setSelectedDisco] = useState(null)
  const [copyDetailsByProduct, setCopyDetailsByProduct] = useState({})
  const [copyRemovalTarget, setCopyRemovalTarget] = useState(null)
  const [removingCopy, setRemovingCopy] = useState(false)
  const [copyRemovalError, setCopyRemovalError] = useState('')
  const [qrState, setQrState] = useState({ disco: null, initialCopy: null, loading: false, error: '' })
  const [affinityDisco, setAffinityDisco] = useState(null)
  const [pagina, setPagina] = useState(1)
  const [porPagina, setPorPagina] = useState(20)
  const [sortKey, setSortKey] = useState(null)
  const [sortDirection, setSortDirection] = useState('desc')
  const [exportandoExcel, setExportandoExcel] = useState(false)
  const [excelExportado, setExcelExportado] = useState(false)
  const [errorExportacionExcel, setErrorExportacionExcel] = useState('')
  const [exportandoZip, setExportandoZip] = useState(false)
  const [errorExportacionZip, setErrorExportacionZip] = useState('')
  const [batchPorFinalizar, setBatchPorFinalizar] = useState(null)
  const [finalizandoBatch, setFinalizandoBatch] = useState(false)
  const [errorFinalizacionBatch, setErrorFinalizacionBatch] = useState('')
  const [finalizationReconciliation, setFinalizationReconciliation] = useState(null)
  const [finalizationReconciliationLoading, setFinalizationReconciliationLoading] = useState(false)
  const [porcentajeSonogramaBatch, setPorcentajeSonogramaBatch] = useState('')
  const debounceRef = useRef(null)
  const copyDetailsRequestVersionRef = useRef({})

  useEffect(() => {
    cargarTodos()
    cargarFuentesImportacionDiscogs()
    window.addEventListener(FINANCIAL_DATA_CHANGED_EVENT, cargarTodos)
    return () => window.removeEventListener(FINANCIAL_DATA_CHANGED_EVENT, cargarTodos)
  }, [])

  async function loadCopyDetails(productId, force = false) {
    if (!productId) return
    const cached = copyDetailsByProduct[productId]
    if (!force && (cached?.loading || cached?.loaded)) return
    const requestVersion = (copyDetailsRequestVersionRef.current[productId] || 0) + 1
    copyDetailsRequestVersionRef.current[productId] = requestVersion
    setCopyDetailsByProduct(prev => ({
      ...prev,
      [productId]: { ...prev[productId], loading: true, error: '', copies: prev[productId]?.copies || [] },
    }))
    try {
      const copies = await api.discos.copias(productId)
      if (copyDetailsRequestVersionRef.current[productId] !== requestVersion) return
      setCopyDetailsByProduct(prev => ({
        ...prev,
        [productId]: { loading: false, loaded: true, error: '', copies: Array.isArray(copies) ? copies : [] },
      }))
    } catch (err) {
      if (copyDetailsRequestVersionRef.current[productId] !== requestVersion) return
      setCopyDetailsByProduct(prev => ({
        ...prev,
        [productId]: {
          loading: false,
          loaded: false,
          error: err.message || 'No se pudieron cargar las copias físicas.',
          copies: prev[productId]?.copies || [],
        },
      }))
    }
  }

  function reconcileProductFromBackend(actualizado) {
    setDiscos(prev => prev.map(item => item.idDisco === actualizado.idDisco ? actualizado : item))
    setSelectedDisco(prev => prev?.idDisco === actualizado.idDisco ? actualizado : prev)
    setHoveredDisco(prev => prev?.idDisco === actualizado.idDisco ? actualizado : prev)
    setSlideOverDisco(prev => prev?.idDisco === actualizado.idDisco ? actualizado : prev)
    setQrState(prev => prev.disco?.idDisco === actualizado.idDisco
      ? { ...prev, disco: actualizado, loading: false, error: '' }
      : prev)
  }

  function requestCopyRemoval(disco, copy) {
    if (normalizedCategory(disco) !== 'USADO' || copy?.estado !== 'DISPONIBLE') return
    setCopyRemovalError('')
    setCopyRemovalTarget({ disco, copy })
  }

  async function handleCopyRemoval({ reason, note }) {
    if (!copyRemovalTarget || removingCopy) return
    const { disco, copy } = copyRemovalTarget
    setRemovingCopy(true)
    setCopyRemovalError('')
    try {
      const actualizado = await api.discos.retirarCopia(
        disco.idDisco,
        copy.id,
        { reason, note },
      )
      reconcileProductFromBackend(actualizado)
      await loadCopyDetails(actualizado.idDisco, true)
      setCopyRemovalTarget(null)
    } catch (err) {
      setCopyRemovalError(err.message || 'No se pudo retirar la copia. No se realizó ningún cambio.')
    } finally {
      setRemovingCopy(false)
    }
  }

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

  async function cargarFuentesImportacionDiscogs() {
    try {
      const sources = await discoService.listarFuentesImportacionDiscogs()
      setFuentesImportacionDiscogs(deduplicateManualSources(sources))
    } catch {
      // Preserve the last known selector state when a refresh fails.
    }
  }

  async function cambiarFiltroImportacionDiscogs(event) {
    const source = event.target.value
    clearTimeout(debounceRef.current)
    setFiltroImportacionDiscogs(source)
    setExcelExportado(false)
    setErrorExportacionExcel('')
    setErrorExportacionZip('')
    setBatchPorFinalizar(null)
    setErrorFinalizacionBatch('')
    setPorcentajeSonogramaBatch('')
    setPagina(1)
    setLoading(true)
    setError('')
    try {
      const data = source
        ? await discoService.getPorFuenteImportacionDiscogs(source)
        : await discoService.getAll()
      setDiscos(data)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  async function exportarSourceExcel() {
    if (exportandoExcel || !batchManualSeleccionado) return
    const source = normalizedManualCustomerCode(batchManualSeleccionado)
    if (!source) return
    setExportandoExcel(true)
    setErrorExportacionExcel('')
    try {
      const result = await api.importaciones.discogsManualSourceExcel(source)
      downloadBlob(result.blob, result.filename || `discogs-manual-${source}.xlsx`, result.contentDisposition)
      setExcelExportado(true)
    } catch (err) {
      setErrorExportacionExcel(err.message || 'No se pudo generar el Excel de la fuente Discogs.')
    } finally {
      setExportandoExcel(false)
    }
  }

  async function descargarBatchZip() {
    if (exportandoZip || !batchManualSeleccionado) return
    const batchId = batchManualSeleccionado.batchId
      || String(batchManualSeleccionado.key || '').replace(/^manual:/i, '')
    setExportandoZip(true)
    setErrorExportacionZip('')
    try {
      const result = await api.importaciones.discogsManualBatchZip(batchId)
      downloadBlob(result.blob, result.filename || `discogs-manual-${batchId}.zip`, result.contentDisposition)
    } catch (err) {
      setErrorExportacionZip(err.message || 'No se pudo generar el ZIP del batch Discogs.')
    } finally {
      setExportandoZip(false)
    }
  }

  async function finalizarBatch() {
    if (finalizandoBatch || !batchPorFinalizar) return
    if (!porcentajeSonogramaBatch) {
      setErrorFinalizacionBatch('Seleccioná el porcentaje Sonograma para finalizar el batch.')
      return
    }
    const batchId = batchPorFinalizar.batchId
      || String(batchPorFinalizar.key || '').replace(/^manual:/i, '')
    setFinalizandoBatch(true)
    setErrorFinalizacionBatch('')
    try {
      const finalized = await api.importaciones.discogsManualBatchFinalize(
        batchId,
        Number(porcentajeSonogramaBatch),
        Boolean(finalizationReconciliation
          && finalizationReconciliation.reconciliationStatus !== 'MATCHED'),
      )
      setFuentesImportacionDiscogs(prev => prev.map(source => (
        source.batchId === batchPorFinalizar.batchId
          ? { ...source, status: finalized.status || 'FINALIZED', label: null, porcentajeSonograma: finalized.porcentajeSonograma }
          : source
      )))
      setBatchPorFinalizar(null)
      setPorcentajeSonogramaBatch('')
      setFinalizationReconciliation(null)
      await cargarFuentesImportacionDiscogs()
    } catch (err) {
      if (err.code === 'MANUAL_DISCOGS_RECONCILIATION_CONFIRMATION_REQUIRED') {
        const summary = err.data?.reconciliation || null
        setFinalizationReconciliation(summary)
      }
      setErrorFinalizacionBatch(err.message || 'No se pudo finalizar el batch Discogs.')
    } finally {
      setFinalizandoBatch(false)
    }
  }

  async function openBatchFinalization(batch) {
    setErrorFinalizacionBatch('')
    setPorcentajeSonogramaBatch('')
    setFinalizationReconciliationLoading(true)
    try {
      const summary = await api.importaciones.discogsManualSourceReconciliation(batch.customerCode)
      setFinalizationReconciliation(summary)
      setBatchPorFinalizar(batch)
    } catch (err) {
      setErrorFinalizacionBatch(err.message || 'No se pudo cargar la conciliación de la fuente.')
    } finally {
      setFinalizationReconciliationLoading(false)
    }
  }

  function onBusquedaChange(e) {
    const q = e.target.value
    setBusqueda(q)
    setPagina(1)
    if (filtroImportacionDiscogs) return
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

  function cambiarFiltroCondicion(condicion) {
    setFiltroCondicion(condicion)
    setPagina(1)
  }

  function cambiarOrden(key) {
    setPagina(1)
    setSortKey(prevKey => {
      if (prevKey === key) {
        setSortDirection(prevDirection => prevDirection === 'desc' ? 'asc' : 'desc')
        return prevKey
      }
      setSortDirection('desc')
      return key
    })
  }

  async function handleGuardar(payload) {
    if (discoForm && discoForm.idDisco) {
      const actualizado = await discoService.actualizar(discoForm.idDisco, payload)
      setDiscos(prev => prev.map(d => d.idDisco === discoForm.idDisco ? actualizado : d))
      setSelectedDisco(prev => prev?.idDisco === actualizado.idDisco ? actualizado : prev)
      if (copyDetailsByProduct[actualizado.idDisco]) loadCopyDetails(actualizado.idDisco, true)
    } else {
      const nuevo = await discoService.crear(payload)
      setDiscos(prev => [nuevo, ...prev])
    }
    setDiscoForm(null)
  }

  async function handleEliminar() {
    if (eliminando || !discoEliminar) return
    const idDisco = discoEliminar.idDisco
    setEliminando(true)
    setErrorEliminacion('')
    try {
      await discoService.eliminar(idDisco)
      setDiscos(prev => prev.filter(d => d.idDisco !== idDisco))
      setSelectedDisco(prev => prev?.idDisco === idDisco ? null : prev)
      setHoveredDisco(prev => prev?.idDisco === idDisco ? null : prev)
      setSlideOverDisco(prev => prev?.idDisco === idDisco ? null : prev)
      setDiscoEliminar(null)
      try {
        const query = busqueda.trim()
        const actualizados = filtroImportacionDiscogs
          ? await discoService.getPorFuenteImportacionDiscogs(filtroImportacionDiscogs)
          : query ? await discoService.buscar(query) : await discoService.getAll()
        setDiscos(actualizados)
      } catch (refreshError) {
        setError(`El disco fue eliminado, pero no se pudo actualizar el catálogo: ${refreshError.message}`)
      }
    } catch (err) {
      setErrorEliminacion(err.message || 'No se pudo eliminar el disco. No se realizó ningún cambio.')
    } finally {
      setEliminando(false)
    }
  }

  function solicitarEliminacion(disco) {
    setErrorEliminacion('')
    setDiscoEliminar(disco)
  }

  async function abrirQr(disco, initialCopy = null) {
    setQrState({ disco, initialCopy, loading: true, error: '' })
    try {
      const actualizado = await api.discos.porId(disco.idDisco)
      setDiscos(prev => prev.map(d => d.idDisco === actualizado.idDisco ? actualizado : d))
      setSelectedDisco(prev => prev?.idDisco === actualizado.idDisco ? actualizado : prev)
      setQrState({ disco: actualizado, initialCopy, loading: false, error: '' })
    } catch (err) {
      setQrState({
        disco,
        initialCopy,
        loading: false,
        error: err.message || 'No se pudo cargar la información actualizada del QR.',
      })
    }
  }

  const discosFiltrados = discos.filter(d => {
    const coincideEstado = filtroEstado === 'TODOS' || d.estado === filtroEstado
    const coincideCondicion = filtroCondicion === 'TODOS' || normalizedCategory(d) === filtroCondicion
    const query = busqueda.trim().toLowerCase()
    const coincideBusqueda = !query || [d.artista, d.album, d.codigoInterno, d.manualBatchCustomerCode]
      .some(value => String(value || '').toLowerCase().includes(query))
    return coincideEstado && coincideCondicion && coincideBusqueda
  })
  const discosOrdenados = sortKey
    ? discosFiltrados
        .map((disco, index) => ({ disco, index }))
        .sort((a, b) => {
          const aValue = parseSortValue(a.disco, sortKey)
          const bValue = parseSortValue(b.disco, sortKey)
          if (aValue == null && bValue == null) return a.index - b.index
          if (aValue == null) return 1
          if (bValue == null) return -1
          if (aValue === bValue) return a.index - b.index
          return sortDirection === 'asc' ? aValue - bValue : bValue - aValue
        })
        .map(({ disco }) => disco)
    : discosFiltrados
  const discosPagina = discosOrdenados.slice((pagina - 1) * porPagina, pagina * porPagina)
  const fuenteSeleccionada = fuentesImportacionDiscogs.find(source => source.key === filtroImportacionDiscogs)
  const batchManualSeleccionado = isManualSource(fuenteSeleccionada) ? fuenteSeleccionada : null
  const activeSourceCode = batchManualSeleccionado ? normalizedManualCustomerCode(batchManualSeleccionado) : ''
  const previewDisco = selectedDisco || hoveredDisco
  const previewCopyState = previewDisco
    ? (copyDetailsByProduct[previewDisco.idDisco] || { loading: true, loaded: false, error: '', copies: [] })
    : null
  const hayFiltro = filtroEstado !== 'TODOS' || filtroCondicion !== 'TODOS'
    || filtroImportacionDiscogs !== '' || busqueda.trim() !== ''

  return (
    <div className="max-w-[1500px] mx-auto px-4 sm:px-6 py-6 space-y-5">

      {/* Header */}
      <div className="flex items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-900 dark:text-white">Catálogo de discos</h1>
          <p className="text-slate-400 dark:text-stone-500 text-sm mt-0.5">
            {!loading && `${discosFiltrados.length} ${discosFiltrados.length === 1 ? 'disco' : 'discos'} mostrados`}
          </p>
        </div>
      </div>

      {/* Barra de búsqueda (ancho completo) + filtros de estado */}
      <div className="flex flex-col lg:flex-row lg:items-start gap-3">
        <div className="relative w-full lg:max-w-md">
          <svg className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 dark:text-stone-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="m21 21-5.197-5.197m0 0A7.5 7.5 0 1 0 5.196 5.196a7.5 7.5 0 0 0 10.607 10.607Z" />
          </svg>
          <input
            value={busqueda}
            onChange={onBusquedaChange}
            placeholder="Buscar disco, artista o código..."
            className="input pl-9"
          />
        </div>
        <label className="flex flex-col gap-1 text-xs text-slate-500 dark:text-stone-400 min-w-[260px]">
          Importación Discogs
          <select
            aria-label="Importación Discogs"
            value={filtroImportacionDiscogs}
            onChange={cambiarFiltroImportacionDiscogs}
            className="input py-2"
          >
            <option value="">Todas las importaciones</option>
            {fuentesImportacionDiscogs.map(source => (
              <option key={source.key} value={source.key}>
                {isManualSource(source)
                  ? manualSourceLabel(source)
                  : `${source.label} (${source.productos} productos)`}
              </option>
            ))}
          </select>
        </label>
        <div className="flex gap-2 flex-wrap">
          {FILTROS.map(estado => (
            <button
              key={estado}
              onClick={() => cambiarFiltro(estado)}
              className={`text-xs px-3 py-2 rounded-full border font-medium transition-colors ${
                filtroEstado === estado
                  ? 'border-[#7E9FA8] bg-[#7E9FA8] text-white'
                  : 'border-slate-200 dark:border-stone-800 bg-slate-50 dark:bg-stone-900 text-slate-600 dark:text-stone-400 hover:bg-slate-100 dark:hover:bg-stone-800'
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
        <div className="flex gap-2 flex-wrap lg:ml-2 lg:pl-4 lg:border-l lg:border-slate-200 lg:dark:border-stone-800">
          {FILTROS_CONDICION.map(condicion => (
            <button
              key={condicion.value}
              type="button"
              onClick={() => cambiarFiltroCondicion(condicion.value)}
              className={`text-xs px-3 py-2 rounded-full border font-medium transition-colors ${
                filtroCondicion === condicion.value
                  ? 'border-[#7E9FA8] bg-[#7E9FA8] text-white'
                  : 'border-slate-200 dark:border-stone-800 bg-slate-50 dark:bg-stone-900 text-slate-600 dark:text-stone-400 hover:bg-slate-100 dark:hover:bg-stone-800'
              }`}
            >
              {condicion.label}
              {condicion.value !== 'TODOS' && (
                <span className="ml-1.5 opacity-70">
                  {discos.filter(d => normalizedCategory(d) === condicion.value).length}
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

      {batchManualSeleccionado && (
        <div
          data-testid="manual-batch-summary"
          className="card px-4 py-3 text-sm text-slate-700 dark:text-stone-300 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3"
        >
          <div>
            <p>{manualSourceLabel(batchManualSeleccionado)}</p>
            {exportandoExcel && (
              <div data-testid="manual-batch-export-progress" role="status" className="mt-2 flex items-center gap-2 text-xs text-slate-500 dark:text-stone-400">
                <span className="h-1.5 w-28 overflow-hidden rounded-full bg-slate-200 dark:bg-stone-800">
                  <span className="block h-full w-1/2 animate-pulse rounded-full bg-[#7E9FA8]" />
                </span>
                Preparando Excel… Generando archivo… Descargando…
              </div>
            )}
            {batchManualSeleccionado.status === 'FINALIZED' && batchManualSeleccionado.porcentajeSonograma != null && (
              <p className="mt-1 text-xs text-slate-500 dark:text-stone-400">
                Porcentaje Sonograma: {batchManualSeleccionado.porcentajeSonograma}%
              </p>
            )}
            {errorExportacionExcel && (
              <p role="alert" className="mt-2 text-xs text-red-600 dark:text-red-400">{errorExportacionExcel}</p>
            )}
            {exportandoZip && (
              <div data-testid="manual-batch-zip-progress" role="status" className="mt-2 text-xs text-slate-500 dark:text-stone-400">
                Preparando ZIP… Generando archivo… Descargando…
              </div>
            )}
            {errorExportacionZip && (
              <p role="alert" className="mt-2 text-xs text-red-600 dark:text-red-400">{errorExportacionZip}</p>
            )}
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={exportarSourceExcel}
              disabled={exportandoExcel}
              className="btn-secondary whitespace-nowrap"
            >
              {exportandoExcel ? 'Generando Excel…' : excelExportado ? 'Descargar Excel' : 'Exportar Excel'}
            </button>
            <button
              type="button"
              onClick={descargarBatchZip}
              disabled={exportandoZip}
              className="btn-primary whitespace-nowrap px-5"
            >
              {exportandoZip ? 'Generando ZIP…' : 'Descargar ZIP'}
            </button>
            {batchManualSeleccionado.status === 'OPEN' && (
              <button
                type="button"
                onClick={() => openBatchFinalization(batchManualSeleccionado)}
                disabled={finalizandoBatch || finalizationReconciliationLoading}
                className="btn-secondary whitespace-nowrap border-[#B8975E] text-[#8a6c32] dark:text-[#D6B86A]"
              >
                {finalizationReconciliationLoading ? 'Cargando conciliación…' : 'Finalizar importación'}
              </button>
            )}
          </div>
        </div>
      )}

      {/* Tabla principal + vista rápida estable en escritorio */}
      <div
        className="grid grid-cols-1 lg:grid-cols-[minmax(0,1fr)_320px] gap-5 items-start"
        onMouseLeave={() => { if (!selectedDisco) setHoveredDisco(null) }}
      >
      <div className="card min-w-0 overflow-hidden">
        {loading ? (
          <Spinner />
        ) : discosFiltrados.length === 0 ? (
          <EmptyState hayFiltro={hayFiltro} />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[1040px] table-fixed text-sm">
                <colgroup>
                  <col className="w-[28%]" />
                  <col className="w-[10%]" />
                  <col className="w-[11%]" />
                  <col className="w-[14%]" />
                  <col className="w-[12%]" />
                  <col className="w-[25%]" />
                </colgroup>
                <thead>
                  <tr className="border-b border-slate-100 dark:border-stone-800">
                    <th className="text-left pl-5 pr-3 py-3 text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider">Artista / Álbum</th>
                    <th className="text-left px-3 py-3 text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider hidden sm:table-cell">Condición</th>
                    <th className="text-left px-3 py-3">
                      <SortHeader
                        label="Precio"
                        sortKey="price"
                        activeKey={sortKey}
                        direction={sortDirection}
                        onSort={cambiarOrden}
                      />
                    </th>
                    <th className="text-left px-3 py-3 hidden md:table-cell">
                      <SortHeader
                        label="Fecha importación"
                        sortKey="importDate"
                        activeKey={sortKey}
                        direction={sortDirection}
                        onSort={cambiarOrden}
                      />
                    </th>
                    <th className="text-left px-3 py-3 text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider">Estado</th>
                    <th className="pl-3 pr-5 py-3 text-right text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider whitespace-nowrap">Acciones</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-stone-800/60">
                  {discosPagina.map(d => (
                    <tr
                      key={d.idDisco}
                      tabIndex={0}
                      onMouseEnter={() => {
                        setHoveredDisco(d)
                        loadCopyDetails(d.idDisco)
                      }}
                      onFocus={() => {
                        if (!selectedDisco) setHoveredDisco(d)
                        loadCopyDetails(d.idDisco)
                      }}
                      onClick={() => {
                        loadCopyDetails(d.idDisco)
                        if (window.matchMedia('(max-width: 1023px)').matches) {
                          setSlideOverDisco(d)
                        } else {
                          setSelectedDisco(d)
                          setHoveredDisco(null)
                        }
                      }}
                      className={`hover:bg-slate-50 dark:hover:bg-stone-900/40 transition-colors cursor-pointer ${selectedDisco?.idDisco === d.idDisco ? 'bg-[#7E9FA8]/10' : ''}`}
                    >
                      <td className="pl-5 pr-3 py-3.5 align-middle">
                        <div className="flex min-w-0 items-center gap-3">
                          {d.imagenUrl ? (
                            <img
                              src={resolveApiUrl(d.imagenUrl)}
                              alt={`${d.artista} - ${d.album}`}
                              className="w-10 h-10 rounded-lg object-cover flex-shrink-0 bg-slate-100 dark:bg-stone-800"
                            />
                          ) : (
                            <div className="w-10 h-10 rounded-lg bg-slate-100 dark:bg-stone-800 flex-shrink-0" />
                          )}
                          <div className="min-w-0">
                            <div className="font-semibold leading-5 text-slate-900 dark:text-white break-words">{d.artista}</div>
                            <div className="text-slate-500 dark:text-stone-400 text-xs leading-4 mt-0.5 break-words">
                              <span>{d.album}</span>
                              {d.anio ? <span className="ml-1.5 whitespace-nowrap text-slate-400 dark:text-stone-600">· {d.anio}</span> : null}
                            </div>
                          </div>
                        </div>
                      </td>
                      <td className="px-3 py-3.5 align-middle text-slate-600 dark:text-stone-400 hidden sm:table-cell">
                        {copyConditionSummary(d, copyDetailsByProduct[d.idDisco])}
                      </td>
                      <td className="px-3 py-3.5 align-middle font-semibold text-slate-900 dark:text-white tabular-nums">
                        {copyPriceSummary(d, copyDetailsByProduct[d.idDisco])}
                      </td>
                      <td className="px-3 py-3.5 align-middle text-xs text-slate-500 dark:text-stone-400 tabular-nums whitespace-nowrap hidden md:table-cell">
                        {formatImportDate(d.fechaActualizacion || d.fechaIngreso) || <span className="text-slate-400 dark:text-stone-600">—</span>}
                      </td>
                      <td className="px-3 py-3.5 align-middle" onClick={e => e.stopPropagation()}>
                        <select
                          value={d.estado}
                          onChange={async (e) => {
                            const nuevoEstado = e.target.value
                            try {
                              const actualizado = await discoService.cambiarEstado(d.idDisco, nuevoEstado)
                              setDiscos(prev => prev.map(x => x.idDisco === d.idDisco ? actualizado : x))
                              setSelectedDisco(prev => prev?.idDisco === d.idDisco ? actualizado : prev)
                              if (copyDetailsByProduct[d.idDisco]) loadCopyDetails(d.idDisco, true)
                            } catch (err) {
                              alert('Error al cambiar estado: ' + err.message)
                            }
                          }}
                          className="w-full min-w-[96px] text-xs rounded-lg border border-slate-200 dark:border-stone-700 bg-white dark:bg-stone-900 text-slate-700 dark:text-stone-300 px-2 py-1 cursor-pointer"
                        >
                          <option value="DISPONIBLE">Disponible</option>
                          <option value="RESERVADO">Reservado</option>
                          <option value="SIN_STOCK">Sin stock</option>
                          <option value="VENDIDO">Vendido</option>
                        </select>
                      </td>
                      {/* stopPropagation para que los botones no abran el slide-over */}
                      <td className="pl-3 pr-5 py-3.5 align-middle text-right" onClick={e => e.stopPropagation()}>
                        <div className="flex items-center justify-end gap-2 whitespace-nowrap">
                          <div className="inline-flex h-7 flex-shrink-0 items-center overflow-hidden rounded-lg border border-slate-200 dark:border-stone-700 text-xs">
                            <button
                              type="button"
                              aria-label={normalizedCategory(d) === 'USADO'
                                ? 'Retirá una copia USADO exacta desde el detalle'
                                : 'Disminuir cantidad de copias'}
                              title={normalizedCategory(d) === 'USADO'
                                ? 'Para conservar QR e historial, retirala desde el detalle de la copia.'
                                : undefined}
                              disabled={normalizedCategory(d) === 'USADO'}
                              onClick={async () => {
                                const nuevaCantidad = Math.max(0, (d.cantidadCopias ?? 1) - 1)
                                try {
                                  const actualizado = await discoService.actualizarCopias(d.idDisco, nuevaCantidad)
                                  setDiscos(prev => prev.map(x => x.idDisco === d.idDisco ? actualizado : x))
                                  setSelectedDisco(prev => prev?.idDisco === d.idDisco ? actualizado : prev)
                                  if (copyDetailsByProduct[d.idDisco]) loadCopyDetails(d.idDisco, true)
                                } catch (err) { alert(err.message) }
                              }}
                              className="w-7 h-7 bg-slate-100 dark:bg-stone-800 hover:bg-slate-200 dark:hover:bg-stone-700 text-slate-600 dark:text-stone-400 flex items-center justify-center font-bold transition-colors disabled:cursor-not-allowed disabled:opacity-40"
                            >−</button>
                            <span className="min-w-7 px-1 text-center font-mono tabular-nums text-slate-700 dark:text-stone-300">
                              {d.cantidadCopias ?? 1}
                            </span>
                            <button
                              onClick={async () => {
                                const nuevaCantidad = (d.cantidadCopias ?? 1) + 1
                                try {
                                  const actualizado = await discoService.actualizarCopias(d.idDisco, nuevaCantidad)
                                  setDiscos(prev => prev.map(x => x.idDisco === d.idDisco ? actualizado : x))
                                  setSelectedDisco(prev => prev?.idDisco === d.idDisco ? actualizado : prev)
                                  if (copyDetailsByProduct[d.idDisco]) loadCopyDetails(d.idDisco, true)
                                } catch (err) { alert(err.message) }
                              }}
                              className="w-7 h-7 bg-slate-100 dark:bg-stone-800 hover:bg-slate-200 dark:hover:bg-stone-700 text-slate-600 dark:text-stone-400 flex items-center justify-center font-bold transition-colors"
                            >+</button>
                          </div>
                          <button
                            onClick={() => setDiscoForm(d)}
                            className="flex-shrink-0 whitespace-nowrap text-xs bg-slate-100 dark:bg-stone-800 hover:bg-slate-200 dark:hover:bg-stone-700 text-slate-600 dark:text-stone-400 px-2.5 py-1.5 rounded-lg transition-colors font-medium"
                          >
                            Editar
                          </button>
                          <button
                            type="button"
                            onClick={() => abrirQr(d)}
                            className="flex-shrink-0 whitespace-nowrap text-xs bg-slate-100 dark:bg-stone-800 hover:bg-slate-200 dark:hover:bg-stone-700 text-slate-600 dark:text-stone-400 px-2.5 py-1.5 rounded-lg transition-colors font-medium"
                          >
                            Ver QR
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
      <CatalogPreview
        disco={previewDisco}
        pinned={Boolean(selectedDisco)}
        copyState={previewCopyState}
        activeSourceCode={activeSourceCode}
        onRetryCopies={() => previewDisco && loadCopyDetails(previewDisco.idDisco, true)}
        onUnpin={() => setSelectedDisco(null)}
        onEditar={setDiscoForm}
        onDarBaja={solicitarEliminacion}
        onViewQr={abrirQr}
        onViewCustomers={setAffinityDisco}
        onRequestRemoval={requestCopyRemoval}
      />
      </div>

      {/* Slide-over de detalle al hacer clic en una fila */}
      <SlideOver
        disco={slideOverDisco}
        copyState={slideOverDisco ? (copyDetailsByProduct[slideOverDisco.idDisco] || { loading: true, error: '', copies: [] }) : null}
        activeSourceCode={activeSourceCode}
        onRetryCopies={() => slideOverDisco && loadCopyDetails(slideOverDisco.idDisco, true)}
        onCerrar={() => setSlideOverDisco(null)}
        onEditar={d => { setDiscoForm(d); setSlideOverDisco(null) }}
        onDarBaja={solicitarEliminacion}
        onViewQr={(d, copy) => { abrirQr(d, copy); setSlideOverDisco(null) }}
        onViewCustomers={setAffinityDisco}
        onRequestRemoval={requestCopyRemoval}
      />

      <CustomerAffinityModal key={affinityDisco?.idDisco || 'sin-disco'} disco={affinityDisco} onClose={() => setAffinityDisco(null)} />

      <QrModal
        key={`${qrState.disco?.idDisco || 'qr-modal'}:${copySelectionKey(qrState.initialCopy)}`}
        disco={qrState.disco}
        initialCopy={qrState.initialCopy}
        loading={qrState.loading}
        error={qrState.error}
        onClose={() => setQrState({ disco: null, initialCopy: null, loading: false, error: '' })}
        onUpdated={(actualizado) => {
          setDiscos(prev => prev.map(item => item.idDisco === actualizado.idDisco ? actualizado : item))
          setSelectedDisco(prev => prev?.idDisco === actualizado.idDisco ? actualizado : prev)
          setQrState(prev => ({ disco: actualizado, initialCopy: prev.initialCopy, loading: false, error: '' }))
          loadCopyDetails(actualizado.idDisco, true)
        }}
      />

      <RetainedCopyRemovalModal
        key={copyRemovalTarget ? `${copyRemovalTarget.disco.idDisco}:${copyRemovalTarget.copy.id}` : 'no-copy-removal'}
        disco={copyRemovalTarget?.disco}
        copy={copyRemovalTarget?.copy}
        loading={removingCopy}
        error={copyRemovalError}
        onConfirm={handleCopyRemoval}
        onCancel={() => {
          if (removingCopy) return
          setCopyRemovalTarget(null)
          setCopyRemovalError('')
        }}
      />

      {/* Formulario de edición / creación */}
      {discoForm !== null && (
        <DiscoForm
          disco={discoForm || null}
          onGuardar={handleGuardar}
          onCancelar={() => setDiscoForm(null)}
        />
      )}

      {/* Confirmación de eliminación permanente */}
      {discoEliminar && (
        <ConfirmModal
          titulo="Eliminar disco definitivamente"
          mensaje={`¿Seguro que querés eliminar definitivamente "${discoEliminar.artista} – ${discoEliminar.album}" del Catálogo? Esta acción no se puede deshacer. El historial de ventas y contabilidad se conservará.`}
          onConfirmar={handleEliminar}
          onCancelar={() => { setDiscoEliminar(null); setErrorEliminacion('') }}
          cargando={eliminando}
          confirmarTexto="Eliminar definitivamente"
          error={errorEliminacion}
        />
      )}

      {batchPorFinalizar && (
        <ConfirmModal
          titulo="Finalizar importación Discogs"
          mensaje="¿Finalizar esta importación? Ya no se podrán agregar nuevas copias a este lote. El Excel y el ZIP seguirán disponibles, y una nueva importación con este código creará un lote nuevo."
          onConfirmar={finalizarBatch}
          onCancelar={() => { if (!finalizandoBatch) { setBatchPorFinalizar(null); setErrorFinalizacionBatch(''); setFinalizationReconciliation(null) } }}
          cargando={finalizandoBatch}
          cargandoTexto="Finalizando…"
          confirmarTexto={finalizationReconciliation?.reconciliationStatus !== 'MATCHED' ? 'Finalizar de todos modos' : 'Finalizar importación'}
          confirmarClassName="bg-[#B8975E] hover:bg-[#9f814c]"
          confirmarDeshabilitado={!porcentajeSonogramaBatch}
          contenido={(<>
            {finalizationReconciliation && (
              <div aria-label="Resumen de conciliación" className="mb-4 rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs text-amber-900 dark:border-amber-900 dark:bg-amber-950/30 dark:text-amber-200">
                <p className="font-semibold">Fuente {finalizationReconciliation.normalizedSourceCustomerCode}</p>
                <p>Esperadas: {finalizationReconciliation.expectedCopyCount ?? 'No definida'}</p>
                <p>Registradas: {finalizationReconciliation.provablePhysicalCopyCount} copias físicas</p>
                <p>Disponibles: {finalizationReconciliation.availableCopyCount} · Vendidas: {finalizationReconciliation.soldCopyCount} · Retiradas: {finalizationReconciliation.removedCopyCount}</p>
                <p>Releases distintos: {finalizationReconciliation.distinctReleaseCount}</p>
                <p>Pendientes: {finalizationReconciliation.pendingOperationCount}</p>
                <p className="mt-1 font-medium">{reconciliationDifferenceText(finalizationReconciliation)}</p>
              </div>
            )}
            <label className="block text-sm text-slate-600 dark:text-white/80 mb-6">
              Porcentaje Sonograma
              <select
                className="input w-full mt-2"
                value={porcentajeSonogramaBatch}
                onChange={event => {
                  setPorcentajeSonogramaBatch(event.target.value)
                  setErrorFinalizacionBatch('')
                }}
                aria-label="Porcentaje Sonograma"
              >
                <option value="">Seleccionar porcentaje</option>
                {[10, 15, 20, 25, 30, 35, 40, 45].map(value => (
                  <option key={value} value={value}>{value}%</option>
                ))}
              </select>
            </label>
          </>)}
          error={errorFinalizacionBatch}
        />
      )}
    </div>
  )
}

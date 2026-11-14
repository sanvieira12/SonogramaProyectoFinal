import { CANALES_VENTA } from '../utils/canalesVenta'

const emptyCounts = Object.fromEntries(Object.keys(CANALES_VENTA).map(canal => [canal, 0]))

// The ledger API already applies the selected dates and search filters.
export function salesChannelStats(movements) {
  const counts = { ...emptyCounts, unknown: 0 }
  const seen = new Set()
  for (const sale of movements) {
    if (sale.tipoMovimiento !== 'VENTA' || sale.estado !== 'COMPLETADA'
      || sale.idVenta == null || seen.has(sale.idVenta)) continue
    seen.add(sale.idVenta)
    if (Object.hasOwn(CANALES_VENTA, sale.canalVenta)) counts[sale.canalVenta]++
    else counts.unknown++
  }
  return counts
}

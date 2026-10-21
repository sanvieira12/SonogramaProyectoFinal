// The ledger API already applies the selected dates and search filters.
export function salesChannelStats(movements) {
  const counts = { local: 0, instagram: 0, unknown: 0 }
  const seen = new Set()
  for (const sale of movements) {
    if (sale.tipoMovimiento !== 'VENTA' || sale.estado !== 'COMPLETADA'
      || sale.idVenta == null || seen.has(sale.idVenta)) continue
    seen.add(sale.idVenta)
    if (sale.canalVenta === 'LOCAL') counts.local++
    else if (sale.canalVenta === 'INSTAGRAM') counts.instagram++
    else counts.unknown++
  }
  return counts
}

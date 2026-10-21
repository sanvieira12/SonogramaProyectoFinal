function quantity(item) {
  const value = Number(item?.cantidad)
  return Number.isInteger(value) && value > 0 ? value : 0
}

function unitPrice(item) {
  const value = Number(item?.precioUnitario)
  return Number.isFinite(value) && value > 0 ? value : 0
}

function lineTotal(item) {
  return Number((quantity(item) * unitPrice(item)).toFixed(2))
}

export function transactionTotal(items = []) {
  return Number(items.reduce((sum, item) => sum + lineTotal(item), 0).toFixed(2))
}

export function mergePreVentaItem(items, incoming) {
  const match = incoming.idDisco && items.find(item =>
    String(item.idDisco) === String(incoming.idDisco)
    && unitPrice(item) === unitPrice(incoming)
    && String(item.notas || '').trim() === String(incoming.notas || '').trim())
  if (!match) return [...items, incoming]
  return items.map(item => item.uid === match.uid
    ? { ...item, cantidad: String(quantity(item) + quantity(incoming)) }
    : item)
}

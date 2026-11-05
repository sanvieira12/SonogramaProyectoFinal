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

export function groupPreVentaTransactionsByClient(transactions = []) {
  const groups = new Map()
  for (const transaction of transactions) {
    const key = String(transaction.idCliente)
    if (!groups.has(key)) {
      groups.set(key, {
        idCliente: transaction.idCliente,
        clienteNombre: transaction.clienteNombre,
        transactions: [],
      })
    }
    groups.get(key).transactions.push(transaction)
  }

  return [...groups.values()].map(group => {
    const total = group.transactions.reduce((sum, tx) => sum + Number(tx.total || 0), 0)
    const pagado = group.transactions.reduce((sum, tx) => sum + Number(tx.pagado || 0), 0)
    const pendiente = group.transactions.reduce((sum, tx) => sum + Number(tx.pendiente || 0), 0)
    const items = group.transactions.flatMap(tx => (tx.items || []).map(item => ({
      ...item,
      idPreVentaTransaccion: tx.idPreVentaTransaccion,
    })))
    return {
      ...group,
      fecha: group.transactions[0]?.fecha,
      primaryTransactionId: group.transactions[0]?.idPreVentaTransaccion,
      items,
      cantidadItems: items.length,
      cantidadUnidades: items.reduce((sum, item) => sum + Number(item.cantidad || 0), 0),
      total: Number(total.toFixed(2)),
      pagado: Number(pagado.toFixed(2)),
      pendiente: Number(pendiente.toFixed(2)),
      estadoPago: pagado === 0 ? 'PENDIENTE' : pendiente === 0 ? 'PAGADA' : 'PARCIALMENTE_PAGADA',
    }
  })
}

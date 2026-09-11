export function groupPreVentasByClient(preVentas = []) {
  const groups = new Map()

  preVentas.forEach(preVenta => {
    const key = String(preVenta.idCliente)
    if (!groups.has(key)) {
      groups.set(key, {
        idCliente: preVenta.idCliente,
        clienteNombre: preVenta.clienteNombre || 'Cliente sin nombre',
        preVentas: [],
      })
    }
    groups.get(key).preVentas.push(preVenta)
  })

  return Array.from(groups.values())
}

export function calculatePreVentaGroupSummary(records = []) {
  const summary = records.reduce((acc, record) => {
    const amount = Number(record.precio || 0)
    const quantity = Number(record.cantidad || 0)
    const paid = record.estado === 'PAGADA'

    acc.preVentaCount += 1
    acc.totalQuantity += Number.isFinite(quantity) ? quantity : 0
    acc.totalAmount += Number.isFinite(amount) ? amount : 0
    if (paid) {
      acc.paidCount += 1
      acc.paidAmount += Number.isFinite(amount) ? amount : 0
    } else {
      acc.pendingCount += 1
      acc.pendingAmount += Number.isFinite(amount) ? amount : 0
    }
    return acc
  }, {
    preVentaCount: 0,
    totalQuantity: 0,
    totalAmount: 0,
    pendingCount: 0,
    paidCount: 0,
    pendingAmount: 0,
    paidAmount: 0,
  })

  return Object.fromEntries(Object.entries(summary).map(([key, value]) => [key, key.endsWith('Amount') ? Number(value.toFixed(2)) : value]))
}

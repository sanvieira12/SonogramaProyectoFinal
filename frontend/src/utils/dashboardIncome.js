export function cantidadVentasLabel(cantidad = 0) {
  const total = Number(cantidad || 0)
  return `${total} ${total === 1 ? 'venta' : 'ventas'}`
}

export function cantidadPagosLabel(cantidad = 0) {
  const total = Number(cantidad || 0)
  return `${total} ${total === 1 ? 'pago de deuda' : 'pagos de deudas'}`
}

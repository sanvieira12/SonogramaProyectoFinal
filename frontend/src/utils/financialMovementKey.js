export function financialMovementKey(movement) {
  return `${movement?.tipoMovimiento || 'VENTA'}-${movement?.idPagoDeuda ?? movement?.idVenta ?? 'none'}`
}

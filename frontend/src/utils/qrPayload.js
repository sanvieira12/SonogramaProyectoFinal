const SALE_PATH = '/ventas/nueva'

function normalizePath(pathname) {
  const path = pathname.replace(/\/+$/, '')
  return path || '/'
}

export function parseQrPayload(value) {
  const rawValue = String(value || '').trim()
  if (!rawValue || rawValue.length > 2048) return null

  try {
    const currentOrigin = typeof window !== 'undefined' ? window.location.origin : null
    const url = new URL(rawValue)
    const idDisco = url.searchParams.get('idDisco')
    const codigoQr = url.searchParams.get('qr')?.trim()

    if (currentOrigin && url.origin !== currentOrigin) return null
    if (normalizePath(url.pathname) !== SALE_PATH) return null
    if (!/^\d+$/.test(idDisco || '') || Number(idDisco) <= 0) return null
    if (!codigoQr || codigoQr.length > 255) return null

    return { idDisco: Number(idDisco), codigoQr }
  } catch {
    return null
  }
}

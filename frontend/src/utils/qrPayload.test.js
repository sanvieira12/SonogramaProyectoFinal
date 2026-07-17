import { describe, expect, it } from 'vitest'
import { parseQrPayload } from './qrPayload'

describe('parseQrPayload', () => {
  it('accepts an existing catalog QR URL from the current application origin', () => {
    expect(parseQrPayload(`${window.location.origin}/ventas/nueva?idDisco=42&qr=legacy-copy-42`)).toEqual({
      idDisco: 42,
      codigoQr: 'legacy-copy-42',
    })
  })

  it('rejects external QR URLs', () => {
    expect(parseQrPayload('https://example.com/ventas/nueva?idDisco=42&qr=legacy-copy-42')).toBeNull()
  })

  it('rejects malformed or unrelated URLs', () => {
    expect(parseQrPayload('http://localhost:5173/discos/42?qr=legacy-copy-42')).toBeNull()
    expect(parseQrPayload('http://localhost:5173/ventas/nueva?idDisco=0&qr=legacy-copy-42')).toBeNull()
    expect(parseQrPayload('http://localhost:5173/ventas/nueva?idDisco=42')).toBeNull()
  })
})

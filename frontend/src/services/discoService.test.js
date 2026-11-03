import { afterEach, describe, expect, it, vi } from 'vitest'
import { discoService } from './discoService'

vi.mock('../api/session', () => ({
  redirectIfUnauthorized: vi.fn(() => false),
}))

describe('discoService catalog errors', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('preserves the specific backend reason when catalog deletion is rejected', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: false,
      status: 409,
      text: () => Promise.resolve(JSON.stringify({
        message: 'No se puede eliminar el disco porque tiene una reserva activa.',
      })),
    })

    await expect(discoService.eliminar(42)).rejects.toThrow(
      'No se puede eliminar el disco porque tiene una reserva activa.',
    )
  })

  it('uses a Spanish connection error instead of exposing the browser fetch error', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'))

    await expect(discoService.eliminar(42)).rejects.toThrow(
      'No se pudo conectar con Sonograma. Revisá la conexión e intentá nuevamente.',
    )
  })
})

import { afterEach, describe, expect, it, vi } from 'vitest'
import { downloadBlob, filenameFromContentDisposition } from './downloadBlob'

const originalCreateObjectURL = URL.createObjectURL
const originalRevokeObjectURL = URL.revokeObjectURL

describe('downloadBlob', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    vi.useRealTimers()
    if (originalCreateObjectURL) {
      Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: originalCreateObjectURL })
    } else {
      delete URL.createObjectURL
    }
    if (originalRevokeObjectURL) {
      Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: originalRevokeObjectURL })
    } else {
      delete URL.revokeObjectURL
    }
    document.body.innerHTML = ''
  })

  it('extracts plain and encoded filenames from Content-Disposition', () => {
    expect(filenameFromContentDisposition('attachment; filename="discogs.zip"')).toBe('discogs.zip')
    expect(filenameFromContentDisposition("attachment; filename*=UTF-8''vinylfuture%20export.zip")).toBe('vinylfuture export.zip')
  })

  it('appends and removes the anchor, then revokes the URL later', () => {
    vi.useFakeTimers()
    const objectUrl = 'blob:sonograma-test'
    const createObjectURL = vi.fn(() => objectUrl)
    const revokeObjectURL = vi.fn()
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: createObjectURL })
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: revokeObjectURL })
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const appendChild = vi.spyOn(document.body, 'appendChild')

    downloadBlob(new Blob(['zip']), 'fallback.zip', 'attachment; filename="real.zip"')

    expect(createObjectURL).toHaveBeenCalled()
    expect(appendChild).toHaveBeenCalledWith(expect.any(HTMLAnchorElement))
    const link = appendChild.mock.calls[0][0]
    expect(link.href).toBe(objectUrl)
    expect(link.download).toBe('real.zip')
    expect(click).toHaveBeenCalledTimes(1)
    expect(document.body.contains(link)).toBe(false)
    expect(revokeObjectURL).not.toHaveBeenCalled()

    vi.advanceTimersByTime(1000)
    expect(revokeObjectURL).toHaveBeenCalledWith(objectUrl)
  })

  it('rejects empty blobs', () => {
    expect(() => downloadBlob(new Blob([]), 'empty.zip')).toThrow('El ZIP se generó vacío o no se pudo preparar.')
  })
})

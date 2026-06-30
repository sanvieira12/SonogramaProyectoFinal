export function filenameFromContentDisposition(contentDisposition) {
  if (!contentDisposition) return ''

  const encodedName = contentDisposition.match(/filename\*=(?:UTF-8'')?([^;]+)/i)?.[1]
  if (encodedName) {
    try {
      return decodeURIComponent(encodedName.trim().replace(/^"|"$/g, ''))
    } catch {
      return encodedName.trim().replace(/^"|"$/g, '')
    }
  }

  return contentDisposition.match(/filename="([^"]+)"/i)?.[1]
    || contentDisposition.match(/filename=([^;]+)/i)?.[1]?.trim()
    || ''
}

export function downloadBlob(blob, fallbackFilename, contentDisposition) {
  if (!blob || blob.size === 0) {
    throw new Error('El ZIP se generó vacío o no se pudo preparar.')
  }

  const filename = filenameFromContentDisposition(contentDisposition) || fallbackFilename || 'download.zip'
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}

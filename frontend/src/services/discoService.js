import { redirectIfUnauthorized } from '../api/session'

const BASE = (import.meta.env.VITE_API_URL || '/api').trim().replace(/\/+$/, '') || '/api'

function token() {
  return localStorage.getItem('token')
}

function headers() {
  return {
    'Content-Type': 'application/json',
    ...(token() ? { Authorization: `Bearer ${token()}` } : {}),
  }
}

async function request(method, path, body) {
  let res
  try {
    res = await fetch(`${BASE}${path}`, {
      method,
      headers: headers(),
      body: body ? JSON.stringify(body) : undefined,
    })
  } catch (error) {
    throw new Error('No se pudo conectar con Sonograma. Revisá la conexión e intentá nuevamente.', { cause: error })
  }
  if (redirectIfUnauthorized(res)) throw new Error('Tu sesión venció. Ingresá nuevamente.')
  if (res.status === 204) return null
  const text = await res.text()
  let data
  try {
    data = text ? JSON.parse(text) : null
  } catch {
    data = null
  }
  if (!res.ok) {
    throw new Error(data?.message || data?.error || text || 'No se pudo completar la operación.')
  }
  if (text && data == null) {
    throw new Error('El servidor devolvió una respuesta inesperada.')
  }
  return data
}

export const discoService = {
  getAll: () => request('GET', '/discos'),
  getPorFuenteImportacionDiscogs: (source) =>
    request('GET', `/discos?discogsSource=${encodeURIComponent(source)}`),
  listarFuentesImportacionDiscogs: () => request('GET', '/discos/filtros/fuentes-discogs'),
  getById: (id) => request('GET', `/discos/${id}`),
  buscar: (q) => request('GET', `/discos/buscar?q=${encodeURIComponent(q)}`),
  getPorEstado: (estado) => request('GET', `/discos/estado/${estado}`),
  crear: (data) => request('POST', '/discos', data),
  actualizar: (id, data) => request('PUT', `/discos/${id}`, data),
  cambiarEstado: (id, estado) =>
    request('PATCH', `/discos/${id}/estado?nuevoEstado=${encodeURIComponent(estado)}`),
  actualizarCopias: (id, cantidad) =>
    request('PATCH', `/discos/${id}/copias?cantidad=${cantidad}`),
  eliminar: (id) => request('DELETE', `/discos/${id}`),
}

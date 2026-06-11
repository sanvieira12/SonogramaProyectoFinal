import { redirectIfUnauthorized } from '../api/session'

const BASE = import.meta.env.VITE_API_URL || '/api'

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
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers: headers(),
    body: body ? JSON.stringify(body) : undefined,
  })
  if (redirectIfUnauthorized(res)) throw new Error('Tu sesión venció. Ingresá nuevamente.')
  if (res.status === 204) return null
  const data = await res.json()
  if (!res.ok) throw new Error(data.message || data.error || 'Error en la solicitud')
  return data
}

export const discoService = {
  getAll: () => request('GET', '/discos'),
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

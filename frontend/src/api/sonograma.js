const BASE = import.meta.env.VITE_API_URL || '/api'

function token() {
  return localStorage.getItem('token')
}

function headers(extra = {}) {
  return {
    'Content-Type': 'application/json',
    ...(token() ? { Authorization: `Bearer ${token()}` } : {}),
    ...extra,
  }
}

async function request(method, path, body) {
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers: headers(),
    body: body ? JSON.stringify(body) : undefined,
  })
  if (res.status === 204) return null
  const data = await res.json()
  if (!res.ok) throw new Error(data.error || 'Error en la solicitud')
  return data
}

export const api = {
  login: (nombreUsuario, contrasenia) =>
    request('POST', '/auth/login', { nombreUsuario, contrasenia }),

  registro: (nombreUsuario, email, contrasenia) =>
    request('POST', '/auth/registro', { nombreUsuario, email, contrasenia }),

  discos: {
    todos: () => request('GET', '/discos'),
    disponibles: () => request('GET', '/discos/disponibles'),
    crear: (disco) => request('POST', '/discos', disco),
    actualizar: (id, disco) => request('PUT', `/discos/${id}`, disco),
    cambiarEstado: (id, estado) => request('PATCH', `/discos/${id}/estado`, { estado }),
    eliminar: (id) => request('DELETE', `/discos/${id}`),
    buscarArtista: (q) => request('GET', `/discos/buscar/artista?q=${encodeURIComponent(q)}`),
    buscarAlbum: (q) => request('GET', `/discos/buscar/album?q=${encodeURIComponent(q)}`),
  },

  clientes: {
    todos: () => request('GET', '/clientes'),
    crear: (cliente) => request('POST', '/clientes', cliente),
  },

  qr: {
    urlDescarga: (idDisco) => `${BASE}/qr/descargar/${idDisco}`,
  },
}

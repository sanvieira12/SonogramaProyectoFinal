import { redirectIfUnauthorized } from './session'

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
  if (redirectIfUnauthorized(res)) throw new Error('Tu sesión venció. Ingresá nuevamente.')
  if (res.status === 204) return null
  const text = await res.text()
  let data
  try {
    data = text ? JSON.parse(text) : null
  } catch {
    data = { message: text || 'Respuesta inesperada del servidor' }
  }
  if (!res.ok) {
    throw new Error(data?.message || data?.error || 'Error en la solicitud')
  }
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
    porId: (id) => request('GET', `/discos/${id}`),
    crear: (disco) => request('POST', '/discos', disco),
    actualizar: (id, disco) => request('PUT', `/discos/${id}`, disco),
    cambiarEstado: (id, estado) =>
      request('PATCH', `/discos/${id}/estado?nuevoEstado=${encodeURIComponent(estado)}`),
    eliminar: (id) => request('DELETE', `/discos/${id}`),
    buscar: (q) => request('GET', `/discos/buscar?q=${encodeURIComponent(q)}`),
  },

  clientes: {
    todos: () => request('GET', '/clientes'),
    porId: (id) => request('GET', `/clientes/${id}`),
    detalle: (id) => request('GET', `/clientes/${id}/detalle`),
    direcciones: (id) => request('GET', `/clientes/${id}/direcciones`),
    buscar: (q) => request('GET', `/clientes/buscar?q=${encodeURIComponent(q)}`),
    crear: (cliente) => request('POST', '/clientes', cliente),
    crearDireccion: (id, direccion) => request('POST', `/clientes/${id}/direcciones`, direccion),
    actualizar: (id, cliente) => request('PUT', `/clientes/${id}`, cliente),
    importarExcel: async (file) => {
      const fd = new FormData(); fd.append('file', file)
      const res = await fetch(`${BASE}/clientes/importar-excel`, {
        method: 'POST',
        headers: token() ? { Authorization: `Bearer ${token()}` } : {},
        body: fd,
      })
      if (redirectIfUnauthorized(res)) throw new Error('Sesión vencida')
      const data = await res.json()
      if (!res.ok) throw new Error(data?.error || 'Error al importar')
      return data
    },
  },

  ventas: {
    todas: () => request('GET', '/ventas'),
    porId: (id) => request('GET', `/ventas/${id}`),
    porCliente: (idCliente) => request('GET', `/ventas/cliente/${idCliente}`),
    registrar: (venta) => request('POST', '/ventas', venta),
    estadisticasPorMes: () => request('GET', '/ventas/estadisticas/por-mes'),
    configuracionCostos: () => request('GET', '/ventas/configuracion-costos'),
  },

  envios: {
    departamentosDac: () => request('GET', '/envios/dac/departamentos'),
    sucursalesDac: (departamento) =>
      request('GET', `/envios/dac/sucursales?departamento=${encodeURIComponent(departamento)}`),
    cotizarDac: (departamento, sucursalCodigo) =>
      request('GET', `/envios/dac/cotizar?departamento=${encodeURIComponent(departamento)}&sucursalCodigo=${encodeURIComponent(sucursalCodigo)}`),
  },

  estadisticas: {
    catalogo: () => request('GET', '/estadisticas/catalogo'),
  },

  deudas: {
    listar: (q) => request('GET', `/deudas${q ? `?q=${encodeURIComponent(q)}` : ''}`),
    resumen: () => request('GET', '/deudas/resumen'),
    porCliente: (id) => request('GET', `/deudas/cliente/${id}`),
    importarExcel: async (file) => {
      const fd = new FormData(); fd.append('file', file)
      const res = await fetch(`${BASE}/deudas/importar-excel`, {
        method: 'POST',
        headers: token() ? { Authorization: `Bearer ${token()}` } : {},
        body: fd,
      })
      if (redirectIfUnauthorized(res)) throw new Error('Sesión vencida')
      const data = await res.json()
      if (!res.ok) throw new Error(data?.error || 'Error al importar')
      return data
    },
    registrarPago: (idDeuda, monto, notas) =>
      request('POST', `/deudas/${idDeuda}/registrar-pago`, { monto, notas }),
  },

  libro: {
    listar: (params = {}) => {
      const q = new URLSearchParams(Object.entries(params).filter(([, v]) => v)).toString()
      return request('GET', `/ventas/libro${q ? `?${q}` : ''}`)
    },
    exportarUrl: (params = {}) => {
      const q = new URLSearchParams(Object.entries(params).filter(([, v]) => v)).toString()
      return `${import.meta.env.VITE_API_URL || '/api'}/ventas/libro/exportar${q ? `?${q}` : ''}`
    },
  },

  shippingOrders: {
    listar: () => request('GET', '/shipping-orders'),
    porId: (id) => request('GET', `/shipping-orders/${id}`),
    crear: (dto) => request('POST', '/shipping-orders', dto),
    exportarUrl: (id) => `${import.meta.env.VITE_API_URL || '/api'}/shipping-orders/${id}/exportar`,
  },

  qr: {
    urlDescarga: (idDisco) => `${BASE}/qr/descargar/${idDisco}`,
    escanear: (codigoQr) => request('POST', '/qr/escanear', { codigoQr }),
  },

  importar: {
    vinylfutureCsv: async (file) => {
      const fd = new FormData()
      fd.append('file', file)
      const res = await fetch(`${BASE}/importar/vinylfuture-csv`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${localStorage.getItem('token')}` },
        body: fd,
      })
      if (redirectIfUnauthorized(res)) throw new Error('Tu sesión venció. Ingresá nuevamente.')
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || 'Error procesando PDF')
      }
      return res.blob()
    },
  },

  pedidos: {
    listar: () => request('GET', '/pedidos'),
    porId: (id) => request('GET', `/pedidos/${id}`),
    uploadPdf: async (file) => {
      const fd = new FormData()
      fd.append('file', file)
      const res = await fetch(`${BASE}/pedidos/upload-pdf`, {
        method: 'POST',
        headers: token() ? { Authorization: `Bearer ${token()}` } : {},
        body: fd,
      })
      if (redirectIfUnauthorized(res)) throw new Error('Tu sesión venció. Ingresá nuevamente.')
      const data = await res.json()
      if (!res.ok) throw new Error(data?.message || data?.error || 'Error al procesar el PDF')
      return data
    },
    configurar: (id, cfg) => request('PATCH', `/pedidos/${id}/configuracion`, cfg),
    enriquecer: (id) => request('POST', `/pedidos/${id}/enriquecer`),
    importarCatalogo: (id) => request('POST', `/pedidos/${id}/importar-catalogo`),
    retryItem: (pedidoId, itemId) => request('POST', `/pedidos/${pedidoId}/items/${itemId}/retry-enrich`),
  },

  importaciones: {
    vinylfuturePreview: async (file) => {
      const fd = new FormData()
      fd.append('file', file)
      const res = await fetch(`${BASE}/importaciones/vinylfuture/preview`, {
        method: 'POST',
        headers: token() ? { Authorization: `Bearer ${token()}` } : {},
        body: fd,
      })
      if (redirectIfUnauthorized(res)) throw new Error('Tu sesión venció. Ingresá nuevamente.')
      if (!res.ok) throw new Error('Error al parsear el Excel')
      return res.json()
    },

    vinylfutureConfirmar: (seleccionados) =>
      request('POST', '/importaciones/vinylfuture/confirmar', seleccionados),

    discogsDesdeLink: (url) =>
      request('POST', '/importaciones/discogs/desde-link', { url }),

    discogsGuardar: (preview) =>
      request('POST', '/importaciones/discogs/guardar', preview),

    discogsDesdeExcel: async (file) => {
      const fd = new FormData()
      fd.append('file', file)
      const res = await fetch(`${BASE}/importaciones/discogs/desde-excel`, {
        method: 'POST',
        headers: token() ? { Authorization: `Bearer ${token()}` } : {},
        body: fd,
      })
      if (redirectIfUnauthorized(res)) throw new Error('Tu sesión venció. Ingresá nuevamente.')
      if (!res.ok) throw new Error('Error al procesar Excel con links de Discogs')
      return res.json()
    },

    discogsGuardarLote: (previews) =>
      request('POST', '/importaciones/discogs/guardar-lote', previews),
  },
}

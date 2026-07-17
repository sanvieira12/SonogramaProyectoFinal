import { redirectIfUnauthorized } from './session'
import { filenameFromContentDisposition } from '../utils/downloadBlob'

export function normalizeApiBase(value) {
  const base = (value || '/api').trim().replace(/\/+$/, '')
  return base || '/api'
}

const BASE = normalizeApiBase(import.meta.env.VITE_API_URL)

export function resolveApiUrl(path) {
  if (!path) return ''
  if (/^(https?:)?\/\//i.test(path) || path.startsWith('data:') || path.startsWith('blob:')) {
    return path
  }
  const origin = (() => {
    try {
      return new URL(BASE, typeof window !== 'undefined' ? window.location.origin : 'http://localhost').origin
    } catch {
      return typeof window !== 'undefined' ? window.location.origin : ''
    }
  })()
  if (!origin) return path
  return path.startsWith('/') ? `${origin}${path}` : `${origin}/${path}`
}

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
  let res
  try {
    res = await fetch(`${BASE}${path}`, {
      method,
      headers: headers(),
      body: body ? JSON.stringify(body) : undefined,
    })
  } catch {
    throw new Error('No se pudo conectar con Sonograma. Revisá la conexión e intentá nuevamente.')
  }
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

async function readResponseMessage(res, fallback) {
  const text = await res.text()
  if (!text) return fallback
  try {
    const data = JSON.parse(text)
    return data?.message || data?.error || text
  } catch {
    return text
  }
}

async function readZipResponse(res, { fallbackFilename, fallbackError }) {
  const contentType = res.headers.get('Content-Type') || ''
  const disposition = res.headers.get('Content-Disposition') || ''

  if (!res.ok) {
    throw new Error(await readResponseMessage(res, fallbackError))
  }

  if (/json|text|html/i.test(contentType)) {
    throw new Error(await readResponseMessage(res, fallbackError))
  }

  const blob = await res.blob()
  if (!blob || blob.size === 0) {
    throw new Error('El ZIP se generó vacío o no se pudo preparar.')
  }
  return {
    blob,
    filename: filenameFromContentDisposition(disposition) || fallbackFilename,
    contentDisposition: disposition,
  }
}

export const api = {
  login: (nombreUsuario, contrasenia) =>
    request('POST', '/auth/login', { nombreUsuario, contrasenia }),
  session: () => request('GET', '/auth/session'),

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
    cambiarEstadoCopia: (idDisco, idCopia, estado) =>
      request('PATCH', `/discos/${idDisco}/copias/${idCopia}/estado?nuevoEstado=${encodeURIComponent(estado)}`),
    eliminar: (id) => request('DELETE', `/discos/${id}`),
    buscar: (q) => request('GET', `/discos/buscar?q=${encodeURIComponent(q)}`),
    previews: {
      listar: (id) => request('GET', `/discos/${id}/previews`),
      agregar: (id, data) => request('POST', `/discos/${id}/previews`, data),
      actualizarUrl: (id, previewId, audioUrl) =>
        request('PATCH', `/discos/${id}/previews/${previewId}/url`, { audioUrl }),
      eliminar: (id, previewId) => request('DELETE', `/discos/${id}/previews/${previewId}`),
    },
  },

  pricing: {
    settings: () => request('GET', '/pricing/settings'),
    updateSettings: (settings) => request('PUT', '/pricing/settings', settings),
    preview: (settings) => request('POST', '/pricing/preview', { settings }),
    apply: (settings, scope, selectedIds = []) => request('POST', '/pricing/apply', { settings, scope, selectedIds }),
    updateMarkup: (id, markup) => request('PATCH', `/pricing/discs/${id}/markup`, { markup }),
    reset: () => request('POST', '/pricing/reset'),
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
    eliminar: (id) => request('DELETE', `/clientes/${id}`),
    exportar: async () => {
      const res = await fetch(`${BASE}/clientes/exportar`, {
        headers: token() ? { Authorization: `Bearer ${token()}` } : {},
      })
      if (redirectIfUnauthorized(res)) throw new Error('Sesión vencida')
      if (!res.ok) throw new Error('No se pudo exportar clientes')
      return res.blob()
    },
    importarExcel: async (file, hoja) => {
      const fd = new FormData(); fd.append('file', file)
      const query = hoja ? `?hoja=${encodeURIComponent(hoja)}` : ''
      const res = await fetch(`${BASE}/clientes/importar-excel${query}`, {
        method: 'POST',
        headers: token() ? { Authorization: `Bearer ${token()}` } : {},
        body: fd,
      })
      if (redirectIfUnauthorized(res)) throw new Error('Sesión vencida')
      const text = await res.text()
      let data
      try {
        data = text ? JSON.parse(text) : null
      } catch {
        data = { message: text }
      }
      if (!res.ok) throw new Error(data?.message || data?.error || 'Error al importar')
      return data
    },
  },

  ventas: {
    todas: () => request('GET', '/ventas'),
    porId: (id) => request('GET', `/ventas/${id}`),
    porCliente: (idCliente) => request('GET', `/ventas/cliente/${idCliente}`),
    registrar: (venta) => request('POST', '/ventas', venta),
    actualizar: (id, venta) => request('PUT', `/ventas/${id}`, venta),
    cancelar: (id) => request('DELETE', `/ventas/${id}`),
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
    ingresos: (periodo) => request('GET', `/estadisticas/ingresos?periodo=${encodeURIComponent(periodo)}`),
  },

  deudas: {
    listar: (q) => request('GET', `/deudas${q ? `?q=${encodeURIComponent(q)}` : ''}`),
    resumen: () => request('GET', '/deudas/resumen'),
    porId: (id) => request('GET', `/deudas/${id}`),
    porCliente: (id) => request('GET', `/deudas/cliente/${id}`),
    crear: (deuda) => request('POST', '/deudas', deuda),
    actualizar: (id, deuda) => request('PUT', `/deudas/${id}`, deuda),
    eliminar: (id) => request('DELETE', `/deudas/${id}`),
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
    eliminarPago: (idDeuda, idPagoDeuda) =>
      request('DELETE', `/deudas/${idDeuda}/pagos/${idPagoDeuda}`),
  },

  libro: {
    listar: (params = {}) => {
      const q = new URLSearchParams(Object.entries(params).filter(([, v]) => v)).toString()
      return request('GET', `/ventas/libro${q ? `?${q}` : ''}`)
    },
    exportarUrl: (params = {}) => {
      const q = new URLSearchParams(Object.entries(params).filter(([, v]) => v)).toString()
      return `${BASE}/ventas/libro/exportar${q ? `?${q}` : ''}`
    },
  },

  preVentas: {
    listar: () => request('GET', '/pre-ventas'),
    crear: (payload) => request('POST', '/pre-ventas', payload),
    marcarPagada: (id) => request('POST', `/pre-ventas/${id}/marcar-pagada`),
    eliminar: (id) => request('DELETE', `/pre-ventas/${id}`),
  },

  gastosTienda: {
    listar: () => request('GET', '/gastos-tienda'),
    resumen: () => request('GET', '/gastos-tienda/resumen'),
    crear: (payload) => request('POST', '/gastos-tienda', payload),
    actualizar: (id, payload) => request('PUT', `/gastos-tienda/${id}`, payload),
    eliminar: (id) => request('DELETE', `/gastos-tienda/${id}`),
  },

  shippingOrders: {
    listar: () => request('GET', '/shipping-orders'),
    porId: (id) => request('GET', `/shipping-orders/${id}`),
    crear: (dto) => request('POST', '/shipping-orders', dto),
    exportarUrl: (id) => `${import.meta.env.VITE_API_URL || '/api'}/shipping-orders/${id}/exportar`,
  },

  qr: {
    urlDescarga: (idDisco) => `${BASE}/qr/descargar/${idDisco}`,
    urlDescargaCopia: (idDisco, copyNumber) => `${BASE}/qr/descargar/${idDisco}/${copyNumber}`,
    descargarCopia: async (idDisco, copyNumber) => {
      const res = await fetch(`${BASE}/qr/descargar/${idDisco}/${copyNumber}`, {
        headers: token() ? { Authorization: `Bearer ${token()}` } : {},
      })
      if (redirectIfUnauthorized(res)) throw new Error('Tu sesión venció. Ingresá nuevamente.')
      if (!res.ok) throw new Error(await readResponseMessage(res, 'No se pudo descargar el QR'))
      const contentType = res.headers.get('Content-Type') || ''
      if (!/image\/png/i.test(contentType)) {
        throw new Error(await readResponseMessage(res, 'El servidor no devolvió una imagen PNG válida'))
      }
      const blob = await res.blob()
      if (!blob || blob.size === 0) throw new Error('El QR se generó vacío o no se pudo preparar.')
      return {
        blob,
        contentDisposition: res.headers.get('Content-Disposition') || '',
      }
    },
    escanear: (codigoQr) => request('POST', '/qr/escanear', { codigoQr }),
  },

  importar: {
    vinylfutureCatalogo: async (file) => {
      const fd = new FormData()
      fd.append('file', file)
      const res = await fetch(`${BASE}/importar/vinylfuture-catalogo`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${localStorage.getItem('token')}` },
        body: fd,
      })
      if (redirectIfUnauthorized(res)) throw new Error('Tu sesión venció. Ingresá nuevamente.')
      const text = await res.text()
      let data
      try { data = text ? JSON.parse(text) : null } catch { data = null }
      if (!res.ok) throw new Error(data?.message || text || 'Error procesando PDF')
      return data
    },
    vinylfutureJob: (jobId) => request('GET', `/importar/vinylfuture/jobs/${encodeURIComponent(jobId)}`),
    vinylfutureCsv: async (file) => {
      const fd = new FormData()
      fd.append('file', file)
      const res = await fetch(`${BASE}/importar/vinylfuture-csv`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${localStorage.getItem('token')}` },
        body: fd,
      })
      if (redirectIfUnauthorized(res)) throw new Error('Tu sesión venció. Ingresá nuevamente.')
      return readZipResponse(res, {
        fallbackFilename: 'vinylfuture-import.zip',
        fallbackError: 'Error procesando PDF',
      })
    },
    vinylfutureZip: async (importId) => {
      const res = await fetch(`${BASE}/importar/vinylfuture/${encodeURIComponent(importId)}/zip`, {
        headers: { Authorization: `Bearer ${localStorage.getItem('token')}` },
      })
      if (redirectIfUnauthorized(res)) throw new Error('Tu sesión venció. Ingresá nuevamente.')
      return readZipResponse(res, {
        fallbackFilename: `vinylfuture-${importId}.zip`,
        fallbackError: 'Error exportando ZIP',
      })
    },
  },

  pedidos: {
    listar: (source) => request('GET', `/pedidos${source ? `?source=${encodeURIComponent(source)}` : ''}`),
    porId: (id) => request('GET', `/pedidos/${id}`),
    uploadControl: async (pdf, template) => {
      const fd = new FormData()
      fd.append('pdf', pdf)
      if (template) fd.append('template', template)
      const res = await fetch(`${BASE}/pedidos/upload-control`, {
        method: 'POST',
        headers: token() ? { Authorization: `Bearer ${token()}` } : {},
        body: fd,
      })
      if (redirectIfUnauthorized(res)) throw new Error('Tu sesión venció. Ingresá nuevamente.')
      if (!res.ok) {
        const text = await res.text()
        let data
        try { data = text ? JSON.parse(text) : null } catch { data = null }
        throw new Error(data?.message || data?.error || text || 'Error al generar el Excel')
      }
      const disposition = res.headers.get('Content-Disposition') || ''
      const encodedName = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
      const plainName = disposition.match(/filename="?([^";]+)"?/i)?.[1]
      return {
        blob: await res.blob(),
        pedidoId: res.headers.get('X-Pedido-Id'),
        filename: encodedName ? decodeURIComponent(encodedName) : (plainName || 'invoice_control.xlsx'),
      }
    },
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
    pdfUrl: (id) => `${BASE}/pedidos/${id}/pdf`,
    descargarPdf: async (id) => {
      const res = await fetch(`${BASE}/pedidos/${id}/pdf`, {
        headers: token() ? { Authorization: `Bearer ${token()}` } : {},
      })
      if (redirectIfUnauthorized(res)) throw new Error('Tu sesión venció. Ingresá nuevamente.')
      if (!res.ok) throw new Error('No se pudo descargar el PDF original')
      return res.blob()
    },
  },

  notas: {
    listar: (search) => request('GET', `/notas${search ? `?search=${encodeURIComponent(search)}` : ''}`),
    porId: (id) => request('GET', `/notas/${id}`),
    crear: (nota) => request('POST', '/notas', nota),
    actualizar: (id, nota) => request('PUT', `/notas/${id}`, nota),
    archivar: (id) => request('DELETE', `/notas/${id}`),
  },

  deudores: {
    todos: () => request('GET', '/deudores'),
    importarExcel: async (formData) => {
      const res = await fetch(`${BASE}/deudores/importar-excel`, {
        method: 'POST',
        headers: token() ? { Authorization: `Bearer ${token()}` } : {},
        body: formData,
      })
      if (redirectIfUnauthorized(res)) throw new Error('Sesión vencida')
      const text = await res.text()
      let data
      try { data = text ? JSON.parse(text) : null } catch { data = { message: text } }
      if (!res.ok) throw new Error(data?.message || data?.error || 'Error al importar')
      return data
    },
    actualizar: (id, data) => request('PUT', `/deudores/${id}`, data),
    eliminar: (id) => request('DELETE', `/deudores/${id}`),
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
      const res = await fetch(`${BASE}/importaciones/discogs/jobs`, {
        method: 'POST',
        headers: token() ? { Authorization: `Bearer ${token()}` } : {},
        body: fd,
      })
      if (redirectIfUnauthorized(res)) throw new Error('Tu sesión venció. Ingresá nuevamente.')
      const data = await res.json()
      if (!res.ok) throw new Error(data?.message || 'Error al procesar Excel con links de Discogs')
      return data
    },

    discogsJob: (jobId) =>
      request('GET', `/importaciones/discogs/jobs/${jobId}`),

    discogsRetryRow: (jobId, rowId) =>
      request('POST', `/importaciones/discogs/jobs/${jobId}/rows/${rowId}/retry`),

    discogsRetryPending: (jobId) =>
      request('POST', `/importaciones/discogs/jobs/${jobId}/retry-pending`),

    discogsImportarJob: (jobId) =>
      request('POST', `/importaciones/discogs/jobs/${jobId}/importar`),

    discogsCoversZip: async (jobId) => {
      const res = await fetch(`${BASE}/importaciones/discogs/jobs/${jobId}/covers.zip`, {
        headers: token() ? { Authorization: `Bearer ${token()}` } : {},
      })
      if (redirectIfUnauthorized(res)) throw new Error('Tu sesión venció. Ingresá nuevamente.')
      return readZipResponse(res, {
        fallbackFilename: `discogs-covers-${jobId}.zip`,
        fallbackError: 'No se pudo generar el ZIP de portadas Discogs',
      })
    },

    discogsGuardarLote: (previews) =>
      request('POST', '/importaciones/discogs/guardar-lote', previews),
  },
}

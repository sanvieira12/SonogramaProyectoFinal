import { useEffect, useState } from 'react'
import { BrowserRouter, Routes, Route, Navigate, Outlet } from 'react-router-dom'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import DiscosCatalogo from './pages/DiscosCatalogo'
import Clientes from './pages/Clientes'
import NuevaVenta from './pages/NuevaVenta'
import Importar from './pages/Importar'
import Deudas from './pages/Deudas'
import LibroVentas from './pages/LibroVentas'
import PreVentas from './pages/PreVentas'
import Pedidos from './pages/Pedidos'
import PedidoDetalle from './pages/PedidoDetalle'
import Notas from './pages/Notas'
import GastosTienda from './pages/GastosTienda'
import Navbar from './components/Navbar'
import StockLayout from './components/StockLayout'
import PricingSettingsPage from './pages/PricingSettingsPage'
import { api } from './api/sonograma'

function PrivateRoute() {
  const [status, setStatus] = useState(localStorage.getItem('token') ? 'checking' : 'guest')

  useEffect(() => {
    if (status !== 'checking') return
    let cancelled = false
    api.session()
      .then(usuario => {
        if (cancelled) return
        localStorage.setItem('usuario', JSON.stringify(usuario))
        setStatus('authenticated')
      })
      .catch(() => {
        if (cancelled) return
        localStorage.removeItem('token')
        localStorage.removeItem('usuario')
        setStatus('guest')
      })
    return () => { cancelled = true }
  }, [status])

  if (status === 'checking') {
    return (
      <div className="min-h-screen bg-white dark:bg-black flex items-center justify-center">
        <p className="text-sm text-slate-500 dark:text-stone-400">Validando sesión...</p>
      </div>
    )
  }
  return status === 'authenticated' ? <Outlet /> : <Navigate to="/login" replace />
}

function Layout() {
  const usuario = JSON.parse(localStorage.getItem('usuario') || '{}')
  return (
    <div className="min-h-screen bg-white dark:bg-black transition-colors duration-300">
      <Navbar usuario={usuario} />
      <Outlet />
    </div>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route element={<PrivateRoute />}>
          <Route element={<Layout />}>
            <Route path="/" element={<Dashboard />} />
            <Route path="/discos" element={<DiscosCatalogo />} />
            <Route path="/clientes" element={<Clientes />} />
            <Route path="/ventas/nueva" element={<NuevaVenta />} />
            <Route path="/pre-ventas" element={<PreVentas />} />
            <Route path="/importar" element={<Importar />} />
            <Route path="/stock" element={<StockLayout />}>
              <Route index element={<PricingSettingsPage />} />
              <Route path="pricing" element={<Navigate to="/stock" replace />} />
              <Route path="catalogo" element={<Navigate to="/discos" replace />} />
            </Route>
            <Route path="/deudas" element={<Deudas />} />
            <Route path="/libro-ventas" element={<LibroVentas />} />
            <Route path="/pedidos" element={<Pedidos />} />
            <Route path="/pedidos/:id" element={<PedidoDetalle />} />
            <Route path="/notas" element={<Notas />} />
            <Route path="/gastos-tienda" element={<GastosTienda />} />
            <Route path="/shipping-orders" element={<Navigate to="/pedidos" replace />} />
            <Route path="/deudores" element={<Navigate to="/deudas" replace />} />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}

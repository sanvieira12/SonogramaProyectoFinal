import { BrowserRouter, Routes, Route, Navigate, Outlet } from 'react-router-dom'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import DiscosCatalogo from './pages/DiscosCatalogo'
import Clientes from './pages/Clientes'
import NuevaVenta from './pages/NuevaVenta'
import Navbar from './components/Navbar'

function PrivateRoute() {
  return localStorage.getItem('token')
    ? <Outlet />
    : <Navigate to="/login" replace />
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
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}

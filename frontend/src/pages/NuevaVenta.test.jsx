import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import NuevaVenta from './NuevaVenta'
import { api } from '../api/sonograma'

const navigate = vi.fn()
let currentSearchParams = new URLSearchParams()

vi.mock('react-router-dom', () => ({
  useNavigate: () => navigate,
  useSearchParams: () => [currentSearchParams],
}))

vi.mock('../components/QRScanner', () => ({ default: () => null }))
vi.mock('../components/DacBranchSelect', () => ({ default: () => null }))
vi.mock('../api/sonograma', () => ({
  api: {
    envios: { departamentosDac: vi.fn(), sucursalesDac: vi.fn(), cotizar: vi.fn() },
    discos: { disponibles: vi.fn(), buscar: vi.fn(), buscarVenta: vi.fn(), porId: vi.fn() },
    clientes: { buscar: vi.fn(), direcciones: vi.fn(), crear: vi.fn() },
    ventas: { registrar: vi.fn() },
  },
  resolveApiUrl: vi.fn(value => value || ''),
}))

describe('NuevaVenta manual items', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    currentSearchParams = new URLSearchParams()
    api.envios.departamentosDac.mockResolvedValue([])
    api.envios.sucursalesDac.mockResolvedValue([])
    api.envios.cotizar.mockResolvedValue({ costo: 0 })
    api.discos.disponibles.mockResolvedValue([])
    api.discos.buscarVenta.mockResolvedValue([])
    api.clientes.direcciones.mockResolvedValue([])
    api.clientes.buscar.mockResolvedValue([{ idCliente: 7, nombre: 'Ana', apellido: 'Pérez', activo: true }])
    api.ventas.registrar.mockResolvedValue({ idVenta: 1 })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('requires and submits the explicit Nuevo/Usado classification', async () => {
    render(<NuevaVenta />)
    fireEvent.click(screen.getByRole('button', { name: 'Agregar disco fuera de catálogo' }))
    fireEvent.change(screen.getByPlaceholderText('Descripción'), { target: { value: 'Vinilo manual' } })
    fireEvent.change(screen.getByPlaceholderText('Precio unitario'), { target: { value: '900' } })
    fireEvent.click(screen.getByRole('button', { name: 'Agregar ítem' }))
    expect(await screen.findByText('Seleccioná si el ítem manual es nuevo o usado')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Tipo de ítem manual'), { target: { value: 'USADO' } })
    fireEvent.click(screen.getByRole('button', { name: 'Agregar ítem' }))
    expect(screen.getByText('Usado')).toBeInTheDocument()

    const clientInput = screen.getByPlaceholderText('Buscar por nombre, cédula, Instagram, teléfono o dirección…')
    fireEvent.change(clientInput, { target: { value: 'Ana' } })
    await waitFor(() => expect(screen.getByRole('button', { name: /Ana Pérez/ })).toBeInTheDocument())
    fireEvent.click(screen.getByRole('button', { name: /Ana Pérez/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar venta' }))

    await waitFor(() => expect(api.ventas.registrar).toHaveBeenCalledWith(expect.objectContaining({
      detalles: [expect.objectContaining({ manualItem: true, clasificacionItem: 'USADO', descripcion: 'Vinilo manual' })],
    })))
  })

  it('preserves codigoQr and exact copyId when a sale starts from an existing QR link', async () => {
    currentSearchParams = new URLSearchParams('idDisco=44&qr=physical-copy-b')
    api.discos.porId.mockResolvedValue({
      idDisco: 44,
      artista: 'Exact QR Artist',
      album: 'Exact QR Album',
      estado: 'DISPONIBLE',
      condicion: 'USADO',
      cantidadCopias: 2,
      precioVenta: 1250,
      qrCopies: [
        { id: 501, copyNumber: 1, codigoQr: 'physical-copy-a', estado: 'DISPONIBLE' },
        { id: 502, copyNumber: 2, codigoQr: 'physical-copy-b', estado: 'DISPONIBLE' },
      ],
    })

    render(<NuevaVenta />)
    expect(await screen.findByText('Exact QR Artist — Exact QR Album')).toBeInTheDocument()

    const clientInput = screen.getByPlaceholderText('Buscar por nombre, cédula, Instagram, teléfono o dirección…')
    fireEvent.change(clientInput, { target: { value: 'Ana' } })
    await waitFor(() => expect(screen.getByRole('button', { name: /Ana Pérez/ })).toBeInTheDocument())
    fireEvent.click(screen.getByRole('button', { name: /Ana Pérez/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar venta' }))

    await waitFor(() => expect(api.ventas.registrar).toHaveBeenCalledTimes(1))
    const submitted = api.ventas.registrar.mock.calls[0][0].detalles[0]
    expect(submitted.codigoQr).toBe('physical-copy-b')
    expect(submitted.copyId).toBe(502)
  })

  it('does not load the full inventory initially or search blank and one-character input', () => {
    render(<NuevaVenta />)

    expect(screen.getByText('Buscá por artista, título, código u origen')).toBeInTheDocument()
    expect(api.discos.disponibles).not.toHaveBeenCalled()
    expect(api.discos.buscar).not.toHaveBeenCalled()
    expect(api.discos.buscarVenta).not.toHaveBeenCalled()

    const input = screen.getByPlaceholderText('Buscar disco disponible para agregar…')
    fireEvent.change(input, { target: { value: ' ' } })
    fireEvent.change(input, { target: { value: 'x' } })

    expect(screen.getByText('Ingresá al menos 2 caracteres.')).toBeInTheDocument()
    expect(api.discos.buscarVenta).not.toHaveBeenCalled()
  })

  it('debounces rapid text and sends only the latest trimmed query', async () => {
    vi.useFakeTimers()
    render(<NuevaVenta />)
    const input = screen.getByPlaceholderText('Buscar disco disponible para agregar…')

    fireEvent.change(input, { target: { value: ' r' } })
    fireEvent.change(input, { target: { value: ' ri' } })
    fireEvent.change(input, { target: { value: ' rif' } })
    fireEvent.change(input, { target: { value: ' rifi ' } })
    await act(async () => { await vi.advanceTimersByTimeAsync(299) })
    expect(api.discos.buscarVenta).not.toHaveBeenCalled()

    await act(async () => { await vi.advanceTimersByTimeAsync(1) })
    expect(api.discos.buscarVenta).toHaveBeenCalledTimes(1)
    expect(api.discos.buscarVenta).toHaveBeenCalledWith('rifi', 20, expect.any(AbortSignal))
  })

  it('aborts the previous request and ignores a late stale response', async () => {
    vi.useFakeTimers()
    let resolveFirst
    let resolveSecond
    const first = new Promise(resolve => { resolveFirst = resolve })
    const second = new Promise(resolve => { resolveSecond = resolve })
    api.discos.buscarVenta.mockReturnValueOnce(first).mockReturnValueOnce(second)
    render(<NuevaVenta />)
    const input = screen.getByPlaceholderText('Buscar disco disponible para agregar…')

    fireEvent.change(input, { target: { value: 'et' } })
    await act(async () => { await vi.advanceTimersByTimeAsync(300) })
    const firstSignal = api.discos.buscarVenta.mock.calls[0][2]
    expect(firstSignal.aborted).toBe(false)

    fireEvent.change(input, { target: { value: 'eterna' } })
    expect(firstSignal.aborted).toBe(true)
    await act(async () => { await vi.advanceTimersByTimeAsync(300) })

    await act(async () => {
      resolveSecond([saleResult({ idDisco: 2, artista: 'Current Artist', album: 'Current Album' })])
      await second
    })
    expect(screen.getByText('Current Artist — Current Album')).toBeInTheDocument()

    await act(async () => {
      resolveFirst([saleResult({ idDisco: 1, artista: 'Stale Artist', album: 'Stale Album' })])
      await first
    })
    expect(screen.queryByText('Stale Artist — Stale Album')).not.toBeInTheDocument()
    expect(screen.getByText('Current Artist — Current Album')).toBeInTheDocument()
  })

  it('clears results and cancels work without falling back to the inventory endpoint', async () => {
    vi.useFakeTimers()
    api.discos.buscarVenta.mockResolvedValue([saleResult()])
    render(<NuevaVenta />)
    const input = screen.getByPlaceholderText('Buscar disco disponible para agregar…')

    fireEvent.change(input, { target: { value: 'radio' } })
    await act(async () => { await vi.advanceTimersByTimeAsync(300) })
    expect(screen.getByText('Search Artist — Search Album')).toBeInTheDocument()

    const signal = api.discos.buscarVenta.mock.calls[0][2]
    fireEvent.change(input, { target: { value: '' } })
    expect(signal.aborted).toBe(true)
    expect(screen.queryByText('Search Artist — Search Album')).not.toBeInTheDocument()
    expect(api.discos.disponibles).not.toHaveBeenCalled()
  })

  it('does not present an aborted search as an application error', async () => {
    vi.useFakeTimers()
    api.discos.buscarVenta.mockImplementation((query, limit, signal) => new Promise((resolve, reject) => {
      signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
    }))
    render(<NuevaVenta />)
    const input = screen.getByPlaceholderText('Buscar disco disponible para agregar…')

    fireEvent.change(input, { target: { value: 'et' } })
    await act(async () => { await vi.advanceTimersByTimeAsync(300) })
    fireEvent.change(input, { target: { value: 'eterna' } })
    await act(async () => {})

    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('shows local loading, errors, copy count, and no copy selector', async () => {
    vi.useFakeTimers()
    let rejectSearch
    api.discos.buscarVenta.mockImplementationOnce(() => new Promise((resolve, reject) => { rejectSearch = reject }))
    render(<NuevaVenta />)
    const input = screen.getByPlaceholderText('Buscar disco disponible para agregar…')

    fireEvent.change(input, { target: { value: 'error' } })
    await act(async () => { await vi.advanceTimersByTimeAsync(300) })
    expect(screen.getByRole('status')).toHaveTextContent('Buscando discos…')
    await act(async () => { rejectSearch(new Error('Fallo controlado')) })
    expect(screen.getByRole('alert')).toHaveTextContent('Fallo controlado')

    api.discos.buscarVenta.mockResolvedValueOnce([saleResult({ availableCopyCount: 2 })])
    fireEvent.change(input, { target: { value: 'radio' } })
    await act(async () => { await vi.advanceTimersByTimeAsync(300) })
    expect(screen.getByText(/2 copias disponibles/)).toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: /copia/i })).not.toBeInTheDocument()
  })

  it('keeps normal NEW text sales product-level and omits copyId from submission', async () => {
    vi.useFakeTimers()
    api.discos.buscarVenta.mockResolvedValue([saleResult()])
    render(<NuevaVenta />)
    const input = screen.getByPlaceholderText('Buscar disco disponible para agregar…')
    fireEvent.change(input, { target: { value: 'radio' } })
    await act(async () => { await vi.advanceTimersByTimeAsync(300) })
    fireEvent.click(screen.getByRole('button', { name: '+ Agregar' }))

    const clientInput = screen.getByPlaceholderText('Buscar por nombre, cédula, Instagram, teléfono o dirección…')
    fireEvent.change(clientInput, { target: { value: 'Ana' } })
    await act(async () => { await vi.advanceTimersByTimeAsync(300) })
    fireEvent.click(screen.getByRole('button', { name: /Ana Pérez/ }))
    await act(async () => {})
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar venta' }))
    await act(async () => {})

    expect(api.ventas.registrar).toHaveBeenCalledTimes(1)
    const submitted = api.ventas.registrar.mock.calls[0][0].detalles[0]
    expect(submitted.idDisco).toBe(81)
    expect(submitted).not.toHaveProperty('copyId')
    expect(submitted.codigoQr).toBeUndefined()
  })

  it('auto-selects a single manual USED copy and stores exact identity at quantity one', async () => {
    vi.useFakeTimers()
    api.discos.buscarVenta.mockResolvedValue([exactSaleResult({
      availableCopyCount: 1,
      availableCopies: [exactCopy({ copyId: 901, copyNumber: 1, codigoQr: 'exact-901' })],
    })])
    render(<NuevaVenta />)
    fireEvent.change(screen.getByPlaceholderText('Buscar disco disponible para agregar…'), { target: { value: 'eterna' } })
    await act(async () => { await vi.advanceTimersByTimeAsync(300) })

    expect(screen.getByText(/Seleccionada: Copia 1/)).toHaveTextContent('LO · NM · $1.100')
    fireEvent.click(screen.getByRole('button', { name: '+ Agregar' }))

    expect(screen.getByText('Copia 1 · LO · NM')).toBeInTheDocument()
    expect(screen.getByLabelText('Cantidad fija para copia exacta')).toBeDisabled()
    expect(screen.getByDisplayValue('1100')).toBeInTheDocument()
  })

  it('auto-selects the exact row for ordinary USED inventory without manual provenance', async () => {
    vi.useFakeTimers()
    api.discos.buscarVenta.mockResolvedValue([saleResult({
      idDisco: 91,
      condicion: 'USADO',
      requiresExactCopySelection: true,
      availableCopyCount: 1,
      availableCopies: [exactCopy({
        copyId: 911,
        copyNumber: 1,
        codigoQr: 'ordinary-used-911',
        precioVenta: 875,
        condicionFisica: 'VG',
        sourceCustomerCode: null,
        normalizedSourceCustomerCode: null,
        manualBatchId: null,
      })],
    })])
    render(<NuevaVenta />)
    fireEvent.change(screen.getByPlaceholderText('Buscar disco disponible para agregar…'), { target: { value: 'ordinary' } })
    await act(async () => { await vi.advanceTimersByTimeAsync(300) })

    expect(screen.getByText(/Seleccionada: Copia 1/)).toHaveTextContent('Sin origen registrado · VG · $875')
    fireEvent.click(screen.getByRole('button', { name: '+ Agregar' }))
    expect(screen.getByText('Copia 1 · Sin origen registrado · VG')).toBeInTheDocument()
    expect(screen.getByLabelText('Cantidad fija para copia exacta')).toBeDisabled()
    expect(screen.getByDisplayValue('875')).toBeInTheDocument()
  })

  it('requires a choice among multiple copies and keeps LO/NM and SV3/VG+ prices independent', async () => {
    vi.useFakeTimers()
    api.discos.buscarVenta.mockResolvedValue([exactSaleResult()])
    render(<NuevaVenta />)
    fireEvent.change(screen.getByPlaceholderText('Buscar disco disponible para agregar…'), { target: { value: 'eterna' } })
    await act(async () => { await vi.advanceTimersByTimeAsync(300) })

    const copy1 = screen.getByRole('button', { name: /Copia 1.*LO.*NM.*1\.100/ })
    const copy2 = screen.getByRole('button', { name: /Copia 2.*SV3.*VG\+.*1\.250/ })
    expect(copy1).toHaveAttribute('aria-pressed', 'false')
    expect(copy2).toHaveAttribute('aria-pressed', 'false')
    fireEvent.click(screen.getByRole('button', { name: '+ Agregar' }))
    expect(screen.getByText('Seleccioná una copia física antes de agregar el disco.')).toBeInTheDocument()

    fireEvent.click(copy2)
    expect(screen.getByText(/Seleccionada: Copia 2/)).toHaveTextContent('SV3 · VG+ · $1.250')
    fireEvent.click(screen.getByRole('button', { name: '+ Agregar' }))
    expect(screen.getByText('Copia 2 · SV3 · VG+')).toBeInTheDocument()
    expect(screen.getByDisplayValue('1250')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Copia 2.*En la venta/ })).toBeDisabled()

    fireEvent.click(screen.getByRole('button', { name: /Copia 1.*LO.*NM.*1\.100/ }))
    fireEvent.click(screen.getByRole('button', { name: '+ Agregar' }))
    expect(screen.getByText('Copia 1 · LO · NM')).toBeInTheDocument()
    expect(screen.getAllByLabelText('Cantidad fija para copia exacta')).toHaveLength(2)

    const clientInput = screen.getByPlaceholderText('Buscar por nombre, cédula, Instagram, teléfono o dirección…')
    fireEvent.change(clientInput, { target: { value: 'Ana' } })
    await act(async () => { await vi.advanceTimersByTimeAsync(300) })
    fireEvent.click(screen.getByRole('button', { name: /Ana Pérez/ }))
    await act(async () => {})
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar venta' }))
    await act(async () => {})

    expect(api.ventas.registrar).toHaveBeenCalledTimes(1)
    expect(api.ventas.registrar.mock.calls[0][0].detalles).toEqual(expect.arrayContaining([
      expect.objectContaining({ idDisco: 90, copyId: 902, codigoQr: 'exact-902', cantidad: 1, precioUnitario: 1250 }),
      expect.objectContaining({ idDisco: 90, copyId: 901, codigoQr: 'exact-901', cantidad: 1, precioUnitario: 1100 }),
    ]))
  })

  it('prefers the single matching LO copy when search is an exact logical source', async () => {
    vi.useFakeTimers()
    api.discos.buscarVenta.mockResolvedValue([exactSaleResult()])
    render(<NuevaVenta />)
    fireEvent.change(screen.getByPlaceholderText('Buscar disco disponible para agregar…'), { target: { value: ' lo ' } })
    await act(async () => { await vi.advanceTimersByTimeAsync(300) })

    expect(screen.getByText(/Seleccionada: Copia 1/)).toHaveTextContent('LO · NM · $1.100')
    fireEvent.click(screen.getByRole('button', { name: '+ Agregar' }))
    expect(screen.getByText('Copia 1 · LO · NM')).toBeInTheDocument()
  })

  it('uses a compact selector for five copies and falls back to product price when copy price is null', async () => {
    vi.useFakeTimers()
    const copies = Array.from({ length: 5 }, (_, index) => exactCopy({
      copyId: 901 + index,
      copyNumber: index + 1,
      codigoQr: `exact-${901 + index}`,
      sourceCustomerCode: `SRC${index + 1}`,
      normalizedSourceCustomerCode: `SRC${index + 1}`,
      precioVenta: index === 4 ? null : 1100 + index * 25,
    }))
    api.discos.buscarVenta.mockResolvedValue([exactSaleResult({ availableCopyCount: 5, availableCopies: copies })])
    render(<NuevaVenta />)
    fireEvent.change(screen.getByPlaceholderText('Buscar disco disponible para agregar…'), { target: { value: 'eterna' } })
    await act(async () => { await vi.advanceTimersByTimeAsync(300) })

    const selector = screen.getByRole('combobox', { name: /Seleccionar copia de Exact Artist/ })
    fireEvent.change(selector, { target: { value: '905' } })
    expect(screen.getByText(/Seleccionada: Copia 5/)).toHaveTextContent('SRC5 · NM · $1.200')
    fireEvent.click(screen.getByRole('button', { name: '+ Agregar' }))
    expect(screen.getByDisplayValue('1200')).toBeInTheDocument()
  })

  it('keeps the cart after an authoritative stale-copy checkout conflict', async () => {
    vi.useFakeTimers()
    api.discos.buscarVenta.mockResolvedValue([exactSaleResult({
      availableCopyCount: 1,
      availableCopies: [exactCopy()],
    })])
    api.ventas.registrar.mockRejectedValue(new Error('La copia seleccionada ya no está disponible.'))
    render(<NuevaVenta />)
    fireEvent.change(screen.getByPlaceholderText('Buscar disco disponible para agregar…'), { target: { value: 'eterna' } })
    await act(async () => { await vi.advanceTimersByTimeAsync(300) })
    fireEvent.click(screen.getByRole('button', { name: '+ Agregar' }))

    fireEvent.change(screen.getByPlaceholderText('Buscar por nombre, cédula, Instagram, teléfono o dirección…'), { target: { value: 'Ana' } })
    await act(async () => { await vi.advanceTimersByTimeAsync(300) })
    fireEvent.click(screen.getByRole('button', { name: /Ana Pérez/ }))
    await act(async () => {})
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar venta' }))
    await act(async () => {})

    expect(screen.getByText('La copia seleccionada ya no está disponible.')).toBeInTheDocument()
    expect(screen.getByText('Copia 1 · LO · NM')).toBeInTheDocument()
  })
})

function saleResult(overrides = {}) {
  return {
    idDisco: 81,
    artista: 'Search Artist',
    album: 'Search Album',
    codigoInterno: 'SEARCH-81',
    estado: 'DISPONIBLE',
    condicion: 'NUEVO',
    precioVenta: 1250,
    availableCopyCount: 1,
    availableCopies: [{ copyId: 811, copyNumber: 1, codigoQr: 'search-copy-1', estado: 'DISPONIBLE' }],
    requiresExactCopySelection: false,
    ...overrides,
  }
}

function exactCopy(overrides = {}) {
  return {
    copyId: 901,
    copyNumber: 1,
    codigoQr: 'exact-901',
    precioVenta: 1100,
    condicionFisica: 'NM',
    sourceCustomerCode: 'LO',
    normalizedSourceCustomerCode: 'LO',
    manualBatchId: 71,
    estado: 'DISPONIBLE',
    ...overrides,
  }
}

function exactSaleResult(overrides = {}) {
  return saleResult({
    idDisco: 90,
    artista: 'Exact Artist',
    album: 'Exact Album',
    codigoInterno: 'EXACT-90',
    precioVenta: 1200,
    condicion: 'USADO',
    requiresExactCopySelection: true,
    availableCopyCount: 2,
    availableCopies: [
      exactCopy(),
      exactCopy({
        copyId: 902,
        copyNumber: 2,
        codigoQr: 'exact-902',
        precioVenta: 1250,
        condicionFisica: 'VG+',
        sourceCustomerCode: 'SV3',
        normalizedSourceCustomerCode: 'SV3',
        manualBatchId: 72,
      }),
    ],
    ...overrides,
  })
}

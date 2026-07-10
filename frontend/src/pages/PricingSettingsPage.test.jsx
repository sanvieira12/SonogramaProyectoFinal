import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PricingSettingsPage from './PricingSettingsPage'
import { api } from '../api/sonograma'

vi.mock('../api/sonograma', () => ({
  api: {
    pricing: {
      settings: vi.fn(),
      preview: vi.fn(),
      apply: vi.fn(),
      updateMarkup: vi.fn(),
      reset: vi.fn(),
    },
  },
}))

const settings = {
  eurUyuRate: 49.5,
  extraCostSingleEur: 5,
  extraCostDoubleEur: 8,
  extraCostMultiEur: 9,
  markupSingle: 1.7,
  markupDouble: 1.5,
  markupMulti: 1.4,
}

const rows = [
  {
    idDisco: 1,
    invoiceNumber: 'A-1',
    invoiceDate: '2026-07-10',
    supplier: 'Proveedor 1',
    shipping: 12,
    code: 'COD-1',
    artist: 'Artista 1',
    title: 'Título 1',
    format: 'LP',
    type: 'SINGLE',
    unitPriceEur: 10,
    quantity: 1,
    unitLineTotalEur: 10,
    extraCostEur: 5,
    realCostEur: 15,
    realCostUyu: 742.5,
    markup: 1.7,
    finalSalePriceUyu: 1260,
    pricingMode: 'AUTO',
  },
  {
    idDisco: 2,
    invoiceNumber: 'B-2',
    invoiceDate: '2026-07-11',
    supplier: 'Proveedor Dos',
    shipping: 18,
    code: 'ZX-200',
    artist: 'Árbol Negro',
    title: 'Canción Íntima',
    format: '2x12"',
    type: 'DOUBLE',
    unitPriceEur: 12,
    quantity: 1,
    unitLineTotalEur: 12,
    extraCostEur: 8,
    realCostEur: 20,
    realCostUyu: 990,
    markup: 1.5,
    finalSalePriceUyu: 1480,
    pricingMode: 'MANUAL',
  },
]

function renderPage() {
  return render(<PricingSettingsPage />)
}

describe('PricingSettingsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.pricing.settings.mockResolvedValue(settings)
    api.pricing.preview.mockResolvedValue({ rows })
    api.pricing.apply.mockResolvedValue({ updatedCount: 1 })
    api.pricing.updateMarkup.mockResolvedValue({
      idDisco: 1,
      markup: 2.1,
      finalSalePriceUyu: 1560,
      pricingMode: 'MANUAL',
    })
  })

  it('renderiza labels en español y deshabilita aplicar a seleccionados sin filas marcadas', async () => {
    renderPage()

    await screen.findByText('Cotización EUR/UYU')
    expect(screen.getByText('Costo extra disco simple (EUR)')).toBeInTheDocument()
    expect(screen.getByText('Actualizar vista previa')).toBeInTheDocument()
    expect(screen.getByText('Discos en la vista previa')).toBeInTheDocument()
    expect(screen.getByText('Ningún disco seleccionado')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Aplicar a seleccionados' })).toBeDisabled()
  })

  it('ya no renderiza el helper text y usa la columna Actualizar para guardar markup', async () => {
    renderPage()

    await screen.findByRole('columnheader', { name: 'Actualizar' })
    expect(screen.queryByText('Multiplicador aplicado al costo real')).not.toBeInTheDocument()

    const headers = screen.getAllByRole('columnheader')
    expect(headers.some(header => header.textContent?.includes('Actualizar'))).toBe(true)

    const saveButtons = screen.getAllByRole('button', { name: /Guardar markup del disco/i })
    expect(saveButtons).toHaveLength(2)
  })

  it('busca por artista de forma case-insensitive y accent-insensitive', async () => {
    renderPage()

    await screen.findByText('2 resultados')

    const searchInput = screen.getByRole('searchbox', { name: 'Buscar discos en stock' })
    fireEvent.change(searchInput, { target: { value: 'arbol negro' } })

    expect(screen.getByText('1 resultado')).toBeInTheDocument()
    expect(screen.getByText('Árbol Negro')).toBeInTheDocument()
    expect(screen.queryByText('Artista 1')).not.toBeInTheDocument()
  })

  it('busca por título, código y proveedor', async () => {
    renderPage()

    await screen.findByText('2 resultados')
    const searchInput = screen.getByRole('searchbox', { name: 'Buscar discos en stock' })

    fireEvent.change(searchInput, { target: { value: 'Título 1' } })
    expect(screen.getByText('1 resultado')).toBeInTheDocument()
    expect(screen.getByText('Artista 1')).toBeInTheDocument()

    fireEvent.change(searchInput, { target: { value: 'ZX-200' } })
    expect(screen.getByText('Árbol Negro')).toBeInTheDocument()

    fireEvent.change(searchInput, { target: { value: 'proveedor dos' } })
    expect(screen.getByText('Árbol Negro')).toBeInTheDocument()
  })

  it('muestra empty state en español y limpiar búsqueda restaura todas las filas', async () => {
    renderPage()

    await screen.findByText('2 resultados')
    const searchInput = screen.getByRole('searchbox', { name: 'Buscar discos en stock' })
    fireEvent.change(searchInput, { target: { value: 'sin-match' } })

    expect(screen.getAllByText('Sin resultados para esta búsqueda')).toHaveLength(2)

    fireEvent.click(screen.getByRole('button', { name: 'Limpiar búsqueda' }))

    expect(await screen.findByText('2 resultados')).toBeInTheDocument()
    expect(screen.getByText('Artista 1')).toBeInTheDocument()
    expect(screen.getByText('Árbol Negro')).toBeInTheDocument()
  })

  it('header select-all afecta solo filas visibles filtradas y mantiene seleccionados ocultos', async () => {
    renderPage()

    await screen.findByText('2 resultados')

    fireEvent.click(screen.getByRole('checkbox', { name: 'Seleccionar disco Artista 1' }))
    expect(screen.getByText('1 disco seleccionado')).toBeInTheDocument()

    const searchInput = screen.getByRole('searchbox', { name: 'Buscar discos en stock' })
    fireEvent.change(searchInput, { target: { value: 'Árbol' } })

    expect(screen.getByText('1 resultado')).toBeInTheDocument()
    expect(screen.getByText('1 disco seleccionado')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('checkbox', { name: 'Seleccionar todos los discos visibles' }))
    expect(screen.getByText('2 discos seleccionados')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Aplicar a seleccionados' }))
    fireEvent.click(screen.getByRole('button', { name: 'Aplicar cambios' }))

    await waitFor(() => {
      expect(api.pricing.apply).toHaveBeenCalledWith(
        expect.objectContaining({ eurUyuRate: 49.5 }),
        'selected',
        expect.arrayContaining([1, 2]),
      )
    })
  })

  it('deseleccionar todos limpia toda la selección actual', async () => {
    renderPage()

    await screen.findByText('2 resultados')
    fireEvent.click(screen.getByRole('button', { name: 'Seleccionar todos' }))
    expect(screen.getByText('2 discos seleccionados')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Deseleccionar todos' }))
    expect(screen.getByText('Ningún disco seleccionado')).toBeInTheDocument()
  })

  it('mantiene la selección después de actualizar la vista previa', async () => {
    renderPage()

    await screen.findByText('2 resultados')

    const checkbox = screen.getByRole('checkbox', { name: 'Seleccionar disco Artista 1' })
    fireEvent.click(checkbox)
    expect(checkbox).toBeChecked()

    fireEvent.click(screen.getByRole('button', { name: 'Actualizar vista previa' }))

    await waitFor(() => {
      expect(api.pricing.preview).toHaveBeenCalledTimes(2)
    })
    expect(screen.getByRole('checkbox', { name: 'Seleccionar disco Artista 1' })).toBeChecked()
  })

  it('valida y guarda el markup individual', async () => {
    renderPage()

    await screen.findByText('2 resultados')

    const markupInput = screen.getByRole('spinbutton', { name: 'Markup del disco Artista 1' })
    fireEvent.change(markupInput, { target: { value: '' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar markup del disco Artista 1' }))

    expect(screen.getByText('Completá markup.')).toBeInTheDocument()
    expect(api.pricing.updateMarkup).not.toHaveBeenCalled()

    fireEvent.change(markupInput, { target: { value: '2.1' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar markup del disco Artista 1' }))

    await waitFor(() => {
      expect(api.pricing.updateMarkup).toHaveBeenCalledWith(1, 2.1)
    })
    expect(await screen.findByText('Markup actualizado correctamente.')).toBeInTheDocument()
  })

  it('el botón de guardar markup está en la última columna reutilizada', async () => {
    renderPage()

    await screen.findByText('2 resultados')
    const row = screen.getByRole('checkbox', { name: 'Seleccionar disco Artista 1' }).closest('tr')
    const cells = within(row).getAllByRole('cell')
    const lastCell = cells[cells.length - 1]

    expect(within(lastCell).getByRole('button', { name: 'Guardar markup del disco Artista 1' })).toBeInTheDocument()
  })
})

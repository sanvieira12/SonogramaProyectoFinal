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
    supplier: 'Future',
    shipping: 'UPS',
    code: 'COD-1',
    artist: 'Artista 1',
    title: 'Título 1',
    format: 'LP',
    type: 'SINGLE',
    unitPriceEur: 13.3644,
    quantity: 1,
    unitLineTotalEur: 13.3644,
    extraCostEur: 5.1256,
    realCostEur: 18.49,
    realCostUyu: 912.8513,
    markup: 1.7,
    finalSalePriceUyu: 2819.7364,
    pricingMode: 'AUTO',
    condicion: 'NUEVO',
  },
  {
    idDisco: 2,
    invoiceNumber: 'B-2',
    invoiceDate: '2026-07-11',
    supplier: 'Discogs',
    shipping: 'Correo',
    code: 'ZX-200',
    artist: 'Árbol Negro',
    title: 'Canción Íntima',
    format: '2x12"',
    type: 'DOUBLE',
    unitPriceEur: 12.75,
    quantity: 1,
    unitLineTotalEur: 12.75,
    extraCostEur: 8.125,
    realCostEur: 20.875,
    realCostUyu: 1031.62375,
    markup: 1.5,
    finalSalePriceUyu: 1480.5,
    pricingMode: 'MANUAL',
    condicion: 'USADO',
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
      markup: 2.1234,
      finalSalePriceUyu: 1560.8754,
      pricingMode: 'MANUAL',
    })
  })

  it('renderiza labels en español y deshabilita aplicar a seleccionados sin filas marcadas', async () => {
    renderPage()

    await screen.findByText('Cotización EUR/UYU')
    expect(screen.getByText('Costo extra disco simple (EUR)')).toBeInTheDocument()
    expect(screen.getByText('Actualizar vista previa')).toBeInTheDocument()
    expect(screen.getByText('Discos en la vista previa')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Aplicar a seleccionados' })).toBeDisabled()
  })

  it('ya no renderiza el helper text y usa la columna Actualizar para guardar markup', async () => {
    renderPage()

    await screen.findByRole('columnheader', { name: 'Actualizar' })
    expect(screen.queryByText('Multiplicador aplicado al costo real')).not.toBeInTheDocument()
    expect(screen.queryByText('Vista previa de recálculo')).not.toBeInTheDocument()
    expect(screen.queryByText('Impacto de los costos y precios con la configuración actual.')).not.toBeInTheDocument()
    expect(screen.queryByText('Ningún disco seleccionado')).not.toBeInTheDocument()

    const headers = screen.getAllByRole('columnheader')
    expect(headers.some(header => header.textContent?.includes('Actualizar'))).toBe(true)

    const saveButtons = screen.getAllByRole('button', { name: /Guardar markup del disco/i })
    expect(saveButtons).toHaveLength(2)
  })

  it('mantiene Guardar markup y Guardando… en una sola línea con clases compactas', async () => {
    let resolveUpdate
    api.pricing.updateMarkup.mockImplementation(() => new Promise(resolve => {
      resolveUpdate = resolve
    }))

    renderPage()

    await screen.findByText('2 resultados')
    const button = screen.getByRole('button', { name: 'Guardar markup del disco Artista 1' })
    expect(button.className).toContain('whitespace-nowrap')
    expect(button.className).toContain('min-w-[140px]')

    fireEvent.click(button)

    const savingButton = await screen.findByRole('button', { name: 'Guardar markup del disco Artista 1' })
    expect(savingButton).toHaveTextContent('Guardando…')
    expect(savingButton.className).toContain('whitespace-nowrap')

    resolveUpdate({
      idDisco: 1,
      markup: 2.1234,
      finalSalePriceUyu: 1560.8754,
      pricingMode: 'MANUAL',
    })

    await screen.findByText('Markup actualizado correctamente.')
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

    fireEvent.change(searchInput, { target: { value: 'discogs' } })
    expect(screen.getByText('Árbol Negro')).toBeInTheDocument()
  })

  it('selecciona Todos por defecto y filtra por condición actualizando el contador', async () => {
    renderPage()

    await screen.findByText('2 resultados')
    expect(screen.getByRole('button', { name: 'Todos' })).toHaveAttribute('aria-pressed', 'true')

    fireEvent.click(screen.getByRole('button', { name: 'Nuevos' }))
    expect(screen.getByText('1 resultado')).toBeInTheDocument()
    expect(screen.getByText('Artista 1')).toBeInTheDocument()
    expect(screen.queryByText('Árbol Negro')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Usados' }))
    expect(screen.getByText('1 resultado')).toBeInTheDocument()
    expect(screen.getByText('Árbol Negro')).toBeInTheDocument()
    expect(screen.queryByText('Artista 1')).not.toBeInTheDocument()
  })

  it('combina el filtro de condición con la búsqueda de texto', async () => {
    renderPage()

    await screen.findByText('2 resultados')
    fireEvent.click(screen.getByRole('button', { name: 'Usados' }))
    fireEvent.change(screen.getByRole('searchbox', { name: 'Buscar discos en stock' }), {
      target: { value: 'ZX-200' },
    })

    expect(screen.getByText('1 resultado')).toBeInTheDocument()
    expect(screen.getByText('Árbol Negro')).toBeInTheDocument()
    expect(screen.queryByText('Artista 1')).not.toBeInTheDocument()
  })

  it('normaliza alias de condición nuevos y usados', async () => {
    api.pricing.preview.mockResolvedValue({
      rows: [
        { ...rows[0], condicion: 'new' },
        { ...rows[1], condicion: 'used' },
      ],
    })

    renderPage()

    await screen.findByText('2 resultados')
    fireEvent.click(screen.getByRole('button', { name: 'Nuevos' }))
    expect(screen.getByText('1 resultado')).toBeInTheDocument()
    expect(screen.getByText('Artista 1')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Usados' }))
    expect(screen.getByText('1 resultado')).toBeInTheDocument()
    expect(screen.getByText('Árbol Negro')).toBeInTheDocument()
  })

  it('limpiar búsqueda también restablece la condición a Todos', async () => {
    renderPage()

    await screen.findByText('2 resultados')
    fireEvent.click(screen.getByRole('button', { name: 'Usados' }))
    fireEvent.change(screen.getByRole('searchbox', { name: 'Buscar discos en stock' }), {
      target: { value: 'Discogs' },
    })

    fireEvent.click(screen.getByRole('button', { name: 'Limpiar búsqueda' }))

    expect(screen.getByRole('button', { name: 'Todos' })).toHaveAttribute('aria-pressed', 'true')
    expect(await screen.findByText('2 resultados')).toBeInTheDocument()
    expect(screen.getByRole('searchbox', { name: 'Buscar discos en stock' })).toHaveValue('')
    expect(screen.getByText('Artista 1')).toBeInTheDocument()
    expect(screen.getByText('Árbol Negro')).toBeInTheDocument()
  })

  it('mantiene Todos para condiciones ausentes o inesperadas y las excluye de Nuevos/Usados', async () => {
    const rowsWithUnexpectedConditions = [
      ...rows,
      { ...rows[0], idDisco: 3, artist: 'Sin condición', condicion: null },
      { ...rows[0], idDisco: 4, artist: 'Condición externa', condicion: 'CONSIGNACION' },
    ]
    api.pricing.preview.mockResolvedValue({ rows: rowsWithUnexpectedConditions })

    renderPage()

    await screen.findByText('4 resultados')
    expect(screen.getByRole('button', { name: 'Todos' })).toHaveAttribute('aria-pressed', 'true')

    fireEvent.click(screen.getByRole('button', { name: 'Nuevos' }))
    expect(screen.getByText('1 resultado')).toBeInTheDocument()
    expect(screen.queryByText('Sin condición')).not.toBeInTheDocument()
    expect(screen.queryByText('Condición externa')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Usados' }))
    expect(screen.getByText('1 resultado')).toBeInTheDocument()
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

    const searchInput = screen.getByRole('searchbox', { name: 'Buscar discos en stock' })
    fireEvent.change(searchInput, { target: { value: 'Árbol' } })

    expect(screen.getByText('1 resultado')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('checkbox', { name: 'Seleccionar todos los discos visibles' }))

    fireEvent.click(screen.getByRole('button', { name: 'Aplicar a seleccionados' }))
    fireEvent.click(screen.getByRole('button', { name: 'Aplicar cambios' }))

    await waitFor(() => {
      expect(api.pricing.apply).toHaveBeenCalledWith(
        expect.objectContaining({ eurUyuRate: '49.5' }),
        'selected',
        expect.arrayContaining([1, 2]),
      )
    })
  })

  it('deseleccionar todos limpia toda la selección actual', async () => {
    renderPage()

    await screen.findByText('2 resultados')
    fireEvent.click(screen.getByRole('button', { name: 'Seleccionar visibles' }))

    fireEvent.click(screen.getByRole('button', { name: 'Deseleccionar visibles' }))
    expect(screen.getByRole('button', { name: 'Aplicar a seleccionados' })).toBeDisabled()
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

  it('ordena proveedor en ambos sentidos y permite volver al orden base', async () => {
    renderPage()

    await screen.findByText('2 resultados')

    const supplierSort = screen.getByRole('button', { name: 'Ordenar proveedor de A a Z' })
    fireEvent.click(supplierSort)

    let supplierCells = screen.getAllByRole('cell').filter(cell => cell.textContent === 'Discogs' || cell.textContent === 'Future')
    expect(supplierCells[0]).toHaveTextContent('Discogs')
    expect(screen.getByRole('columnheader', { name: /Proveedor/ })).toHaveAttribute('aria-sort', 'ascending')

    fireEvent.click(screen.getByRole('button', { name: 'Ordenar proveedor de Z a A' }))
    supplierCells = screen.getAllByRole('cell').filter(cell => cell.textContent === 'Discogs' || cell.textContent === 'Future')
    expect(supplierCells[0]).toHaveTextContent('Future')
    expect(screen.getByRole('columnheader', { name: /Proveedor/ })).toHaveAttribute('aria-sort', 'descending')

    fireEvent.click(screen.getByRole('button', { name: 'Restablecer orden de proveedor' }))
    supplierCells = screen.getAllByRole('cell').filter(cell => cell.textContent === 'Discogs' || cell.textContent === 'Future')
    expect(supplierCells[0]).toHaveTextContent('Future')
    expect(screen.getByRole('columnheader', { name: /Proveedor/ })).toHaveAttribute('aria-sort', 'none')
  })

  it('valida y guarda el markup individual', async () => {
    renderPage()

    await screen.findByText('2 resultados')

    const markupInput = screen.getByRole('spinbutton', { name: 'Markup del disco Artista 1' })
    fireEvent.change(markupInput, { target: { value: '' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar markup del disco Artista 1' }))

    expect(screen.getByText('Completá markup.')).toBeInTheDocument()
    expect(api.pricing.updateMarkup).not.toHaveBeenCalled()

    fireEvent.change(markupInput, { target: { value: '2.1234' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar markup del disco Artista 1' }))

    await waitFor(() => {
      expect(api.pricing.updateMarkup).toHaveBeenCalledWith(1, '2.1234')
    })
    expect(await screen.findByText('Markup actualizado correctamente.')).toBeInTheDocument()
  })

  it('no renderiza Manual ni Automático en la columna Actualizar', async () => {
    renderPage()

    await screen.findByText('2 resultados')

    const firstRow = screen.getByRole('checkbox', { name: 'Seleccionar disco Artista 1' }).closest('tr')
    const secondRow = screen.getByRole('checkbox', { name: 'Seleccionar disco Árbol Negro' }).closest('tr')
    const firstLastCell = within(firstRow).getAllByRole('cell').at(-1)
    const secondLastCell = within(secondRow).getAllByRole('cell').at(-1)

    expect(within(firstLastCell).queryByText('Automático')).not.toBeInTheDocument()
    expect(within(secondLastCell).queryByText('Manual')).not.toBeInTheDocument()
  })

  it('muestra precios finales con decimales sin forzar cero decimales', async () => {
    renderPage()

    await screen.findByText('2 resultados')

    expect(screen.getByText('2.819,7364')).toBeInTheDocument()
    expect(screen.getByText('1.480,5')).toBeInTheDocument()
    expect(screen.queryByText('2.820')).not.toBeInTheDocument()
  })

  it('formatea para mostrar sin alterar el valor usado en el guardado', async () => {
    renderPage()

    await screen.findByText('2 resultados')
    expect(screen.getAllByText('13,3644').length).toBeGreaterThan(0)

    const markupInput = screen.getByRole('spinbutton', { name: 'Markup del disco Artista 1' })
    fireEvent.change(markupInput, { target: { value: '1.4567' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar markup del disco Artista 1' }))

    await waitFor(() => {
      expect(api.pricing.updateMarkup).toHaveBeenCalledWith(1, '1.4567')
    })
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

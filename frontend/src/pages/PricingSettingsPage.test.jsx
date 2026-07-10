import { fireEvent, render, screen, waitFor } from '@testing-library/react'
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
    invoiceNumber: 'A-2',
    invoiceDate: '2026-07-11',
    supplier: 'Proveedor 2',
    shipping: 12,
    code: 'COD-2',
    artist: 'Artista 2',
    title: 'Título 2',
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
    render(<PricingSettingsPage />)

    await screen.findByText('Cotización EUR/UYU')
    expect(screen.getByText('Costo extra disco simple (EUR)')).toBeInTheDocument()
    expect(screen.getByText('Actualizar vista previa')).toBeInTheDocument()
    expect(screen.getByText('Discos en la vista previa')).toBeInTheDocument()
    expect(screen.getByText('Ningún disco seleccionado')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Aplicar a seleccionados' })).toBeDisabled()
  })

  it('selecciona y deselecciona todos los visibles mostrando el contador correcto', async () => {
    render(<PricingSettingsPage />)

    await screen.findByText('Artista 1')

    fireEvent.click(screen.getByRole('button', { name: 'Seleccionar todos' }))
    expect(screen.getByText('2 discos seleccionados')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Deseleccionar todos' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Deseleccionar todos' }))
    expect(screen.getByText('Ningún disco seleccionado')).toBeInTheDocument()
  })

  it('envía solo los ids seleccionados al aplicar a seleccionados', async () => {
    render(<PricingSettingsPage />)

    await screen.findByText('Artista 1')

    fireEvent.click(screen.getByRole('checkbox', { name: 'Seleccionar disco Artista 1' }))
    expect(screen.getByText('1 disco seleccionado')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Aplicar a seleccionados' }))
    expect(
      screen.getByText('Se recalcularán los precios de 1 discos seleccionados. Los discos no seleccionados no se modificarán.'),
    ).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Aplicar cambios' }))

    await waitFor(() => {
      expect(api.pricing.apply).toHaveBeenCalledWith(
        expect.objectContaining({
          eurUyuRate: 49.5,
          markupSingle: 1.7,
        }),
        'selected',
        [1],
      )
    })
    expect(api.pricing.apply).not.toHaveBeenCalledWith(expect.anything(), 'selected', [2])
    await screen.findByText('Cambios aplicados correctamente a 1 discos.')
  })

  it('mantiene la selección después de actualizar la vista previa', async () => {
    render(<PricingSettingsPage />)

    await screen.findByText('Artista 1')

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
    render(<PricingSettingsPage />)

    await screen.findByText('Artista 1')

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
})

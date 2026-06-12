import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import ClienteImportResult from './ClienteImportResult'

describe('ClienteImportResult', () => {
  it('muestra el resumen y el reporte recibido del backend', () => {
    render(
      <ClienteImportResult
        onClose={vi.fn()}
        result={{
          hoja: 'Hoja 1',
          totalFilasLeidas: 8,
          clientesValidos: 8,
          creados: 6,
          actualizados: 2,
          omitidos: 0,
          filasConIncidencias: 0,
          filas: [{
            filaExcel: 3,
            nombre: 'Ismael Gonzalez',
            cedula: '46410184',
            telefono: '99860877',
            estado: 'created',
            mensaje: 'Cliente creado',
          }, {
            filaExcel: 4,
            nombre: 'Enzo Ferraro',
            cedula: '51575632',
            telefono: '95442045',
            estado: 'updated',
            mensaje: 'Cliente existente actualizado',
          }],
        }}
      />,
    )

    expect(screen.getByText('Importación completada')).toBeInTheDocument()
    expect(screen.getByText('Hoja: Hoja 1')).toBeInTheDocument()
    expect(screen.getByText('Ismael Gonzalez')).toBeInTheDocument()
    expect(screen.getByText('Creado')).toBeInTheDocument()
    expect(screen.getByText('Actualizado')).toBeInTheDocument()
    expect(screen.getByText('99860877')).toBeInTheDocument()
  })

  it('muestra la razón real cuando la importación falla', () => {
    render(<ClienteImportResult result={{ error: 'Falta el encabezado CLIENTE' }} onClose={vi.fn()} />)
    expect(screen.getByText(/Falta el encabezado CLIENTE/)).toBeInTheDocument()
  })
})

import { describe, expect, it } from 'vitest'
import { normalizeDacSearch } from './dacBranches'

describe('normalizeDacSearch', () => {
  it('ignora mayúsculas, tildes, diéresis, guiones y espacios repetidos', () => {
    expect(normalizeDacSearch('  Río--Branco  ')).toBe('rio branco')
    expect(normalizeDacSearch('Nueva Helvecia')).toBe('nueva helvecia')
  })
})

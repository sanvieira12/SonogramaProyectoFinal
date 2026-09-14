import { describe, expect, it } from 'vitest'
import { businessDateInMontevideo } from './businessDate'

describe('businessDateInMontevideo', () => {
  it('keeps the Uruguay business date across the UTC rollover', () => {
    expect(businessDateInMontevideo(new Date('2026-09-15T01:30:00.000Z'))).toBe('2026-09-14')
    expect(businessDateInMontevideo(new Date('2026-09-15T02:59:59.000Z'))).toBe('2026-09-14')
    expect(businessDateInMontevideo(new Date('2026-09-15T03:00:00.000Z'))).toBe('2026-09-15')
  })

  it('handles month and year boundaries in business time', () => {
    expect(businessDateInMontevideo(new Date('2026-10-01T02:59:59.000Z'))).toBe('2026-09-30')
    expect(businessDateInMontevideo(new Date('2026-10-01T03:00:00.000Z'))).toBe('2026-10-01')
    expect(businessDateInMontevideo(new Date('2027-01-01T02:59:59.000Z'))).toBe('2026-12-31')
    expect(businessDateInMontevideo(new Date('2027-01-01T03:00:00.000Z'))).toBe('2027-01-01')
  })
})

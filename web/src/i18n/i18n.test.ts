import { describe, expect, it } from 'vitest'
import en from './en.json'
import fr from './fr.json'

function keys(node: unknown, prefix = ''): string[] {
  if (typeof node !== 'object' || node === null) return [prefix]
  return Object.entries(node).flatMap(([k, v]) => keys(v, prefix ? `${prefix}.${k}` : k))
}

function values(node: unknown): unknown[] {
  return typeof node === 'object' && node !== null ? Object.values(node).flatMap(values) : [node]
}

describe('translations (NFR-12)', () => {
  it('have the same keys in French and English', () => {
    expect(keys(en).sort()).toEqual(keys(fr).sort())
  })

  it('have no empty text', () => {
    for (const text of [...values(fr), ...values(en)]) {
      expect(typeof text === 'string' && text.trim().length > 0).toBe(true)
    }
  })
})

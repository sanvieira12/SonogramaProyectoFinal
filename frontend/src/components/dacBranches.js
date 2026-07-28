export function normalizeDacSearch(value) {
  return String(value || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLocaleLowerCase('es-UY')
    .replace(/[^\p{L}\p{N}]+/gu, ' ')
    .trim()
    .replace(/\s+/g, ' ')
}

export function getDacBranchId(branch) {
  return branch?.id || (branch?.codigo ? `dac-${branch.codigo}` : '')
}

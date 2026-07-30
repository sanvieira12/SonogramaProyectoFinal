export const EXPENSE_CATEGORIES = [
  { value: 'FIXED_EXPENSES', label: 'Gastos fijos' },
  { value: 'STORE_EXPENSES', label: 'Gastos secundarios' },
  { value: 'USED_ORDERS', label: 'Pedidos usados' },
  { value: 'NEW_ORDERS', label: 'Pedidos nuevos' },
]

export const CATEGORY_LABELS = Object.fromEntries(EXPENSE_CATEGORIES.map(category => [category.value, category.label]))

const LEGACY_CATEGORY_VALUES = {
  'gastos del local': 'STORE_EXPENSES',
  'gasto local': 'STORE_EXPENSES',
  'gastos de tienda': 'STORE_EXPENSES',
}

export function normalizeExpenseCategory(category) {
  if (!category) return category
  const value = String(category).trim()
  return LEGACY_CATEGORY_VALUES[value.toLocaleLowerCase('es-UY')] || value
}

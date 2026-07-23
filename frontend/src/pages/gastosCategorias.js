export const EXPENSE_CATEGORIES = [
  { value: 'FIXED_EXPENSES', label: 'Gastos fijos' },
  { value: 'STORE_EXPENSES', label: 'Gastos de tienda' },
  { value: 'USED_ORDERS', label: 'Pedidos usados' },
  { value: 'NEW_ORDERS', label: 'Pedidos nuevos' },
]

export const CATEGORY_LABELS = Object.fromEntries(EXPENSE_CATEGORIES.map(category => [category.value, category.label]))

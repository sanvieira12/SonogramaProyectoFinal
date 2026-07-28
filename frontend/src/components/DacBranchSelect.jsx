import { useEffect, useMemo, useRef, useState } from 'react'
import { api } from '../api/sonograma'
import { getDacBranchId, normalizeDacSearch } from './dacBranches'

function branchId(branch) {
  return getDacBranchId(branch)
}

function branchLabel(branch) {
  return branch?.label || `${branch?.nombre || ''} — ${branch?.direccion || ''}`
}

export default function DacBranchSelect({ department, value, onChange, disabled = false, error }) {
  const wrapperRef = useRef(null)
  const [branches, setBranches] = useState([])
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const [loadError, setLoadError] = useState('')

  useEffect(() => {
    let active = true
    if (!department) {
      return () => { active = false }
    }
    api.envios.sucursalesDac(department)
      .then(data => { if (active) setBranches(Array.isArray(data) ? data : []) })
      .catch(() => { if (active) { setBranches([]); setLoadError('No se pudieron cargar las sucursales DAC') } })
    return () => { active = false }
  }, [department])

  useEffect(() => {
    function closeOnOutside(event) {
      if (!wrapperRef.current?.contains(event.target)) setOpen(false)
    }
    document.addEventListener('mousedown', closeOnOutside)
    return () => document.removeEventListener('mousedown', closeOnOutside)
  }, [])

  const selected = useMemo(
    () => branches.find(branch => branchId(branch) === value) || null,
    [branches, value],
  )
  const filtered = useMemo(() => {
    const needle = normalizeDacSearch(query)
    if (!needle) return branches
    return branches.filter(branch => normalizeDacSearch(
      `${branch.nombre} ${branch.direccion} ${branch.label || ''}`,
    ).includes(needle))
  }, [branches, query])

  const placeholder = !department
    ? 'Seleccioná primero un departamento.'
    : 'Buscá o seleccioná una sucursal DAC'

  function select(branch) {
    onChange?.(branch || null)
    setQuery('')
    setOpen(false)
  }

  return (
    <div ref={wrapperRef} className="relative min-w-0">
      <div className="relative">
        <input
          type="text"
          className={`input w-full pr-16 ${error ? 'border-red-400' : ''}`}
          value={open ? query : (selected ? branchLabel(selected) : '')}
          onChange={event => { setQuery(event.target.value); setOpen(true) }}
          onFocus={() => { if (department && !disabled) { setQuery(''); setOpen(true) } }}
          placeholder={placeholder}
          disabled={!department || disabled}
          autoComplete="off"
          role="combobox"
          aria-expanded={open}
          aria-haspopup="listbox"
          aria-label="Sucursal DAC"
        />
        {selected && !disabled && (
          <button
            type="button"
            onClick={() => select(null)}
            className="absolute right-2 top-1/2 -translate-y-1/2 rounded px-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700 dark:hover:bg-stone-800 dark:hover:text-white"
            aria-label="Limpiar sucursal DAC"
          >
            ×
          </button>
        )}
      </div>
      {open && department && !disabled && (
        <div className="absolute z-30 mt-1 max-h-64 w-full overflow-y-auto rounded-lg border border-slate-200 bg-white p-1 shadow-xl dark:border-stone-700 dark:bg-stone-900" role="listbox">
          {filtered.length === 0 ? (
            <p className="px-3 py-2 text-sm text-slate-500 dark:text-stone-400">No hay sucursales que coincidan.</p>
          ) : filtered.map(branch => (
            <button
              type="button"
              key={branchId(branch)}
              onClick={() => select(branch)}
              className="block w-full min-w-0 rounded-md px-3 py-2 text-left text-sm text-slate-700 hover:bg-slate-100 dark:text-stone-200 dark:hover:bg-stone-800"
              role="option"
              aria-selected={branchId(branch) === value}
            >
              <span className="block break-words font-medium">{branch.nombre}</span>
              <span className="block break-words text-xs text-slate-500 dark:text-stone-400">{branch.direccion}</span>
            </button>
          ))}
        </div>
      )}
      {(error || loadError) && <p className="mt-1 text-xs text-red-500">{error || loadError}</p>}
    </div>
  )
}

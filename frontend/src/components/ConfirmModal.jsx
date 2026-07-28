import { useEffect } from 'react'

export default function ConfirmModal({ titulo, mensaje, onConfirmar, onCancelar, cargando, confirmarTexto = 'Confirmar', error }) {
  useEffect(() => {
    function handleKeyDown(event) {
      if (event.key === 'Escape' && !cargando) onCancelar()
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [cargando, onCancelar])

  return (
    <div
      className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 px-4"
      onClick={event => {
        if (event.target === event.currentTarget && !cargando) onCancelar()
      }}
      role="presentation"
    >
      <div
        className="bg-white dark:bg-stone-900 border border-slate-200 dark:border-stone-700 rounded-2xl shadow-2xl w-full max-w-sm p-6"
        onClick={e => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirm-modal-title"
      >
        <h3 id="confirm-modal-title" className="text-slate-900 dark:text-white font-bold text-base mb-2">{titulo}</h3>
        <p className="text-slate-500 dark:text-white/70 text-sm mb-6">{mensaje}</p>
        {error && <p role="alert" className="text-red-600 dark:text-red-300 text-sm mb-4">{error}</p>}
        <div className="flex gap-3">
          <button
            onClick={onCancelar}
            disabled={cargando}
            className="btn-secondary flex-1"
          >
            Cancelar
          </button>
          <button
            onClick={onConfirmar}
            disabled={cargando}
            className="flex-1 bg-red-600 hover:bg-red-700 disabled:opacity-50 text-white font-semibold rounded-lg px-4 py-2 text-sm transition-all duration-200 active:scale-95"
          >
            {cargando ? 'Eliminando...' : confirmarTexto}
          </button>
        </div>
      </div>
    </div>
  )
}

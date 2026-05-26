import { useRef, useState } from 'react'
import { api } from '../api/sonograma'

function Spinner({ text }) {
  return (
    <div className="flex items-center justify-center gap-3 py-4">
      <svg className="animate-spin w-5 h-5 text-[#7E9FA8]" viewBox="0 0 24 24" fill="none">
        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
      </svg>
      <span className="text-slate-500 dark:text-stone-400 text-sm">{text}</span>
    </div>
  )
}

function UploadIcon() {
  return (
    <svg className="w-10 h-10 text-slate-300 dark:text-stone-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 16.5V9.75m0 0 3 3m-3-3-3 3M6.75 19.5a4.5 4.5 0 0 1-1.41-8.775 5.25 5.25 0 0 1 10.233-2.33 3 3 0 0 1 3.758 3.848A3.752 3.752 0 0 1 18 19.5H6.75Z" />
    </svg>
  )
}

function CheckIcon() {
  return (
    <svg className="w-5 h-5 text-emerald-500" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="m4.5 12.75 6 6 9-13.5" />
    </svg>
  )
}

function PdfIcon() {
  return (
    <svg className="w-5 h-5 text-[#7E9FA8]" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 0 0-3.375-3.375h-1.5A1.125 1.125 0 0 1 13.5 7.125v-1.5a3.375 3.375 0 0 0-3.375-3.375H8.25m0 12.75h7.5m-7.5 3H12M10.5 2.25H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 0 0-9-9Z" />
    </svg>
  )
}

const STATES = { idle: 'idle', procesando: 'procesando', listo: 'listo', error: 'error' }

export default function Importar() {
  const [estado, setEstado] = useState(STATES.idle)
  const [archivo, setArchivo] = useState(null)
  const [blobUrl, setBlobUrl] = useState(null)
  const [csvFilename, setCsvFilename] = useState('')
  const [errorMsg, setErrorMsg] = useState('')
  const [dragging, setDragging] = useState(false)
  const inputRef = useRef(null)

  function handleFile(file) {
    if (!file) return
    if (file.type !== 'application/pdf') {
      setErrorMsg('El archivo debe ser un PDF.')
      setEstado(STATES.error)
      return
    }
    setArchivo(file)
    setEstado(STATES.idle)
    setErrorMsg('')
    setBlobUrl(null)
  }

  function onInputChange(e) {
    handleFile(e.target.files[0])
  }

  function onDrop(e) {
    e.preventDefault()
    setDragging(false)
    handleFile(e.dataTransfer.files[0])
  }

  async function procesar() {
    if (!archivo) return
    setEstado(STATES.procesando)
    setErrorMsg('')
    setBlobUrl(null)

    try {
      const blob = await api.importar.vinylfutureCsv(archivo)
      const url = URL.createObjectURL(blob)
      const ts = new Date().toISOString().replace(/[:T]/g, '-').slice(0, 16)
      const filename = `vinylfuture-import-${ts}.zip`
      setBlobUrl(url)
      setCsvFilename(filename)
      setEstado(STATES.listo)

      // Trigger automatic download
      const a = document.createElement('a')
      a.href = url
      a.download = filename
      a.click()
    } catch (err) {
      setErrorMsg(err.message || 'Ocurrió un error al procesar el PDF.')
      setEstado(STATES.error)
    }
  }

  function reset() {
    setEstado(STATES.idle)
    setArchivo(null)
    setBlobUrl(null)
    setErrorMsg('')
    if (inputRef.current) inputRef.current.value = ''
  }

  return (
    <main className="max-w-2xl mx-auto px-4 py-10">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-slate-900 dark:text-white tracking-tight">Importar factura</h1>
        <p className="mt-1.5 text-sm text-slate-500 dark:text-stone-400">
          Subí una factura PDF de deejay.de para buscar cada ítem en{' '}
          <a
            href="https://www.vinylfuture.com"
            target="_blank"
            rel="noreferrer"
            className="text-[#5C7D87] dark:text-[#7E9FA8] underline underline-offset-2"
          >
            vinylfuture.com
          </a>
          {' '}y descargar portadas e MP3s de cada track. Obtenés un ZIP con todo organizado por álbum.
        </p>
      </div>

      {/* Drop zone */}
      <div
        className={`relative rounded-2xl border-2 border-dashed transition-colors cursor-pointer
          ${dragging
            ? 'border-[#7E9FA8] bg-[#7E9FA8]/5'
            : archivo
              ? 'border-[#7E9FA8]/50 bg-[#7E9FA8]/5 dark:bg-[#7E9FA8]/5'
              : 'border-slate-200 dark:border-stone-700 hover:border-[#7E9FA8]/50 hover:bg-slate-50 dark:hover:bg-stone-900/50'
          }`}
        onClick={() => inputRef.current?.click()}
        onDragOver={(e) => { e.preventDefault(); setDragging(true) }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
      >
        <input
          ref={inputRef}
          type="file"
          accept="application/pdf"
          className="hidden"
          onChange={onInputChange}
        />

        <div className="flex flex-col items-center justify-center gap-3 py-12 px-6 text-center pointer-events-none">
          {archivo ? (
            <>
              <div className="flex items-center gap-2 text-[#5C7D87] dark:text-[#7E9FA8]">
                <PdfIcon />
                <span className="font-medium text-sm">{archivo.name}</span>
              </div>
              <span className="text-xs text-slate-400 dark:text-stone-500">
                {(archivo.size / 1024).toFixed(0)} KB · Clic para cambiar
              </span>
            </>
          ) : (
            <>
              <UploadIcon />
              <div>
                <p className="font-medium text-slate-700 dark:text-stone-300 text-sm">
                  Arrastrá el PDF aquí o hacé clic para seleccionar
                </p>
                <p className="text-xs text-slate-400 dark:text-stone-500 mt-1">Solo archivos PDF</p>
              </div>
            </>
          )}
        </div>
      </div>

      {/* Actions */}
      <div className="mt-5 flex gap-3 flex-wrap">
        <button
          onClick={procesar}
          disabled={!archivo || estado === STATES.procesando}
          className="px-5 py-2.5 rounded-lg bg-[#5C7D87] hover:bg-[#4a6a74] disabled:opacity-40 disabled:cursor-not-allowed
            text-white text-sm font-medium transition-colors"
        >
          {estado === STATES.procesando ? 'Procesando…' : 'Procesar PDF'}
        </button>

        {archivo && estado !== STATES.procesando && (
          <button
            onClick={reset}
            className="px-5 py-2.5 rounded-lg border border-slate-200 dark:border-stone-700
              text-slate-600 dark:text-stone-300 text-sm font-medium hover:bg-slate-50 dark:hover:bg-stone-900 transition-colors"
          >
            Limpiar
          </button>
        )}
      </div>

      {/* Status */}
      {estado === STATES.procesando && (
        <div className="mt-6 p-4 rounded-xl bg-slate-50 dark:bg-stone-900 border border-slate-100 dark:border-stone-800">
          <Spinner text="Buscando en vinylfuture.com y descargando portadas y MP3s… puede tardar varios segundos por ítem." />
          <p className="text-xs text-center text-slate-400 dark:text-stone-500 mt-2">
            No cierres esta página mientras se procesa.
          </p>
        </div>
      )}

      {estado === STATES.listo && (
        <div className="mt-6 p-5 rounded-xl bg-emerald-50 dark:bg-emerald-900/20 border border-emerald-200 dark:border-emerald-800">
          <div className="flex items-center gap-2 mb-3">
            <CheckIcon />
            <span className="font-medium text-emerald-700 dark:text-emerald-400 text-sm">
              ZIP generado correctamente
            </span>
          </div>
          <p className="text-xs text-emerald-700/70 dark:text-emerald-300/60 mb-3">
            La descarga debería haber comenzado automáticamente. El ZIP incluye el CSV, portadas e MP3s por álbum.
            Si algún archivo no se pudo descargar, encontrás un <code className="font-mono">missing.txt</code> en la carpeta del álbum.
          </p>
          <a
            href={blobUrl}
            download={csvFilename}
            className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-700
              text-white text-sm font-medium transition-colors"
          >
            <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M3 16.5v2.25A2.25 2.25 0 0 0 5.25 21h13.5A2.25 2.25 0 0 0 21 18.75V16.5M16.5 12 12 16.5m0 0L7.5 12m4.5 4.5V3" />
            </svg>
            Descargar ZIP
          </a>
        </div>
      )}

      {estado === STATES.error && (
        <div className="mt-6 p-4 rounded-xl bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800">
          <p className="text-sm text-red-700 dark:text-red-400 font-medium">Error al procesar</p>
          {errorMsg && <p className="text-xs text-red-600 dark:text-red-300 mt-1">{errorMsg}</p>}
        </div>
      )}

      {/* Info card */}
      <div className="mt-8 p-4 rounded-xl bg-slate-50 dark:bg-stone-900 border border-slate-100 dark:border-stone-800">
        <p className="text-xs font-semibold uppercase tracking-wider text-slate-400 dark:text-stone-500 mb-2">
          Contenido del ZIP
        </p>
        <div className="flex flex-col gap-1.5 text-xs text-slate-500 dark:text-stone-400 font-mono">
          <span>import.csv — artista, album, url_vinylfuture, estado_match</span>
          <span>{'{artista} - {album} - {codigo}/'}</span>
          <span className="pl-4">images/ — {'{codigo}_front.jpg'}, {'{codigo}_back.jpg'}</span>
          <span className="pl-4">audio/ — {'{codigo}_A1.mp3'}, {'{codigo}_B1.mp3'} …</span>
          <span className="pl-4">missing.txt — assets que no se pudieron descargar</span>
        </div>
      </div>
    </main>
  )
}

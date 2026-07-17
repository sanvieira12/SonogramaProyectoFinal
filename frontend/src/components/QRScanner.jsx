import { memo, useCallback, useEffect, useRef, useState } from 'react'
import './QRScanner.css'

const INITIAL_STATUS = {
  tone: 'idle',
  message: 'Apuntá la cámara a un código QR',
}

const MAX_SCAN_QUEUE = 4
const RETRY_COOLDOWN_MS = 1400

let browserReaderModulePromise

function loadBrowserReader() {
  browserReaderModulePromise ||= import('@zxing/browser')
  return browserReaderModulePromise
}

function isExpectedDecodeMiss(error) {
  const name = error?.name || error?.constructor?.name || ''
  return /NotFound|Checksum|Format/i.test(name)
}

function describeCameraError(error) {
  switch (error?.name) {
    case 'NotAllowedError':
      return 'El acceso a la cámara fue denegado. Habilitalo en los permisos del navegador e intentá nuevamente.'
    case 'NotFoundError':
      return 'No se encontró una cámara disponible en este dispositivo.'
    case 'NotReadableError':
      return 'La cámara está siendo usada por otra aplicación.'
    case 'SecurityError':
      return 'La cámara requiere una conexión segura HTTPS.'
    default:
      return 'No se pudo iniciar la cámara. Revisá los permisos e intentá nuevamente.'
  }
}

function playTone(audioContextRef, { frequency, endFrequency, type, volume }) {
  if (typeof window === 'undefined' || (!window.AudioContext && !window.webkitAudioContext)) return

  try {
    const AudioContextConstructor = window.AudioContext || window.webkitAudioContext
    const audioContext = audioContextRef.current || new AudioContextConstructor()
    audioContextRef.current = audioContext
    const now = audioContext.currentTime
    const oscillator = audioContext.createOscillator()
    const gain = audioContext.createGain()

    oscillator.type = type
    oscillator.frequency.setValueAtTime(frequency, now)
    oscillator.frequency.exponentialRampToValueAtTime(endFrequency, now + 0.08)
    gain.gain.setValueAtTime(0.0001, now)
    gain.gain.exponentialRampToValueAtTime(volume, now + 0.012)
    gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.14)
    oscillator.connect(gain)
    gain.connect(audioContext.destination)
    oscillator.start(now)
    oscillator.stop(now + 0.15)
  } catch {
    // Audio feedback is progressive enhancement; scanning continues silently.
  }
}

function vibrate(pattern = [35, 25, 55]) {
  if (typeof navigator !== 'undefined' && typeof navigator.vibrate === 'function') {
    navigator.vibrate(pattern)
  }
}

const QRScanner = memo(function QRScanner({
  open = true,
  onScan,
  onScanError,
  onClose,
  lastScannedRecord,
  title = 'Escanear código QR',
  closeLabel = 'Finalizar escaneo',
}) {
  const videoRef = useRef(null)
  const closeButtonRef = useRef(null)
  const controlsRef = useRef(null)
  const readerRef = useRef(null)
  const audioContextRef = useRef(null)
  const onScanRef = useRef(onScan)
  const onScanErrorRef = useRef(onScanError)
  const onCloseRef = useRef(onClose)
  const startedRef = useRef(false)
  const activeRef = useRef(false)
  const startAttemptRef = useRef(0)
  const seenValuesRef = useRef(new Set())
  const scanQueueRef = useRef([])
  const queuedValuesRef = useRef(new Set())
  const retryTimersRef = useRef(new Map())
  const processingQueueRef = useRef(false)
  const feedbackTimerRef = useRef(null)
  const [status, setStatus] = useState(INITIAL_STATUS)
  const [starting, setStarting] = useState(false)
  const [queuedCount, setQueuedCount] = useState(0)

  useEffect(() => {
    onScanRef.current = onScan
  }, [onScan])

  useEffect(() => {
    onScanErrorRef.current = onScanError
  }, [onScanError])

  useEffect(() => {
    onCloseRef.current = onClose
  }, [onClose])

  const clearFeedbackTimer = useCallback(() => {
    if (feedbackTimerRef.current) {
      window.clearTimeout(feedbackTimerRef.current)
      feedbackTimerRef.current = null
    }
  }, [])

  const showSuccess = useCallback((message) => {
    clearFeedbackTimer()
    setStatus({ tone: 'success', message })
    playTone(audioContextRef, {
      frequency: 880,
      endFrequency: 1320,
      type: 'sine',
      volume: 0.16,
    })
    vibrate()
    feedbackTimerRef.current = window.setTimeout(() => {
      feedbackTimerRef.current = null
      setStatus(current => current.tone === 'success' ? INITIAL_STATUS : current)
    }, 900)
  }, [clearFeedbackTimer])

  const showError = useCallback((message) => {
    clearFeedbackTimer()
    setStatus({ tone: 'error', message })
    playTone(audioContextRef, {
      frequency: 220,
      endFrequency: 140,
      type: 'triangle',
      volume: 0.12,
    })
    vibrate([70])
    feedbackTimerRef.current = window.setTimeout(() => {
      feedbackTimerRef.current = null
      setStatus(current => current.tone === 'error' ? INITIAL_STATUS : current)
    }, RETRY_COOLDOWN_MS)
  }, [clearFeedbackTimer])

  const processScanQueue = useCallback(async () => {
    if (processingQueueRef.current) return
    processingQueueRef.current = true

    while (activeRef.current && scanQueueRef.current.length > 0) {
      const value = scanQueueRef.current.shift()
      queuedValuesRef.current.delete(value)
      setQueuedCount(scanQueueRef.current.length)

      try {
        await onScanRef.current?.(value)
        if (activeRef.current) {
          setStatus({ tone: 'success', message: scanQueueRef.current.length ? 'Código agregado; continuando…' : 'Código agregado correctamente' })
        }
      } catch (scanError) {
        if (!activeRef.current) break
        const message = scanError?.message || 'El código fue leído, pero no pudo ser procesado.'
        showError(message)
        onScanErrorRef.current?.(scanError instanceof Error ? scanError : new Error(message))
        const retryTimer = window.setTimeout(() => {
          retryTimersRef.current.delete(value)
          seenValuesRef.current.delete(value)
        }, RETRY_COOLDOWN_MS)
        retryTimersRef.current.set(value, retryTimer)
      }
    }

    processingQueueRef.current = false
  }, [showError])

  const enqueueScan = useCallback((value) => {
    if (scanQueueRef.current.length >= MAX_SCAN_QUEUE) {
      showError('Procesando lecturas… mantené el código siguiente dentro del recuadro.')
      const retryTimer = window.setTimeout(() => {
        retryTimersRef.current.delete(value)
        seenValuesRef.current.delete(value)
      }, RETRY_COOLDOWN_MS)
      retryTimersRef.current.set(value, retryTimer)
      return
    }

    scanQueueRef.current.push(value)
    queuedValuesRef.current.add(value)
    setQueuedCount(scanQueueRef.current.length)
    showSuccess(scanQueueRef.current.length > 1 ? `Código leído · ${scanQueueRef.current.length} en cola` : 'Código leído · agregando…')
    processScanQueue()
  }, [processScanQueue, showError, showSuccess])

  const stopScanner = useCallback(() => {
    startAttemptRef.current += 1
    clearFeedbackTimer()
    scanQueueRef.current = []
    queuedValuesRef.current.clear()
    setQueuedCount(0)
    retryTimersRef.current.forEach(timer => window.clearTimeout(timer))
    retryTimersRef.current.clear()
    controlsRef.current?.stop?.()
    controlsRef.current = null
    readerRef.current?.reset?.()
    readerRef.current = null

    const video = videoRef.current
    if (video?.srcObject) {
      video.srcObject.getTracks().forEach(track => track.stop())
      video.srcObject = null
    }
  }, [clearFeedbackTimer])

  const startScanner = useCallback(async () => {
    if (startedRef.current || !videoRef.current || !open) return

    if (!navigator.mediaDevices?.getUserMedia) {
      setStatus({ tone: 'error', message: 'Este navegador no permite usar la cámara.' })
      return
    }

    const attempt = startAttemptRef.current + 1
    startAttemptRef.current = attempt
    startedRef.current = true
    setStarting(true)
    setStatus({ tone: 'idle', message: 'Solicitando acceso a la cámara…' })

    try {
      const { BrowserQRCodeReader } = await loadBrowserReader()
      const reader = new BrowserQRCodeReader(undefined, {
        delayBetweenScanAttempts: 120,
        delayBetweenScanSuccess: 180,
        tryPlayVideoTimeout: 5000,
      })
      readerRef.current = reader
      if (!activeRef.current || !open || attempt !== startAttemptRef.current) {
        reader.reset?.()
        startedRef.current = false
        return
      }

      const controls = await reader.decodeFromConstraints(
        {
          audio: false,
          video: {
            facingMode: { ideal: 'environment' },
            width: { ideal: 1280, max: 1280 },
            height: { ideal: 720, max: 720 },
            frameRate: { ideal: 15, max: 20 },
          },
        },
        videoRef.current,
        (result, error) => {
          if (result) {
            const value = result.getText().trim()
            if (!value || seenValuesRef.current.has(value) || queuedValuesRef.current.has(value)) return
            seenValuesRef.current.add(value)
            enqueueScan(value)
            return
          }

          if (error && !isExpectedDecodeMiss(error)) {
            setStatus({ tone: 'error', message: 'No se pudo leer el código. Ajustá el encuadre.' })
          }
        },
      )

      if (!activeRef.current || !open || attempt !== startAttemptRef.current) {
        controls.stop?.()
        startedRef.current = false
        return
      }
      controlsRef.current = controls
      setStarting(false)
      setStatus(INITIAL_STATUS)
    } catch (error) {
      if (attempt !== startAttemptRef.current) return
      startedRef.current = false
      setStarting(false)
      stopScanner()
      if (activeRef.current) {
        setStatus({ tone: 'error', message: describeCameraError(error) })
      }
    }
  }, [enqueueScan, open, stopScanner])

  useEffect(() => {
    if (!open) return undefined

    activeRef.current = true
    seenValuesRef.current.clear()
    let cancelled = false
    const start = async () => {
      if (!cancelled) await startScanner()
    }
    start()

    return () => {
      cancelled = true
      activeRef.current = false
      startedRef.current = false
      stopScanner()
      audioContextRef.current?.close?.()
      audioContextRef.current = null
    }
  }, [open, startScanner, stopScanner])

  useEffect(() => {
    if (!open) return undefined

    const handleVisibilityChange = () => {
      if (document.hidden) {
        activeRef.current = false
        startedRef.current = false
        stopScanner()
        return
      }
      activeRef.current = true
      startScanner()
    }
    const handlePageHide = () => {
      activeRef.current = false
      startedRef.current = false
      stopScanner()
    }
    const handlePageShow = () => {
      if (!document.hidden) {
        activeRef.current = true
        startScanner()
      }
    }
    const resumeVideo = () => {
      videoRef.current?.play?.().catch(() => {})
    }

    document.addEventListener('visibilitychange', handleVisibilityChange)
    window.addEventListener('pagehide', handlePageHide)
    window.addEventListener('pageshow', handlePageShow)
    window.addEventListener('orientationchange', resumeVideo)
    window.addEventListener('resize', resumeVideo)

    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange)
      window.removeEventListener('pagehide', handlePageHide)
      window.removeEventListener('pageshow', handlePageShow)
      window.removeEventListener('orientationchange', resumeVideo)
      window.removeEventListener('resize', resumeVideo)
    }
  }, [open, startScanner, stopScanner])

  useEffect(() => {
    if (!open) return undefined
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    closeButtonRef.current?.focus()
    const handleKeyDown = event => {
      if (event.key === 'Escape') {
        event.preventDefault()
        onCloseRef.current?.()
      }
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.body.style.overflow = previousOverflow
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [open])

  if (!open) return null

  const isError = status.tone === 'error'
  const isSuccess = status.tone === 'success'
  const statusClassName = isSuccess
    ? 'rounded-full border border-emerald-300/40 bg-emerald-400/15 px-4 py-2 text-center text-xs text-emerald-100 backdrop-blur'
    : isError
      ? 'rounded-full border border-rose-300/40 bg-rose-400/15 px-4 py-2 text-center text-xs text-rose-100 backdrop-blur'
      : 'rounded-full border border-white/15 bg-white/10 px-4 py-2 text-center text-xs text-white/75 backdrop-blur'

  return (
    <div
      className="qr-scanner-shell fixed inset-0 z-[100] min-h-[100dvh] overflow-hidden text-white"
      role="dialog"
      aria-modal="true"
      aria-labelledby="qr-scanner-title"
      aria-describedby="qr-scanner-instructions"
    >
      <video
        ref={videoRef}
        autoPlay
        muted
        playsInline
        disablePictureInPicture
        aria-label="Vista previa de la cámara para escanear un código QR"
        className="qr-scanner-video absolute inset-0 h-full w-full object-cover"
      />

      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_center,transparent_0%,rgba(0,0,0,.18)_48%,rgba(0,0,0,.8)_100%)]" />

      <header className="absolute inset-x-0 top-0 flex items-center justify-between gap-4 bg-gradient-to-b from-black/75 to-transparent px-5 pb-10 pt-[max(1.25rem,env(safe-area-inset-top))] sm:px-8">
        <div>
          <p className="text-[11px] font-semibold uppercase tracking-[0.22em] text-cyan-100/70">Sonograma</p>
          <h1 id="qr-scanner-title" className="mt-1 text-lg font-semibold tracking-tight sm:text-xl">{title}</h1>
        </div>
        <button
          ref={closeButtonRef}
          type="button"
          onClick={() => onCloseRef.current?.()}
          className="pointer-events-auto flex h-11 w-11 items-center justify-center rounded-full border border-white/20 bg-black/35 text-2xl text-white/80 backdrop-blur transition hover:bg-white/15 focus:outline-none focus:ring-2 focus:ring-cyan-200"
          aria-label={closeLabel}
        >
          ×
        </button>
      </header>

      <main className="absolute inset-0 flex items-center justify-center px-5 pb-32 pt-24">
        <div className="relative">
          <div className="qr-scanner-frame relative flex items-center justify-center">
            {!isSuccess && !isError && <div className="qr-scanner-line" />}
            {starting && (
              <div className="rounded-full border border-white/20 bg-black/45 px-4 py-2 text-xs text-white/80 backdrop-blur" role="status">
                Activando cámara…
              </div>
            )}
            {isSuccess && (
              <div key="success" className="qr-scanner-feedback flex h-20 w-20 items-center justify-center rounded-full bg-emerald-400 text-5xl font-light text-black shadow-[0_0_45px_rgba(52,211,153,.65)]" aria-hidden="true">
                ✓
              </div>
            )}
            {isError && (
              <div key="error" className="qr-scanner-feedback-error flex h-20 w-20 items-center justify-center rounded-full bg-rose-400 text-5xl font-light text-black shadow-[0_0_45px_rgba(251,113,133,.65)]" aria-hidden="true">
                !
              </div>
            )}
          </div>
          <p id="qr-scanner-instructions" className="mt-7 text-center text-sm text-white/75">Mantené el código dentro del recuadro</p>
        </div>
      </main>

      <footer className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/90 via-black/70 to-transparent px-5 pb-[max(1.25rem,env(safe-area-inset-bottom))] pt-14 sm:px-8">
        <div className="mx-auto flex max-w-xl flex-col items-center gap-3">
          {lastScannedRecord && (
            <div className="w-full rounded-2xl border border-white/15 bg-black/35 px-4 py-3 text-center backdrop-blur">
              <p className="text-[10px] font-semibold uppercase tracking-[0.18em] text-cyan-100/65">Último escaneado</p>
              <p className="mt-1 truncate text-sm font-semibold text-white">
                {lastScannedRecord.artista || 'Disco'}{lastScannedRecord.album ? ` — ${lastScannedRecord.album}` : ''}
              </p>
              <p className="mt-0.5 truncate text-xs text-white/60">
                {lastScannedRecord.codigoInterno || `ID ${lastScannedRecord.idDisco}`}
              </p>
            </div>
          )}
          <span role="status" aria-live="polite" aria-atomic="true" className={statusClassName}>
            {status.message}
          </span>
          {queuedCount > 0 && (
            <span className="text-xs text-white/60" aria-live="polite">
              {queuedCount} {queuedCount === 1 ? 'lectura pendiente' : 'lecturas pendientes'}
            </span>
          )}
          <button
            type="button"
            onClick={() => onCloseRef.current?.()}
            className="pointer-events-auto min-h-11 w-full rounded-xl border border-white/20 bg-white/10 px-4 py-3 text-sm font-semibold text-white transition hover:bg-white/15 focus:outline-none focus:ring-2 focus:ring-cyan-200"
          >
            {closeLabel}
          </button>
        </div>
      </footer>
    </div>
  )
})

export default QRScanner

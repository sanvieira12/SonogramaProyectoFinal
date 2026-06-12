import { useEffect, useRef, useState } from 'react'

// Module-level singleton — only one player plays at a time
let globalStopFn = null

export function stopAllPreviews() {
  globalStopFn?.()
  globalStopFn = null
}

function fmtTime(secs) {
  if (!isFinite(secs) || secs < 0) return '0:00'
  const m = Math.floor(secs / 60)
  const s = Math.floor(secs % 60)
  return `${m}:${s.toString().padStart(2, '0')}`
}

export default function CompactPlayer({ audioUrl, trackName, trackPosition }) {
  const audioRef = useRef(null)
  const [playing, setPlaying] = useState(false)
  const [current, setCurrent] = useState(0)
  const [duration, setDuration] = useState(0)

  useEffect(() => {
    const audio = new Audio()
    audio.preload = 'none'
    audioRef.current = audio

    const onTime = () => setCurrent(audio.currentTime)
    const onMeta = () => setDuration(audio.duration)
    const onEnd  = () => { setPlaying(false); setCurrent(0) }

    audio.addEventListener('timeupdate', onTime)
    audio.addEventListener('loadedmetadata', onMeta)
    audio.addEventListener('ended', onEnd)

    return () => {
      audio.removeEventListener('timeupdate', onTime)
      audio.removeEventListener('loadedmetadata', onMeta)
      audio.removeEventListener('ended', onEnd)
      audio.pause()
      audio.src = ''
      if (globalStopFn === localStop) globalStopFn = null
    }
  }, [audioUrl]) // eslint-disable-line react-hooks/exhaustive-deps

  function localStop() {
    const audio = audioRef.current
    if (!audio) return
    audio.pause()
    audio.currentTime = 0
    setPlaying(false)
    setCurrent(0)
  }

  function togglePlay() {
    const audio = audioRef.current
    if (!audio) return

    if (playing) {
      audio.pause()
      setPlaying(false)
      if (globalStopFn === localStop) globalStopFn = null
    } else {
      // Stop whatever is currently playing
      if (globalStopFn && globalStopFn !== localStop) globalStopFn()
      globalStopFn = localStop

      if (!audio.src) audio.src = audioUrl
      audio.play().catch(() => {
        setPlaying(false)
        globalStopFn = null
      })
      setPlaying(true)
    }
  }

  const progress = duration > 0 ? (current / duration) * 100 : 0

  return (
    <div className="flex items-center gap-3 py-2 px-3 rounded-lg bg-stone-950/70 border border-stone-800/60">
      {/* Circular play/pause */}
      <button
        type="button"
        onClick={togglePlay}
        className="w-8 h-8 rounded-full bg-[#7E9FA8]/15 hover:bg-[#7E9FA8]/30 border border-[#7E9FA8]/40 flex items-center justify-center flex-shrink-0 transition-colors"
        title={playing ? 'Pausar' : 'Reproducir'}
      >
        {playing ? (
          <svg className="w-3.5 h-3.5 text-[#7E9FA8]" fill="currentColor" viewBox="0 0 24 24">
            <rect x="6" y="4" width="4" height="16" rx="1" />
            <rect x="14" y="4" width="4" height="16" rx="1" />
          </svg>
        ) : (
          <svg className="w-3.5 h-3.5 text-[#7E9FA8] translate-x-px" fill="currentColor" viewBox="0 0 24 24">
            <path d="M8 5v14l11-7z" />
          </svg>
        )}
      </button>

      {/* Track info + progress */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center justify-between gap-2 mb-1.5">
          <span className="text-xs text-stone-300 truncate">
            {trackPosition && (
              <span className="text-stone-500 mr-1.5 text-[10px] font-mono">{trackPosition}</span>
            )}
            <span className="font-medium">{trackName || 'Track'}</span>
          </span>
          <span className="text-[10px] text-stone-500 flex-shrink-0 tabular-nums">
            {fmtTime(current)} / {fmtTime(duration)}
          </span>
        </div>
        <div className="h-[3px] bg-stone-800 rounded-full overflow-hidden">
          <div
            className="h-full bg-[#7E9FA8] rounded-full transition-none"
            style={{ width: `${progress}%` }}
          />
        </div>
      </div>
    </div>
  )
}

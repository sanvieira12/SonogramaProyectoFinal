import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CompactPlayer from './CompactPlayer'

describe('CompactPlayer', () => {
  let audioInstances

  beforeEach(() => {
    audioInstances = []
    globalThis.Audio = vi.fn().mockImplementation(() => {
      const listeners = {}
      const audio = {
        preload: '',
        src: '',
        currentTime: 0,
        duration: 0,
        addEventListener: vi.fn((event, handler) => { listeners[event] = handler }),
        removeEventListener: vi.fn(),
        pause: vi.fn(),
        play: vi.fn().mockResolvedValue(undefined),
        dispatch: event => listeners[event]?.(),
      }
      audioInstances.push(audio)
      return audio
    })
  })

  it('renders a YouTube listen link when there is no MP3', () => {
    render(
      <CompactPlayer
        trackPosition="A1"
        trackName="Video Track"
        youtubeUrl="https://www.youtube.com/watch?v=test"
      />,
    )

    expect(screen.getByText('Video Track')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Escuchar' })).toHaveAttribute(
      'href',
      'https://www.youtube.com/watch?v=test',
    )
  })

  it('resolves backend-relative MP3 URLs before playback', () => {
    const path = '/api/importar/vinylfuture/media/CAT-1/A1.mp3'
    render(
      <CompactPlayer
        trackPosition="A1"
        trackName="Local Track"
        audioUrl={path}
      />,
    )

    fireEvent.click(screen.getByTitle('Reproducir'))

    expect(audioInstances[0].src).toBe(new URL(path, window.location.origin).href)
  })
})

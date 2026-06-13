import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import CompactPlayer from './CompactPlayer'

describe('CompactPlayer', () => {
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
})

const MONTEVIDEO_TIME_ZONE = 'America/Montevideo'

export function businessDateInMontevideo(date = new Date()) {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: MONTEVIDEO_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(date)
  const values = Object.fromEntries(
    parts
      .filter(({ type }) => type !== 'literal')
      .map(({ type, value }) => [type, value]),
  )
  return `${values.year}-${values.month}-${values.day}`
}

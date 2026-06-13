let globalStopFn = null

export function stopAllPreviews() {
  globalStopFn?.()
  globalStopFn = null
}

export function claimPreview(stopFn) {
  if (globalStopFn && globalStopFn !== stopFn) globalStopFn()
  globalStopFn = stopFn
}

export function releasePreview(stopFn) {
  if (globalStopFn === stopFn) globalStopFn = null
}

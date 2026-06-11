export function redirectIfUnauthorized(response) {
  if (![401, 403].includes(response.status) || !localStorage.getItem('token')) {
    return false
  }

  localStorage.removeItem('token')
  localStorage.removeItem('usuario')

  if (window.location.pathname !== '/login') {
    window.location.assign('/login')
  }

  return true
}

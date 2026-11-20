/**
 * 极简登录态管理（localStorage）。
 * 项目规模小，没必要引 Pinia/Vuex；把"存/取/清"三件事封装好就够用。
 * 需要响应式共享状态时再升级到 Pinia，面试时可以说明这个取舍。
 */
const TOKEN_KEY = 'token'
const USER_KEY = 'user'

export function saveLogin(data) {
  localStorage.setItem(TOKEN_KEY, data.token)
  localStorage.setItem(USER_KEY, JSON.stringify(data.user))
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function getUser() {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch (e) {
    return null
  }
}

export function isAdmin() {
  const user = getUser()
  return !!user && user.role === 1
}

export function clearLogin() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

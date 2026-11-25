import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * Axios 实例：统一 baseURL、Token 注入、响应解包、错误提示。
 * 页面里调用 api 函数拿到的直接就是 data，不用每处都写 res.data.data。
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// 请求拦截器：自动带上 Token
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器：按后端统一返回结构 { code, message, data } 处理
request.interceptors.response.use(
  (response) => {
    const res = response.data

    // 后端成功约定 code = 200
    if (res.code === 200) {
      return res.data
    }

    if (res.code === 401) return rejectUnauthorized(res.message)

    // 其余业务错误统一弹提示，页面只需要处理成功分支
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    // 后端现在让 HTTP 状态码与业务 code 对齐，错误信息仍在统一 Result 中。
    const res = error.response?.data
    if (res?.code === 401) return rejectUnauthorized(res.message)

    const msg = res?.message || (error.response
      ? `服务异常(${error.response.status})，请稍后重试`
      : '网络异常，请确认后端已启动（http://localhost:8080）')
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

function rejectUnauthorized(message) {
  ElMessage.error(message || '登录已失效，请重新登录')
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  // 用 replace 避免用户点后退又回到需要登录的页面
  setTimeout(() => window.location.replace('/login'), 600)
  return Promise.reject(new Error(message || '登录已失效，请重新登录'))
}

export default request

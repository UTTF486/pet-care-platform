import request from './request'

/**
 * 所有后端接口集中管理。
 * 好处：接口路径变了只改这一处；页面里看得见"这个页面调了哪些接口"。
 */

// ---------------- 认证 ----------------
export const register = (data) => request.post('/auth/register', data)
export const login = (data) => request.post('/auth/login', data)
export const logout = () => request.post('/auth/logout')
export const getProfile = () => request.get('/auth/profile')
export const changePassword = (data) => request.put('/auth/password', data)

// ---------------- 宠物档案 ----------------
export const listPets = () => request.get('/pets')
export const addPet = (data) => request.post('/pets', data)
export const updatePet = (data) => request.put('/pets', data)
export const deletePet = (id) => request.delete(`/pets/${id}`)

// ---------------- 服务（用户端） ----------------
export const listCategories = () => request.get('/categories')
export const listHotServices = () => request.get('/services/hot')
export const listServices = (params) => request.get('/services', { params })
export const getService = (id) => request.get(`/services/${id}`)

// ---------------- 订单（用户端） ----------------
export const createOrder = (data) => request.post('/orders', data)
export const listMyOrders = (params) => request.get('/orders', { params })
export const getOrder = (id) => request.get(`/orders/${id}`)
export const getOrderLogs = (id) => request.get(`/orders/${id}/logs`)
export const getOrderReview = (id) => request.get(`/orders/${id}/review`)
export const cancelOrder = (id, reason) => request.put(`/orders/${id}/cancel`, null, { params: { reason } })
export const urgeOrder = (id) => request.post(`/orders/${id}/urge`)
export const reviewOrder = (data) => request.post('/orders/review', data)

// ---------------- 消息 ----------------
export const listMessages = (params) => request.get('/messages', { params })
export const getUnreadCount = () => request.get('/messages/unread-count')
export const readAllMessages = () => request.put('/messages/read-all')

// ---------------- 后台：服务管理 ----------------
export const adminListServices = (params) => request.get('/admin/services', { params })
export const adminSaveService = (data) => request.post('/admin/services', data)
export const adminUpdateService = (id, data) => request.put(`/admin/services/${id}`, data)
export const adminChangeServiceStatus = (id, status) =>
  request.put(`/admin/services/${id}/status`, null, { params: { status } })

// ---------------- 后台：订单审核 ----------------
export const adminListOrders = (params) => request.get('/admin/orders', { params })
export const adminAcceptOrder = (id, servicerId) =>
  request.put(`/admin/orders/${id}/accept`, null, { params: { servicerId } })
export const adminFinishOrder = (id) => request.put(`/admin/orders/${id}/finish`)
export const adminRejectOrder = (id, reason) =>
  request.put(`/admin/orders/${id}/reject`, null, { params: { reason } })

// ---------------- 后台：用户 / 服务师 ----------------
export const adminListUsers = (params) => request.get('/admin/users', { params })
export const adminChangeUserStatus = (id, status) =>
  request.put(`/admin/users/${id}/status`, null, { params: { status } })

export const adminListServicers = (params) => request.get('/admin/servicers', { params })
export const adminAvailableServicers = () => request.get('/admin/servicers/available')
export const adminSaveServicer = (data) => request.post('/admin/servicers', data)
export const adminUpdateServicer = (id, data) => request.put(`/admin/servicers/${id}`, data)

// ---------------- 后台：数据看板 ----------------
export const statsOverview = () => request.get('/admin/stats/overview')
export const statsTrend = (type, range) => request.get('/admin/stats/trend', { params: { type, range } })
export const statsRanking = (type, range, limit = 5) =>
  request.get('/admin/stats/ranking', { params: { type, range, limit } })

import test from 'node:test'
import assert from 'node:assert/strict'
import {
  clearRequestId,
  getOrCreateRequestId,
  rotateRequestId
} from './idempotency.mjs'

test('uses memory when both web storages are unavailable', () => {
  const unavailable = {
    getItem() { throw new Error('blocked') },
    setItem() { throw new Error('blocked') },
    removeItem() { throw new Error('blocked') }
  }
  let sequence = 0
  const options = {
    storage: unavailable,
    fallbackStorage: unavailable,
    memory: new Map(),
    consumed: new Map(),
    createId: () => `request-${++sequence}`,
    now: () => 1000
  }

  assert.equal(getOrCreateRequestId('same-payload', options), 'request-1')
  assert.equal(getOrCreateRequestId('same-payload', options), 'request-1')
})

test('expires stale request ids after thirty minutes', () => {
  const options = {
    storage: memoryStorage(),
    fallbackStorage: memoryStorage(),
    memory: new Map([['payload', { requestId: 'stale', expiresAt: 100 }]]),
    consumed: new Map(),
    createId: () => 'request-1',
    now: () => 101
  }

  assert.equal(getOrCreateRequestId('payload', options), 'request-1')
})

test('readable but unwritable localStorage reuses session fallback across calls', () => {
  const primary = {
    getItem() { return null },
    setItem() { throw new Error('quota exceeded') },
    removeItem() {}
  }
  const options = {
    storage: primary,
    fallbackStorage: memoryStorage(),
    memory: new Map(),
    consumed: new Map(),
    createId: () => 'request-1',
    now: () => 1000
  }

  assert.equal(getOrCreateRequestId('payload', options), 'request-1')
  assert.equal(getOrCreateRequestId('payload', options), 'request-1')
})

test('delete failure but tombstone success prevents old id reuse', () => {
  const storage = memoryStorage()
  storage.setItem('payload', JSON.stringify({ requestId: 'old', expiresAt: 999999 }))
  storage.removeItem = () => { throw new Error('remove blocked') }
  const options = {
    storage,
    fallbackStorage: memoryStorage(),
    memory: new Map(),
    consumed: new Map(),
    createId: () => 'request-1',
    now: () => 1000
  }

  clearRequestId('payload', options)
  assert.equal(JSON.parse(storage.getItem('payload')).consumed, true)
  assert.equal(getOrCreateRequestId('payload', options), 'request-1')
})

test('both primary operations failing still rotates after reload in same tab', () => {
  const oldRecord = JSON.stringify({ requestId: 'old', expiresAt: 999999 })
  const primary = {
    getItem() { return oldRecord },
    setItem() { throw new Error('set blocked') },
    removeItem() { throw new Error('remove blocked') }
  }
  const fallback = memoryStorage()
  const options = {
    storage: primary,
    fallbackStorage: fallback,
    memory: new Map(),
    consumed: new Map(),
    createId: () => 'request-1',
    now: () => 1000
  }

  clearRequestId('payload', options)

  const reloadedOptions = {
    ...options,
    memory: new Map(),
    consumed: new Map()
  }
  assert.equal(getOrCreateRequestId('payload', reloadedOptions), 'request-1')
  assert.equal(getOrCreateRequestId('payload', reloadedOptions), 'request-1')
})

test('cross-tab completion invalidates another tab memory cache', () => {
  const sharedStorage = memoryStorage()
  let sequence = 0
  const common = {
    storage: sharedStorage,
    createId: () => `request-${++sequence}`,
    now: () => 1000
  }
  const tabA = {
    ...common,
    fallbackStorage: memoryStorage(),
    memory: new Map(),
    consumed: new Map()
  }
  const tabB = {
    ...common,
    fallbackStorage: memoryStorage(),
    memory: new Map(),
    consumed: new Map()
  }

  assert.equal(getOrCreateRequestId('payload', tabA), 'request-1')
  assert.equal(getOrCreateRequestId('payload', tabB), 'request-1')
  clearRequestId('payload', tabA)

  assert.equal(getOrCreateRequestId('payload', tabB), 'request-2')
})

test('rotate generates a new request id after conflict', () => {
  let sequence = 0
  const options = {
    storage: memoryStorage(),
    fallbackStorage: memoryStorage(),
    memory: new Map(),
    consumed: new Map(),
    createId: () => `request-${++sequence}`,
    now: () => 1000
  }

  assert.equal(getOrCreateRequestId('payload', options), 'request-1')
  assert.equal(rotateRequestId('payload', options), 'request-2')
})

test('prunes old memory and consumed entries', () => {
  const memory = new Map()
  const consumed = new Map()
  const options = {
    storage: memoryStorage(),
    fallbackStorage: memoryStorage(),
    memory,
    consumed,
    createId: () => 'new',
    now: () => 1000,
    maxMemoryEntries: 1,
    maxConsumedEntries: 1
  }
  memory.set('old-1', { requestId: 'old-1', expiresAt: 2000 })
  memory.set('old-2', { requestId: 'old-2', expiresAt: 2000 })
  consumed.set('old-1', 2000)
  consumed.set('old-2', 2000)

  getOrCreateRequestId('payload', options)

  assert.equal(memory.size, 1)
  assert.equal(consumed.size, 1)
})

function memoryStorage() {
  const values = new Map()
  return {
    getItem(key) { return values.has(key) ? values.get(key) : null },
    setItem(key, value) { values.set(key, String(value)) },
    removeItem(key) { values.delete(key) }
  }
}

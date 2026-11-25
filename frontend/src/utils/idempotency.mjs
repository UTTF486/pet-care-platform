const REQUEST_TTL_MS = 30 * 60 * 1000
const MAX_MEMORY_ENTRIES = 200
const MAX_CONSUMED_ENTRIES = 500
const memoryCache = new Map()
const consumedKeys = new Map()

export function createRequestId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `req-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`
}

export function getOrCreateRequestId(storageKey, options = {}) {
  const context = createContext(storageKey, options)
  prune(context.memory, context.now, context.maxMemoryEntries)
  prune(context.consumed, context.now, context.maxConsumedEntries)

  const primaryState = readState(context.storage, storageKey)
  const fallbackState = readState(context.fallbackStorage, storageKey)
  const memoryRecord = context.memory.get(storageKey)
  const consumed = isConsumedRecord(primaryState.record)
    || isConsumedRecord(fallbackState.record)
    || isConsumed(context.consumed, storageKey, context.now())
  if (consumed) {
    context.memory.delete(storageKey)
    return createRecord(context)
  }

  const primaryRecord = isUsableRecord(primaryState.record, context.now())
    ? primaryState.record : null
  const fallbackRecord = isUsableRecord(fallbackState.record, context.now())
    ? fallbackState.record : null
  const cachedRecord = isUsableRecord(memoryRecord, context.now())
    ? memoryRecord : null

  if (primaryRecord && fallbackRecord) {
    const selected = latestRecord(primaryRecord, fallbackRecord)
    context.memory.set(storageKey, selected)
    return selected.requestId
  }
  if (primaryRecord) {
    if (cachedRecord?.fallbackOnly && newerThan(cachedRecord, primaryRecord)) {
      context.memory.set(storageKey, cachedRecord)
      return cachedRecord.requestId
    }
    context.memory.set(storageKey, primaryRecord)
    return primaryRecord.requestId
  }
  if (fallbackRecord && (!primaryState.available || fallbackRecord.fallbackOnly)) {
    context.memory.set(storageKey, fallbackRecord)
    return fallbackRecord.requestId
  }
  if (!primaryState.available && cachedRecord) {
    return cachedRecord.requestId
  }
  if (primaryState.available && cachedRecord?.fallbackOnly) {
    return cachedRecord.requestId
  }
  return createRecord(context)
}

export function clearRequestId(storageKey, options = {}) {
  const context = createContext(storageKey, options)
  context.memory.delete(storageKey)
  context.consumed.set(storageKey, context.now() + context.ttlMs)
  prune(context.consumed, context.now, context.maxConsumedEntries)

  try {
    context.storage?.removeItem(storageKey)
  } catch (ignored) {
    writeRecord(context.storage, storageKey, consumedRecord(context))
  }

  // sessionStorage 的消费标记可在刷新后阻止旧 localStorage 值复活。
  try {
    context.fallbackStorage?.removeItem(storageKey)
  } catch (ignored) {
    // 后面仍会尝试写入新的消费占位。
  }
  writeRecord(context.fallbackStorage, storageKey, consumedRecord(context))
}

export function rotateRequestId(storageKey, options = {}) {
  clearRequestId(storageKey, options)
  return getOrCreateRequestId(storageKey, options)
}

function createRecord(context) {
  const record = {
    requestId: context.createId(),
    expiresAt: context.now() + context.ttlMs,
    issuedAt: context.now()
  }
  const primaryWritten = writeRecord(context.storage, context.storageKey, record)
  const memoryRecord = primaryWritten ? record : { ...record, fallbackOnly: true }
  context.memory.set(context.storageKey, memoryRecord)
  prune(context.memory, context.now, context.maxMemoryEntries)
  context.consumed.delete(context.storageKey)
  writeRecord(context.fallbackStorage, context.storageKey, memoryRecord)
  return record.requestId
}

function consumedRecord(context) {
  return { consumed: true, expiresAt: context.now() + context.ttlMs }
}

function createContext(storageKey, options) {
  return {
    storageKey,
    storage: resolveStorage(options, 'storage', 'localStorage'),
    fallbackStorage: resolveStorage(options, 'fallbackStorage', 'sessionStorage'),
    memory: options.memory || memoryCache,
    consumed: options.consumed || consumedKeys,
    now: options.now || (() => Date.now()),
    createId: options.createId || createRequestId,
    ttlMs: options.ttlMs || REQUEST_TTL_MS,
    maxMemoryEntries: options.maxMemoryEntries || MAX_MEMORY_ENTRIES,
    maxConsumedEntries: options.maxConsumedEntries || MAX_CONSUMED_ENTRIES
  }
}

function resolveStorage(options, optionName, globalName) {
  if (Object.prototype.hasOwnProperty.call(options, optionName)) return options[optionName]
  try {
    return globalThis[globalName]
  } catch (ignored) {
    return null
  }
}

function readState(storage, storageKey) {
  if (!storage) return { available: false, record: null }
  try {
    const raw = storage.getItem(storageKey)
    return { available: true, record: raw ? JSON.parse(raw) : null }
  } catch (ignored) {
    return { available: false, record: null }
  }
}

function writeRecord(storage, storageKey, record) {
  try {
    storage?.setItem(storageKey, JSON.stringify(record))
    return Boolean(storage)
  } catch (ignored) {
    return false
  }
}

function isUsableRecord(record, now) {
  return Boolean(record?.requestId && !record.consumed && record.expiresAt > now)
}

function isConsumedRecord(record) {
  return Boolean(record?.consumed)
}

function isConsumed(consumed, storageKey, now) {
  const expiresAt = consumed.get(storageKey)
  if (!expiresAt) return false
  if (expiresAt <= now) {
    consumed.delete(storageKey)
    return false
  }
  return true
}

function latestRecord(first, second) {
  return newerThan(first, second) ? first : second
}

function newerThan(first, second) {
  return (first.issuedAt || 0) > (second.issuedAt || 0)
}

function prune(entries, now, maxEntries) {
  for (const [key, value] of entries) {
    const expiresAt = typeof value === 'number' ? value : value?.expiresAt
    if (!expiresAt || expiresAt <= now()) entries.delete(key)
  }
  while (entries.size > maxEntries) {
    entries.delete(entries.keys().next().value)
  }
}

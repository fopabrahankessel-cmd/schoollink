import { useCallback, useEffect, useRef, useState } from 'react'
import { useAuth } from '../auth/useAuth'

export interface ResourceState<T> {
  data: T | null
  loading: boolean
  error: unknown
  /** Fetches again, keeping the current data on screen; resolves once the new data is shown. */
  reload: () => Promise<void>
}

interface Result<T> {
  path: string
  data: T | null
  error: unknown
}

/** GETs one object, as useList does a list; a null path waits. */
export function useResource<T>(path: string | null): ResourceState<T> {
  const { client } = useAuth()
  const [result, setResult] = useState<Result<T> | null>(null)
  // Only the latest request may set the data: a slow answer for an old path is dropped.
  const latest = useRef(0)

  const load = useCallback(
    async (target: string) => {
      const id = ++latest.current
      let next: Result<T>
      try {
        next = { path: target, data: await client.request<T>(target), error: null }
      } catch (error) {
        next = { path: target, data: null, error }
      }
      if (id === latest.current) setResult(next)
    },
    [client],
  )

  useEffect(() => {
    if (path !== null) void load(path)
  }, [load, path])

  const reload = useCallback(async () => {
    if (path !== null) await load(path)
  }, [load, path])

  const current = path !== null && result?.path === path ? result : null
  return {
    data: current?.data ?? null,
    loading: path !== null && current === null,
    error: current?.error ?? null,
    reload,
  }
}

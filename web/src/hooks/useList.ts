import { useCallback, useEffect, useRef, useState } from 'react'
import { useAuth } from '../auth/useAuth'

export interface ListState<T> {
  items: T[]
  loading: boolean
  error: unknown
  /** Fetches again; resolves once the new list is shown. */
  reload: () => Promise<void>
}

interface Result<T> {
  path: string
  items: T[]
  error: unknown
}

/** GETs a list; a null path waits (e.g. until a parent item is selected). */
export function useList<T>(path: string | null): ListState<T> {
  const { client } = useAuth()
  const [result, setResult] = useState<Result<T> | null>(null)
  // Only the latest request may set the list: a slow answer for an old path is dropped.
  const latest = useRef(0)

  const load = useCallback(
    async (target: string) => {
      const id = ++latest.current
      let next: Result<T>
      try {
        next = { path: target, items: await client.request<T[]>(target), error: null }
      } catch (error) {
        next = { path: target, items: [], error }
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
    items: current?.items ?? [],
    loading: path !== null && current === null,
    error: current?.error ?? null,
    reload,
  }
}

import { useEffect, useState } from 'react'
import type { ApiClient } from '../api/client'
import { fetchLogo } from '../api/letterhead'

/** The logo as an object URL, fetched again when its version changes; released when replaced. */
export function useLogoUrl(client: ApiClient, version: string | null) {
  const [url, setUrl] = useState<{ version: string; url: string } | null>(null)
  useEffect(() => {
    if (version === null) return
    let live = true
    let created: string | null = null
    fetchLogo(client)
      .then((blob) => {
        if (!live || blob === null) return
        created = URL.createObjectURL(blob)
        setUrl({ version, url: created })
      })
      .catch(() => undefined) // the preview then shows no logo; the page stays usable
    return () => {
      live = false
      if (created !== null) URL.revokeObjectURL(created)
    }
  }, [client, version])
  return version !== null && url?.version === version ? url.url : null
}

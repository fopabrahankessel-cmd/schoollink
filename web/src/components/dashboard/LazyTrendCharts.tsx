import { Box } from '@mui/material'
import { lazy, Suspense, type ComponentProps } from 'react'
import type { TrendCharts as Charts } from './TrendCharts'

// Recharts is large: it is fetched only when a dashboard shows a chart, not with the rest of the app.
const TrendCharts = lazy(() => import('./TrendCharts').then((m) => ({ default: m.TrendCharts })))

/** The trend charts, keeping their place while the chart code loads. */
export function LazyTrendCharts(props: ComponentProps<typeof Charts>) {
  return (
    <Suspense fallback={<Box sx={{ minHeight: 276 }} />}>
      <TrendCharts {...props} />
    </Suspense>
  )
}

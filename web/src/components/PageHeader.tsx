import { Box, Stack, Typography } from '@mui/material'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import { ArrowLeftIcon, type Icon } from '../icons'
import { theme, tokens } from '../theme'

interface Props {
  title: string
  description?: ReactNode
  icon?: Icon
  /** Buttons on the right; they wrap under the title on a phone. */
  actions?: ReactNode
  back?: { to: string; label: string }
}

/**
 * A screen's title bar. It sticks to the top of the sheet while the screen scrolls, and gains a
 * hairline once content passes under it, so the title and its actions stay in reach.
 */
export function PageHeader({ title, description, icon: TitleIcon, actions, back }: Props) {
  const sentinel = useRef<HTMLDivElement>(null)
  const [stuck, setStuck] = useState(false)
  useEffect(() => {
    const node = sentinel.current
    if (!node || typeof IntersectionObserver === 'undefined') return
    const observer = new IntersectionObserver(([entry]) => setStuck(!entry.isIntersecting))
    observer.observe(node)
    return () => observer.disconnect()
  }, [])

  return (
    <>
      <Box ref={sentinel} aria-hidden sx={{ height: 0 }} />
      <Box
        component="header"
        sx={{
          position: 'sticky',
          top: 0,
          zIndex: 3,
          mx: { xs: -2, sm: -3, lg: -5 },
          px: { xs: 2, sm: 3, lg: 5 },
          pt: { xs: 2, sm: 3.5 },
          pb: 2,
          mb: { xs: 1, sm: 2 },
          bgcolor: tokens.surface,
          borderBottom: `1px solid ${stuck ? tokens.border : 'transparent'}`,
          transition: 'border-color 150ms ease',
          // on a phone the header would take a quarter of the screen: it scrolls away there
          [theme.breakpoints.down('sm')]: { position: 'static', borderBottomColor: 'transparent' },
        }}
      >
        {back && (
          <Box
            component={Link}
            to={back.to}
            sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.75, mb: 1.5, fontSize: 13, color: tokens.muted, minHeight: 24 }}
          >
            <ArrowLeftIcon size={16} aria-hidden />
            {back.label}
          </Box>
        )}
        <Stack
          direction="row"
          sx={{ alignItems: 'center', justifyContent: 'space-between', gap: 2, [theme.breakpoints.down('sm')]: { flexWrap: 'wrap' } }}
        >
          <Stack direction="row" sx={{ alignItems: 'center', gap: 1.75, minWidth: 0 }}>
            {TitleIcon && (
              <Box
                aria-hidden
                sx={{
                  width: 44,
                  height: 44,
                  flex: 'none',
                  display: 'grid',
                  placeItems: 'center',
                  borderRadius: `${tokens.radius.card}px`,
                  bgcolor: tokens.brandSoft,
                  color: tokens.brandDark,
                  [theme.breakpoints.down('sm')]: { display: 'none' },
                }}
              >
                <TitleIcon size={24} weight="duotone" />
              </Box>
            )}
            <Box sx={{ minWidth: 0 }}>
              <Typography variant="h5" component="h1" sx={{ [theme.breakpoints.down('sm')]: { fontSize: 20 } }}>
                {title}
              </Typography>
              {description && (
                <Typography color="text.secondary" sx={{ mt: 0.25, maxWidth: 640 }}>
                  {description}
                </Typography>
              )}
            </Box>
          </Stack>
          {actions && (
            <Stack direction="row" sx={{ gap: 1, flex: 'none', alignItems: 'center', flexWrap: 'wrap' }}>
              {actions}
            </Stack>
          )}
        </Stack>
      </Box>
    </>
  )
}

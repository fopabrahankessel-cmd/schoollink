import { alpha, createTheme } from '@mui/material/styles'

/**
 * Design tokens. Palette from the education (LMS) profile: a calm teal for the brand and the
 * selection, amber for what is in progress, green for done, red only for errors and danger. Every
 * text colour reaches 4.5:1 on the surface it sits on.
 */
export const tokens = {
  ground: '#eef3f3',
  surface: '#ffffff',
  raised: '#f6f9f9',
  active: '#e3f1ef',
  border: '#dfe7e7',
  rule: '#edf2f2',
  control: '#c5d1d1',
  text: '#10201f',
  muted: '#4a5b5a',
  brand: '#0f766e',
  brandDark: '#115e59',
  brandSoft: '#e3f1ef',
  brandTint: '#f0f8f7',
  amber: '#b45309',
  amberSoft: '#fef3c7',
  positive: '#15803d',
  negative: '#dc2626',
  warning: '#b45309',
  pill: {
    positive: { bg: '#dcfce7', ink: '#14532d' },
    negative: { bg: '#fee2e2', ink: '#7f1d1d' },
    warning: { bg: '#fef3c7', ink: '#78350f' },
    neutral: { bg: '#e8eeee', ink: '#334443' },
    brand: { bg: '#e3f1ef', ink: '#134e4a' },
  },
  radius: { sheet: 20, card: 12, control: 8, chip: 6 },
  shadow: {
    card: '0 1px 2px rgba(16, 32, 31, 0.04), 0 1px 3px rgba(16, 32, 31, 0.05)',
    raised: '0 6px 16px rgba(16, 32, 31, 0.08), 0 1px 3px rgba(16, 32, 31, 0.06)',
    float: '0 16px 40px rgba(16, 32, 31, 0.16), 0 2px 8px rgba(16, 32, 31, 0.08)',
  },
  sidebarWidth: 248,
  sheetInset: 10,
  /** Strong ease-out: the interface answers at once, then settles. */
  easeOut: 'cubic-bezier(0.23, 1, 0.32, 1)',
} as const

/** Pale identity tints for people and subjects (never data); picked by a stable hash of a key. */
const TINTS = [
  { bg: '#e0f2f1', ink: '#115e59' },
  { bg: '#e0ecfb', ink: '#1e4f8a' },
  { bg: '#fdeedc', ink: '#8a4b0f' },
  { bg: '#f3e8f5', ink: '#6b2f7a' },
  { bg: '#e6f4e1', ink: '#2f6b1f' },
  { bg: '#fde7e7', ink: '#8c2b2b' },
  { bg: '#e9edf5', ink: '#3a4766' },
  { bg: '#fff4cc', ink: '#6e5300' },
] as const

export function tintFor(key: string) {
  let hash = 0
  for (const char of key) hash = (hash * 31 + char.charCodeAt(0)) | 0
  return TINTS[Math.abs(hash) % TINTS.length]
}

const font = '"Plus Jakarta Sans", "Segoe UI", system-ui, sans-serif'
/** Hover effects only where a pointer really hovers (a tap would trigger them on touch screens). */
export const hover = '@media (hover: hover) and (pointer: fine)'
const reducedMotion = '@media (prefers-reduced-motion: reduce)'

export const theme = createTheme({
  palette: {
    primary: { main: tokens.brand, dark: tokens.brandDark, contrastText: '#ffffff' },
    secondary: { main: tokens.amber },
    success: { main: tokens.positive },
    error: { main: tokens.negative },
    warning: { main: tokens.warning },
    text: { primary: tokens.text, secondary: tokens.muted },
    divider: tokens.border,
    background: { default: tokens.ground, paper: tokens.surface },
  },
  shape: { borderRadius: tokens.radius.control },
  typography: {
    fontFamily: font,
    fontSize: 14,
    h4: { fontSize: 28, fontWeight: 700, lineHeight: 1.2, letterSpacing: '-0.02em' },
    h5: { fontSize: 24, fontWeight: 700, lineHeight: 1.25, letterSpacing: '-0.015em' },
    h6: { fontSize: 16, fontWeight: 700, lineHeight: 1.4 },
    subtitle1: { fontSize: 16, fontWeight: 600 },
    subtitle2: { fontSize: 14, fontWeight: 600 },
    body1: { fontSize: 14, lineHeight: 1.5 },
    body2: { fontSize: 14, lineHeight: 1.5 },
    caption: { fontSize: 12, lineHeight: 1.45 },
    button: { fontSize: 14, fontWeight: 600, textTransform: 'none' },
  },
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        body: { fontVariantNumeric: 'tabular-nums', WebkitFontSmoothing: 'antialiased' },
        a: {
          color: tokens.brand,
          fontWeight: 600,
          textDecoration: 'none',
          textUnderlineOffset: 3,
          [hover]: { '&:hover': { textDecoration: 'underline' } },
        },
        // Ripples are replaced by a press scale and a visible keyboard focus.
        ':focus-visible': { outline: `2px solid ${tokens.brand}`, outlineOffset: 2 },
        '::selection': { backgroundColor: alpha(tokens.brand, 0.18) },
        '@keyframes sl-dialog-in': {
          from: { opacity: 0, transform: 'scale(0.96) translateY(4px)' },
          to: { opacity: 1, transform: 'none' },
        },
      },
    },
    MuiButtonBase: { defaultProps: { disableRipple: true } },
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: {
        root: {
          minHeight: 38,
          paddingLeft: 14,
          paddingRight: 14,
          borderRadius: tokens.radius.control,
          whiteSpace: 'nowrap',
          transition: `transform 160ms ${tokens.easeOut}, background-color 150ms ease, border-color 150ms ease, box-shadow 150ms ease`,
          '&:active:not(.Mui-disabled)': { transform: 'scale(0.97)' },
          [reducedMotion]: { '&:active:not(.Mui-disabled)': { transform: 'none' } },
          variants: [
            {
              props: { variant: 'contained', color: 'primary' },
              style: {
                boxShadow: `0 1px 2px ${alpha(tokens.brandDark, 0.3)}, inset 0 1px 0 ${alpha('#ffffff', 0.12)}`,
                [hover]: { '&:hover': { backgroundColor: tokens.brandDark } },
              },
            },
            {
              props: { variant: 'outlined', color: 'primary' },
              style: {
                color: tokens.text,
                borderColor: tokens.control,
                backgroundColor: tokens.surface,
                [hover]: { '&:hover': { borderColor: tokens.brand, backgroundColor: tokens.brandTint } },
              },
            },
            {
              props: { variant: 'outlined', color: 'error' },
              style: { backgroundColor: tokens.surface, borderColor: alpha(tokens.negative, 0.45) },
            },
            {
              props: { variant: 'text', color: 'primary' },
              style: { [hover]: { '&:hover': { backgroundColor: tokens.brandTint } } },
            },
          ],
        },
        sizeSmall: { minHeight: 32, paddingLeft: 10, paddingRight: 10, fontSize: 13 },
        sizeLarge: { minHeight: 44, fontSize: 15 },
        startIcon: { marginRight: 6, '& > svg': { fontSize: 18 } },
        endIcon: { marginLeft: 6, '& > svg': { fontSize: 18 } },
      },
    },
    MuiIconButton: {
      styleOverrides: {
        root: {
          borderRadius: tokens.radius.control,
          color: tokens.muted,
          transition: `transform 160ms ${tokens.easeOut}, background-color 150ms ease, color 150ms ease`,
          '&:active': { transform: 'scale(0.92)' },
          [hover]: { '&:hover': { backgroundColor: tokens.raised, color: tokens.text } },
          [reducedMotion]: { '&:active': { transform: 'none' } },
        },
      },
    },
    MuiTooltip: {
      defaultProps: { enterDelay: 400 },
      styleOverrides: {
        tooltip: { backgroundColor: tokens.text, fontSize: 12, fontWeight: 500, borderRadius: 6, padding: '6px 8px' },
      },
    },
    MuiLink: { defaultProps: { underline: 'hover' } },
    MuiPaper: {
      styleOverrides: {
        outlined: { borderColor: tokens.border, borderRadius: tokens.radius.card, boxShadow: tokens.shadow.card },
      },
    },
    MuiCard: { defaultProps: { variant: 'outlined' } },
    MuiDialog: {
      defaultProps: { transitionDuration: { enter: 220, exit: 140 } },
      styleOverrides: {
        paper: {
          borderRadius: 16,
          boxShadow: tokens.shadow.float,
          animation: `sl-dialog-in 220ms ${tokens.easeOut}`,
          [reducedMotion]: { animation: 'none' },
        },
      },
    },
    MuiBackdrop: { styleOverrides: { root: { '&:not(.MuiBackdrop-invisible)': { backgroundColor: 'rgba(16, 32, 31, 0.4)' } } } },
    MuiDialogTitle: { styleOverrides: { root: { fontSize: 18, fontWeight: 700, padding: '22px 24px 8px' } } },
    MuiDialogContent: { styleOverrides: { root: { overscrollBehavior: 'contain' } } },
    MuiDialogActions: {
      styleOverrides: { root: { padding: '14px 24px 20px', gap: 8, borderTop: `1px solid ${tokens.rule}` } },
    },
    MuiDrawer: { styleOverrides: { paper: { borderRight: 0, boxShadow: tokens.shadow.float } } },
    MuiTextField: { defaultProps: { fullWidth: true, margin: 'normal', size: 'small' } },
    // Labels sit above their field, never inside it.
    MuiInputLabel: {
      defaultProps: { shrink: true },
      styleOverrides: {
        root: {
          position: 'relative',
          transform: 'none',
          marginBottom: 6,
          fontSize: 13,
          fontWeight: 600,
          lineHeight: 1.4,
          color: tokens.text,
          whiteSpace: 'normal',
          maxWidth: '100%',
          '&.Mui-focused': { color: tokens.text },
          '&.Mui-error': { color: tokens.negative },
          '&.Mui-disabled': { color: tokens.muted },
        },
        asterisk: { color: tokens.negative },
      },
    },
    MuiOutlinedInput: {
      defaultProps: { notched: false },
      styleOverrides: {
        root: {
          backgroundColor: tokens.surface,
          borderRadius: tokens.radius.control,
          transition: 'box-shadow 150ms ease',
          '& .MuiOutlinedInput-notchedOutline': { borderColor: tokens.control, top: 0, transition: 'border-color 150ms ease' },
          '& .MuiOutlinedInput-notchedOutline legend': { display: 'none' },
          [hover]: { '&:hover:not(.Mui-focused):not(.Mui-error) .MuiOutlinedInput-notchedOutline': { borderColor: '#94a6a5' } },
          '&.Mui-focused': { boxShadow: `0 0 0 3px ${alpha(tokens.brand, 0.16)}` },
          '&.Mui-focused .MuiOutlinedInput-notchedOutline': { borderColor: tokens.brand, borderWidth: 1 },
          '&.Mui-error.Mui-focused': { boxShadow: `0 0 0 3px ${alpha(tokens.negative, 0.14)}` },
        },
        // 38px, the height of a button beside it
        input: { minHeight: 22, paddingTop: 8, paddingBottom: 8, '&::placeholder': { color: '#6b7b7a', opacity: 1 } },
        adornedStart: { paddingLeft: 10 },
      },
    },
    MuiInputAdornment: { styleOverrides: { root: { color: tokens.muted } } },
    MuiFormHelperText: { styleOverrides: { root: { marginLeft: 0, marginRight: 0, marginTop: 6, fontSize: 12 } } },
    MuiTableCell: {
      styleOverrides: {
        root: { borderBottomColor: tokens.rule, fontSize: 14, paddingTop: 12, paddingBottom: 12 },
        head: {
          fontSize: 12,
          fontWeight: 600,
          color: tokens.muted,
          backgroundColor: tokens.raised,
          borderBottomColor: tokens.border,
          paddingTop: 10,
          paddingBottom: 10,
          whiteSpace: 'nowrap',
        },
        stickyHeader: { backgroundColor: tokens.raised },
        sizeSmall: { paddingTop: 10, paddingBottom: 10 },
      },
    },
    MuiTableRow: {
      styleOverrides: {
        root: {
          transition: 'background-color 120ms ease',
          '&:last-child > .MuiTableCell-body': { borderBottom: 0 },
          [hover]: { '&.MuiTableRow-hover:hover': { backgroundColor: tokens.brandTint } },
          '&.Mui-selected, &.Mui-selected:hover': { backgroundColor: tokens.active },
        },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: {
          height: 24,
          borderRadius: tokens.radius.chip,
          fontSize: 12,
          fontWeight: 600,
          flex: 'none',
          backgroundColor: tokens.pill.neutral.bg,
          color: tokens.pill.neutral.ink,
          variants: [
            { props: { color: 'success' }, style: { backgroundColor: tokens.pill.positive.bg, color: tokens.pill.positive.ink } },
            { props: { color: 'warning' }, style: { backgroundColor: tokens.pill.warning.bg, color: tokens.pill.warning.ink } },
            { props: { color: 'error' }, style: { backgroundColor: tokens.pill.negative.bg, color: tokens.pill.negative.ink } },
            { props: { color: 'primary' }, style: { backgroundColor: tokens.pill.brand.bg, color: tokens.pill.brand.ink } },
          ],
        },
        label: { paddingLeft: 8, paddingRight: 8 },
        icon: { marginLeft: 6, marginRight: -2, fontSize: 14, color: 'inherit' },
      },
    },
    MuiAlert: {
      styleOverrides: {
        root: {
          borderRadius: tokens.radius.card,
          fontSize: 14,
          alignItems: 'center',
          variants: [
            { props: { variant: 'standard', severity: 'error' }, style: { border: `1px solid ${alpha(tokens.negative, 0.2)}` } },
            {
              props: { variant: 'standard', severity: 'info' },
              style: { border: `1px solid ${alpha(tokens.brand, 0.2)}`, backgroundColor: tokens.brandTint, color: tokens.text },
            },
          ],
        },
      },
    },
    MuiTabs: {
      styleOverrides: {
        root: { minHeight: 40, borderBottom: `1px solid ${tokens.border}` },
        indicator: { height: 2, backgroundColor: tokens.brand, borderRadius: 2 },
      },
    },
    MuiTab: {
      styleOverrides: {
        root: {
          minHeight: 40,
          minWidth: 0,
          paddingLeft: 12,
          paddingRight: 12,
          color: tokens.muted,
          '&.Mui-selected': { color: tokens.text },
        },
      },
    },
    MuiToggleButton: {
      styleOverrides: {
        root: {
          height: 30,
          paddingLeft: 10,
          paddingRight: 10,
          fontSize: 12,
          fontWeight: 600,
          color: tokens.muted,
          borderColor: tokens.control,
          '&.Mui-selected, &.Mui-selected:hover': { backgroundColor: tokens.brandSoft, color: tokens.brandDark },
        },
      },
    },
    MuiLinearProgress: {
      styleOverrides: {
        root: { height: 8, borderRadius: 999, backgroundColor: tokens.rule },
        bar: { borderRadius: 999, backgroundColor: tokens.brand },
      },
    },
    MuiAvatar: { styleOverrides: { root: { fontSize: 13, fontWeight: 700 } } },
  },
})

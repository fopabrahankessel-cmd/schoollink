import { createTheme } from '@mui/material/styles'

/**
 * Design tokens (style "framed sheet"): a tinted frame around a white work sheet, colour kept for
 * data, status and the one primary action. The SchoolLink blue marks data, links and the selection.
 */
export const tokens = {
  ground: '#f9fbfc',
  surface: '#ffffff',
  raised: '#f4f6f8',
  active: '#ebedee',
  border: '#e8eaec',
  rule: '#f0f1f2',
  control: '#cdced1',
  text: '#131416',
  muted: '#5d5f60',
  accent: '#1d5d9b',
  positive: '#1e7b4f',
  negative: '#c4242a',
  warning: '#92400e',
  pill: {
    positive: { bg: '#dcfae5', ink: '#04250e' },
    negative: { bg: '#ffedea', ink: '#831c1e' },
    warning: { bg: '#fffbeb', ink: '#92400e' },
    neutral: { bg: '#ebedee', ink: '#353736' },
  },
  radius: { sheet: 16, card: 8, control: 6, chip: 4 },
  sidebarWidth: 228,
  sheetInset: 8,
  /** Strong ease-out: UI answers at once, then settles. */
  easeOut: 'cubic-bezier(0.23, 1, 0.32, 1)',
} as const

const font = '"IBM Plex Sans", Inter, system-ui, sans-serif'
/** Hover effects only where a pointer really hovers (taps would trigger them on touch screens). */
const hover = '@media (hover: hover) and (pointer: fine)'
const reducedMotion = '@media (prefers-reduced-motion: reduce)'

export const theme = createTheme({
  palette: {
    primary: { main: tokens.text, contrastText: '#ffffff' },
    secondary: { main: tokens.accent },
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
    // 12 / 14 / 16 / 22: four sizes do all the work
    h5: { fontSize: 22, fontWeight: 600, lineHeight: 1.3, letterSpacing: '-0.01em' },
    h6: { fontSize: 16, fontWeight: 600, lineHeight: 1.5 },
    subtitle1: { fontSize: 16, fontWeight: 500 },
    subtitle2: { fontSize: 14, fontWeight: 600 },
    body1: { fontSize: 14, lineHeight: 1.45 },
    body2: { fontSize: 14, lineHeight: 1.45 },
    caption: { fontSize: 12, lineHeight: 1.4 },
    button: { fontSize: 14, fontWeight: 500, textTransform: 'none' },
  },
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        body: { fontVariantNumeric: 'tabular-nums' },
        a: {
          color: tokens.accent,
          textDecoration: 'none',
          textUnderlineOffset: 3,
          [hover]: { '&:hover': { textDecoration: 'underline' } },
        },
        // Ripples are replaced by a press scale and a visible keyboard focus.
        ':focus-visible': { outline: `2px solid ${tokens.accent}`, outlineOffset: 2 },
        '@keyframes sl-dialog-in': {
          from: { opacity: 0, transform: 'scale(0.96)' },
          to: { opacity: 1, transform: 'scale(1)' },
        },
      },
    },
    MuiButtonBase: { defaultProps: { disableRipple: true } },
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: {
        root: {
          minHeight: 36,
          borderRadius: tokens.radius.control,
          transition: `transform 160ms ${tokens.easeOut}, background-color 120ms ease, border-color 120ms ease`,
          '&:active:not(.Mui-disabled)': { transform: 'scale(0.97)' },
          [reducedMotion]: { '&:active:not(.Mui-disabled)': { transform: 'none' } },
          variants: [
            {
              props: { variant: 'outlined', color: 'primary' },
              style: { [hover]: { '&:hover': { borderColor: tokens.text, backgroundColor: tokens.raised } } },
            },
          ],
        },
        sizeSmall: { minHeight: 30 },
        sizeLarge: { minHeight: 40 },
        outlined: { borderColor: tokens.control, backgroundColor: tokens.surface },
        text: { [hover]: { '&:hover': { backgroundColor: tokens.raised } } },
      },
    },
    MuiIconButton: {
      styleOverrides: {
        root: {
          transition: `transform 160ms ${tokens.easeOut}`,
          '&:active': { transform: 'scale(0.94)' },
          [reducedMotion]: { '&:active': { transform: 'none' } },
        },
      },
    },
    MuiLink: { defaultProps: { underline: 'hover', color: 'secondary' } },
    MuiCard: { defaultProps: { variant: 'outlined' } },
    MuiPaper: {
      styleOverrides: {
        outlined: { borderColor: tokens.border, borderRadius: tokens.radius.card },
      },
    },
    MuiDialog: {
      defaultProps: { transitionDuration: { enter: 200, exit: 140 } },
      styleOverrides: {
        paper: {
          borderRadius: 12,
          boxShadow: '0 12px 32px rgba(19, 20, 22, 0.14), 0 2px 6px rgba(19, 20, 22, 0.06)',
          animation: `sl-dialog-in 200ms ${tokens.easeOut}`,
          [reducedMotion]: { animation: 'none' },
        },
        backdrop: { backgroundColor: 'rgba(19, 20, 22, 0.32)' },
      },
    },
    MuiDialogTitle: { styleOverrides: { root: { fontSize: 16, fontWeight: 600, paddingBottom: 8 } } },
    MuiDialogActions: { styleOverrides: { root: { padding: '12px 24px 20px', gap: 4 } } },
    MuiTextField: { defaultProps: { fullWidth: true, margin: 'normal', size: 'small' } },
    // Labels sit above their field, never inside it.
    MuiInputLabel: {
      defaultProps: { shrink: true },
      styleOverrides: {
        root: {
          position: 'relative',
          transform: 'none',
          marginBottom: 8,
          fontSize: 14,
          fontWeight: 500,
          lineHeight: 1.4,
          color: tokens.text,
          whiteSpace: 'normal',
          maxWidth: '100%',
          '&.Mui-focused': { color: tokens.text },
          '&.Mui-error': { color: tokens.negative },
          '&.Mui-disabled': { color: tokens.muted },
        },
        asterisk: { color: tokens.muted },
      },
    },
    MuiOutlinedInput: {
      defaultProps: { notched: false },
      styleOverrides: {
        root: {
          backgroundColor: tokens.surface,
          borderRadius: tokens.radius.control,
          '& .MuiOutlinedInput-notchedOutline': { borderColor: tokens.control, top: 0 },
          '& .MuiOutlinedInput-notchedOutline legend': { display: 'none' },
          [hover]: { '&:hover:not(.Mui-focused):not(.Mui-error) .MuiOutlinedInput-notchedOutline': { borderColor: '#9fa1a5' } },
          '&.Mui-focused .MuiOutlinedInput-notchedOutline': { borderColor: tokens.accent, borderWidth: 1, boxShadow: `0 0 0 3px ${tokens.accent}22` },
        },
        // 36px, the same height as a button beside it
        input: { minHeight: 22, paddingTop: 7, paddingBottom: 7 },
      },
    },
    MuiFormHelperText: { styleOverrides: { root: { marginLeft: 0, marginRight: 0, fontSize: 12 } } },
    MuiTableCell: {
      styleOverrides: {
        root: { borderBottomColor: tokens.rule, fontSize: 14, paddingTop: 12, paddingBottom: 12 },
        head: { fontSize: 12, fontWeight: 400, color: tokens.muted, borderBottomColor: tokens.border, paddingTop: 8, paddingBottom: 8 },
        sizeSmall: { paddingTop: 10, paddingBottom: 10 },
      },
    },
    MuiTableRow: {
      styleOverrides: {
        root: {
          '&:last-child > .MuiTableCell-body': { borderBottom: 0 },
          [hover]: { '&.MuiTableRow-hover:hover': { backgroundColor: tokens.raised } },
          '&.Mui-selected, &.Mui-selected:hover': { backgroundColor: tokens.active },
        },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: {
          height: 22,
          borderRadius: tokens.radius.chip,
          fontSize: 12,
          fontWeight: 500,
          flex: 'none',
          backgroundColor: tokens.pill.neutral.bg,
          color: tokens.pill.neutral.ink,
          variants: [
            { props: { color: 'success' }, style: { backgroundColor: tokens.pill.positive.bg, color: tokens.pill.positive.ink } },
            { props: { color: 'warning' }, style: { backgroundColor: tokens.pill.warning.bg, color: tokens.pill.warning.ink } },
            { props: { color: 'error' }, style: { backgroundColor: tokens.pill.negative.bg, color: tokens.pill.negative.ink } },
          ],
        },
        label: { paddingLeft: 8, paddingRight: 8 },
      },
    },
    MuiAlert: {
      styleOverrides: { root: { borderRadius: tokens.radius.card, fontSize: 14 } },
    },
    MuiTabs: {
      styleOverrides: {
        root: { minHeight: 40, borderBottom: `1px solid ${tokens.border}` },
        indicator: { height: 2, backgroundColor: tokens.text },
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
          '&.Mui-selected': { color: tokens.text, fontWeight: 600 },
        },
      },
    },
    MuiToggleButton: {
      styleOverrides: {
        root: {
          height: 28,
          paddingLeft: 10,
          paddingRight: 10,
          fontSize: 12,
          fontWeight: 500,
          color: tokens.muted,
          borderColor: tokens.control,
          '&.Mui-selected, &.Mui-selected:hover': { backgroundColor: tokens.surface, color: tokens.text },
        },
      },
    },
    MuiLinearProgress: {
      styleOverrides: {
        root: { height: 6, borderRadius: 999, backgroundColor: tokens.active },
        bar: { borderRadius: 999, backgroundColor: tokens.accent },
      },
    },
  },
})

import { tokens } from '../theme'

/** SchoolLink's mark: a register line, ticked. Decorative; the name always sits beside it. */
export function Mark({ size = 24 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      <rect width="24" height="24" rx="6" fill={tokens.brand} />
      <path d="M5 8h7M5 12h5M5 16h7" stroke="#ffffff" strokeOpacity="0.55" strokeWidth="1.6" strokeLinecap="round" />
      <path d="m13.5 12.5 2.2 2.2 4.1-5" fill="none" stroke="#ffffff" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

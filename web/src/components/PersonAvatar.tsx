import { Avatar } from '@mui/material'
import { tintFor } from '../theme'

/** Initials on a pale tint that stays the same for a person (no photos, NFR-10). Decorative. */
export function PersonAvatar({ id, firstName, lastName, size = 36 }: { id: string; firstName: string; lastName: string; size?: number }) {
  const tint = tintFor(id)
  return (
    <Avatar
      aria-hidden
      sx={{ width: size, height: size, bgcolor: tint.bg, color: tint.ink, fontSize: Math.round(size * 0.36), flex: 'none' }}
    >
      {`${firstName.charAt(0)}${lastName.charAt(0)}`.toUpperCase()}
    </Avatar>
  )
}

import { Chip, Stack } from '@mui/material'
import { useTranslation } from 'react-i18next'
import { roleLabel, type RoleAssignment } from '../../api/staff'
import type { Block } from '../../api/structure'

export function RoleChips({ roles, blocks }: { roles: RoleAssignment[]; blocks: Block[] }) {
  const { t } = useTranslation()
  return (
    <Stack direction="row" spacing={0.5} useFlexGap sx={{ flexWrap: 'wrap' }}>
      {roles.map((r) => (
        <Chip key={r.id} size="small" label={roleLabel(r, blocks, t)} />
      ))}
    </Stack>
  )
}

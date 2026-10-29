import { Stack, Typography } from '@mui/material'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { PATHS, query, type AcademicYear, type Block, type SchoolClass } from '../../api/structure'
import { CrudSection } from '../../components/CrudSection'
import { YearPicker } from '../../components/YearPicker'
import { useList } from '../../hooks/useList'

/** UC-A1, D5: blocks (each with one DM) and the classes of an academic year, each in a block. */
export function ClassesPage() {
  const { t } = useTranslation()
  const years = useList<AcademicYear>(PATHS.years)
  const blocks = useList<Block>(PATHS.blocks)
  const [yearId, setYearId] = useState('')
  const blockName = (id: string) => blocks.items.find((b) => b.id === id)?.name ?? ''

  return (
    <Stack spacing={3}>
      <Typography variant="h5" component="h1">
        {t('structure.classes')}
      </Typography>
      <CrudSection<Block>
        title={t('structure.blocks')}
        itemName={t('structure.block')}
        path={PATHS.blocks}
        listPath={PATHS.blocks}
        emptyText={t('structure.noBlocks')}
        columns={[{ label: t('structure.name'), render: (b) => b.name }]}
        fields={[{ name: 'name', label: t('structure.name'), type: 'text' }]}
        toValues={(b) => ({ name: b.name })}
        onChanged={() => void blocks.reload()}
      />
      <YearPicker years={years.items} value={yearId} onChange={setYearId} />
      <CrudSection<SchoolClass>
        key={`classes-${yearId}`}
        title={t('structure.classes')}
        itemName={t('structure.schoolClass')}
        path={PATHS.classes}
        listPath={yearId ? PATHS.classes + query({ academicYearId: yearId }) : null}
        waitingText={t('structure.pickYearForClasses')}
        emptyText={t('structure.noClasses')}
        columns={[
          { label: t('structure.name'), render: (c) => c.name },
          { label: t('structure.level'), render: (c) => c.level },
          { label: t('structure.block'), render: (c) => blockName(c.blockId) },
        ]}
        fields={[
          { name: 'name', label: t('structure.name'), type: 'text' },
          { name: 'level', label: t('structure.level'), type: 'text' },
          {
            name: 'blockId',
            label: t('structure.block'),
            type: 'select',
            options: blocks.items.map((b) => ({ value: b.id, label: b.name })),
          },
        ]}
        toValues={(c) => ({ name: c.name, level: c.level, blockId: c.blockId })}
        fixed={yearId ? { academicYearId: yearId } : undefined}
      />
    </Stack>
  )
}

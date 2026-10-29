// Student and guardian import API (UC-A3, D19, docs/IMPORT.md): upload for a preview, then commit.
// The school comes from the signed-in user (NFR-8); the file holds generated data only in tests.

export const IMPORT_PATH = '/students/import'

/** The server's limits (docs/IMPORT.md), checked here too so a wrong file fails before upload. */
export const MAX_FILE_BYTES = 5 * 1024 * 1024
export const ACCEPTED_EXTENSIONS = ['.csv', '.xlsx'] as const

/** The headers a file must have; the template has them all. */
export const REQUIRED_COLUMNS = [
  'matricule',
  'nom',
  'prenom',
  'sexe',
  'annee_naissance',
  'classe',
  'tuteur1_nom',
  'tuteur1_telephone',
] as const

export type ImportStatus = 'PREVIEW' | 'COMMITTED' | 'EXPIRED'

/** One problem in one cell: the row as the spreadsheet numbers it (header = 1), the column as written. */
export interface ImportError {
  row: number
  column: string | null
  code: string
  message: string
}

export interface StudentImport {
  id: string
  academicYearId: string
  fileName: string
  status: ImportStatus
  rowCount: number
  validCount: number
  errorCount: number
  errors: ImportError[]
}

export interface ImportCommit {
  id: string
  status: ImportStatus
  studentsCreated: number
  guardiansCreated: number
  guardiansLinked: number
}

export function importPath(id: string): string {
  return `${IMPORT_PATH}/${id}`
}

/** null when the file can go to the server, otherwise the i18n key saying why not. */
export function fileProblem(file: File): string | null {
  const name = file.name.toLowerCase()
  if (!ACCEPTED_EXTENSIONS.some((ext) => name.endsWith(ext))) return 'import.fileType'
  if (file.size > MAX_FILE_BYTES) return 'import.fileTooBig'
  if (file.size === 0) return 'import.fileEmpty'
  return null
}

/**
 * The error list as a CSV the admin can open next to their file: ";" and a BOM, so Excel in
 * French opens it with its accents.
 */
export function errorsCsv(errors: ImportError[], headers: [string, string, string]): string {
  const cell = (text: string) => {
    // headers come from the uploaded file: never let a spreadsheet read one as a formula
    const value = /^[=+\-@\t\r]/.test(text) ? `'${text}` : text
    return /[";\n\r]/.test(value) ? `"${value.replaceAll('"', '""')}"` : value
  }
  const lines = [headers, ...errors.map((e) => [String(e.row), e.column ?? '', e.message])]
  return '﻿' + lines.map((line) => line.map(cell).join(';')).join('\r\n') + '\r\n'
}

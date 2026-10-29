/** "2026-09-07" -> "7 sept. 2026" / "Sep 7, 2026", in the interface language. */
export function formatDate(iso: string, language: string): string {
  const [year, month, day] = iso.split('-').map(Number)
  return new Intl.DateTimeFormat(language, { dateStyle: 'medium' }).format(new Date(year, month - 1, day))
}

/** The year that contains today, else the most recent one. */
export function currentYear<T extends { startDate: string; endDate: string }>(years: T[], today = new Date()): T | undefined {
  const iso = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
  return years.find((y) => y.startDate <= iso && iso <= y.endDate) ?? [...years].sort((a, b) => b.startDate.localeCompare(a.startDate))[0]
}

/** A file size in the reader's language: "12 ko", "1.4 MB". */
export function formatFileSize(bytes: number, language: string): string {
  const mega = bytes >= 1024 * 1024
  return new Intl.NumberFormat(language, {
    style: 'unit',
    unit: mega ? 'megabyte' : 'kilobyte',
    maximumFractionDigits: mega ? 1 : 0,
  }).format(mega ? bytes / 1024 / 1024 : Math.max(1, bytes / 1024))
}

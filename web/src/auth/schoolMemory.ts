/** D16: the school picked at sign-in, remembered on this device. */
export const SCHOOL_KEY = 'schoollink.schoolId'

export function rememberedSchool(): string | null {
  try {
    return localStorage.getItem(SCHOOL_KEY)
  } catch {
    return null
  }
}

export function rememberSchool(schoolId: string): void {
  try {
    localStorage.setItem(SCHOOL_KEY, schoolId)
  } catch {
    // storage blocked
  }
}

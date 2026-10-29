import type { Role } from '../api/client'

/** SPEC §3: the web dashboard serves these roles; the others use the mobile app. */
export const WEB_ROLES: readonly Role[] = ['ADMIN', 'DM', 'COUNSELLOR', 'RESEARCHER']

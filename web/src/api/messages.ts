// UC-D4, UC-A5, D38: messages from the DM and announcements from the administrator to parents. The
// school, the author and the DM's classes come from the signed-in user (NFR-8), never from a
// parameter. Phone numbers never appear here (NFR-9).

export type MessageKind = 'DM_MESSAGE' | 'ANNOUNCEMENT'

export type Audience = 'STUDENT' | 'CLASS' | 'SCHOOL'

/** The longest text the server accepts (D38). */
export const MAX_MESSAGE_LENGTH = 480

/** What the author is writing; `id` is made once per composer, so sending twice sends once. */
export interface MessageDraft {
  id: string
  studentId?: string
  classId?: string
  body: string
  sms: boolean
  plainSms: boolean
}

/** Who would get it and what its SMS would cost; nothing is sent. */
export interface MessagePreview {
  recipients: number
  byApp: number
  bySms: number
  unreached: number
  smsUnits: number
  smsUnicode: boolean
  smsLeft: number
  estimatedCost: number
  overCap: boolean
  smsSample: string | null
}

/** One count per guardian and channel. */
export interface Delivery {
  recipients: number
  pending: number
  sent: number
  delivered: number
  opened: number
  failed: number
}

export interface SentMessage {
  id: string
  kind: MessageKind
  audience: Audience
  studentId: string | null
  studentName: string | null
  classId: string | null
  className: string | null
  body: string
  sms: boolean
  authorName: string
  createdAt: string
  delivery: Delivery
}

export interface ClassOption {
  classId: string
  className: string
  students: number
}

export const MESSAGE_PATHS = {
  send: (kind: MessageKind) => (kind === 'DM_MESSAGE' ? '/messages' : '/announcements'),
  preview: (kind: MessageKind) => (kind === 'DM_MESSAGE' ? '/messages/preview' : '/announcements/preview'),
  sent: (kind: MessageKind) => (kind === 'DM_MESSAGE' ? '/messages' : '/announcements'),
  classes: '/messages/classes',
  /** D41: the DM's messages to one student's parents, for the student page. */
  ofStudent: (studentId: string) => `/messages?studentId=${encodeURIComponent(studentId)}`,
}

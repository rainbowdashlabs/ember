/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

/** How a mailbox connection is secured. */
export const MailSecurity = {
    SSL: 'SSL',
    STARTTLS: 'STARTTLS',
    NONE: 'NONE',
} as const

export type MailSecurityName = (typeof MailSecurity)[keyof typeof MailSecurity]

/** What becomes of a message once its attachments have been dealt with. Deleting is not offered. */
export const MailRuleAction = {
    NOTHING: 'NOTHING',
    MARK_SEEN: 'MARK_SEEN',
    FLAG: 'FLAG',
    MOVE: 'MOVE',
} as const

export type MailRuleActionName = (typeof MailRuleAction)[keyof typeof MailRuleAction]

/** Where the title of a filed document comes from. */
export const MailTitleSource = {
    SUBJECT: 'SUBJECT',
    FILE_NAME: 'FILE_NAME',
} as const

export type MailTitleSourceName = (typeof MailTitleSource)[keyof typeof MailTitleSource]

/** What became of one attachment. The grain is the attachment, so one mail can report several. */
export const MailImportOutcome = {
    IMPORTED: 'IMPORTED',
    DUPLICATE: 'DUPLICATE',
    SENDER_NOT_ALLOWED: 'SENDER_NOT_ALLOWED',
    NO_RULE_MATCHED: 'NO_RULE_MATCHED',
    TYPE_NOT_ALLOWED: 'TYPE_NOT_ALLOWED',
    TOO_LARGE: 'TOO_LARGE',
    TOO_SMALL: 'TOO_SMALL',
    NO_ATTACHMENT: 'NO_ATTACHMENT',
    QUOTA_EXCEEDED: 'QUOTA_EXCEEDED',
    AUTHENTICATION_FAILED: 'AUTHENTICATION_FAILED',
    FAILED: 'FAILED',
} as const

export type MailImportOutcomeName = (typeof MailImportOutcome)[keyof typeof MailImportOutcome]

/**
 * What the operator has decided, which the page needs before it can offer anything sensible: a page that
 * did not know the floor would offer an interval the server then quietly overrode.
 */
export interface MailImportSettings {
    enabled: boolean
    minimumIntervalMinutes: number
    maxAttachmentsPerCycle: number
    logRetentionDays: number
    /** Whether an encryption key is configured. Without one a mailbox cannot be saved at all. */
    canStorePasswords: boolean
    supportedTypes: string[]
}

/** A mailbox as the page reads it. The password is never handed back, not even as a length. */
export interface Mailbox {
    id: number
    name: string
    host: string
    port: number
    security: MailSecurityName
    username: string
    folder: string
    enabled: boolean
    intervalMinutes: number
    importFrom: string
    /**
     * Whether a message has to carry a valid signature whose domain matches the sender address before a
     * rule may take it. Off unless the station's correspondents sign their mail, because otherwise it
     * refuses everything.
     */
    verifyDkim: boolean
    lastCheckAt?: string | null
    lastError?: string | null
    failureCount: number
    suspended: boolean
}

export interface MailboxRequest {
    name: string
    host: string
    port: number
    security: MailSecurityName
    username: string
    /** Only on the way in, and only when it is being set. */
    password?: string | null
    folder: string
    enabled: boolean
    intervalMinutes: number
    importFrom: string
    verifyDkim: boolean
}

export interface MailRule {
    id: number
    mailboxId: number
    name: string
    position: number
    enabled: boolean
    subjectFilter?: string | null
    attachmentNameFilter?: string | null
    acceptedTypes: string[]
    minSizeBytes: number
    includeInline: boolean
    titleSource: MailTitleSourceName
    hidden: boolean
    keepOnArchive: boolean
    readSubjectForMember: boolean
    action: MailRuleActionName
    moveToFolder?: string | null
    senderPatterns: string[]
    tags: string[]
}

export type MailRuleRequest = Omit<MailRule, 'id' | 'mailboxId'>

export interface MailImportLogEntry {
    id: number
    mailboxId: number
    ruleId?: number | null
    ruleName?: string | null
    sender?: string | null
    subject?: string | null
    attachmentName?: string | null
    outcome: MailImportOutcomeName
    reason?: string | null
    documentId?: number | null
    /** Whether the readable half has been cleared by the pruning, leaving the row as a key. */
    pruned: boolean
    createdAt: string
}

export interface MailImportLogPage {
    entries: MailImportLogEntry[]
    total: number
}

/** What a connection test came to. It connects, lists the folders, and sends nothing. */
export interface MailboxTestResult {
    connected: boolean
    error?: string | null
    folders: string[]
    folderExists: boolean
    writesAuthResult: boolean
}

export interface MailImportCycle {
    looked: number
    imported: number
    refused: number
}

/**
 * The two forms a sender pattern may take, checked here as well as on the server so somebody typing one
 * is told before they save rather than after.
 *
 * <p>There is deliberately no general pattern language: this is the check that decides whether a stranger
 * can put files into the station's document store.
 */
export function isValidSenderPattern(pattern: string): boolean {
    const candidate = pattern.trim().toLowerCase()
    if (!candidate || candidate.includes(' ')) return false
    if (candidate.startsWith('*@')) return isDomain(candidate.slice(2))
    if (candidate.includes('*')) return false
    const at = candidate.indexOf('@')
    if (at <= 0 || at !== candidate.lastIndexOf('@')) return false
    return isDomain(candidate.slice(at + 1))
}

function isDomain(domain: string): boolean {
    if (!domain || domain.includes('*')) return false
    if (domain.startsWith('.') || domain.endsWith('.')) return false
    return domain.indexOf('.') > 0
}

/** Whether an outcome is the one that produced a document, which is what the page colours differently. */
export function wasImported(outcome: MailImportOutcomeName): boolean {
    return outcome === MailImportOutcome.IMPORTED
}

export async function settings(): Promise<MailImportSettings> {
    const res = await client.get<MailImportSettings>('/station/mail-import/settings')
    return res.data
}

export async function listMailboxes(): Promise<Mailbox[]> {
    const res = await client.get<Mailbox[]>('/station/mail-import/mailboxes')
    return res.data
}

export async function createMailbox(data: MailboxRequest): Promise<Mailbox> {
    const res = await client.post<Mailbox>('/station/mail-import/mailboxes', data)
    return res.data
}

export async function updateMailbox(id: number, data: MailboxRequest): Promise<Mailbox> {
    const res = await client.put<Mailbox>(`/station/mail-import/mailboxes/${id}`, data)
    return res.data
}

export async function updatePassword(id: number, password: string): Promise<void> {
    await client.put(`/station/mail-import/mailboxes/${id}/password`, {password})
}

export async function deleteMailbox(id: number): Promise<void> {
    await client.delete(`/station/mail-import/mailboxes/${id}`)
}

export async function testMailbox(id: number): Promise<MailboxTestResult> {
    const res = await client.post<MailboxTestResult>(`/station/mail-import/mailboxes/${id}/test`)
    return res.data
}

export async function runNow(id: number): Promise<MailImportCycle> {
    const res = await client.post<MailImportCycle>(`/station/mail-import/mailboxes/${id}/run`)
    return res.data
}

export async function resumeMailbox(id: number): Promise<Mailbox> {
    const res = await client.post<Mailbox>(`/station/mail-import/mailboxes/${id}/resume`)
    return res.data
}

export async function listRules(mailboxId: number): Promise<MailRule[]> {
    const res = await client.get<MailRule[]>(`/station/mail-import/mailboxes/${mailboxId}/rules`)
    return res.data
}

export async function createRule(mailboxId: number, data: MailRuleRequest): Promise<MailRule> {
    const res = await client.post<MailRule>(`/station/mail-import/mailboxes/${mailboxId}/rules`, data)
    return res.data
}

export async function updateRule(ruleId: number, data: MailRuleRequest): Promise<MailRule> {
    const res = await client.put<MailRule>(`/station/mail-import/rules/${ruleId}`, data)
    return res.data
}

export async function deleteRule(ruleId: number): Promise<void> {
    await client.delete(`/station/mail-import/rules/${ruleId}`)
}

export async function log(page = 0, size = 50): Promise<MailImportLogPage> {
    const res = await client.get<MailImportLogPage>('/station/mail-import/log', {params: {page, size}})
    return res.data
}

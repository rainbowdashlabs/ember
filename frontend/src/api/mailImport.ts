/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {
    components,
    CycleResponse,
    InstanceSettingsResponse,
    LogPageResponse,
    MailboxRequest,
    MailboxResponse,
    RuleRequest,
    RuleResponse,
    TestResult,
} from './generated/schema'

type Schemas = components['schemas']

export type MailSecurityName = Schemas['MailSecurity']

/** How a mailbox connection is secured. */
export const MailSecurity = {
    SSL: 'SSL',
    STARTTLS: 'STARTTLS',
    NONE: 'NONE',
} as const satisfies Record<MailSecurityName, MailSecurityName>

export type MailRuleActionName = Schemas['MailRuleAction']

/** What becomes of a message once its attachments have been dealt with. Deleting is not offered. */
export const MailRuleAction = {
    NOTHING: 'NOTHING',
    MARK_SEEN: 'MARK_SEEN',
    FLAG: 'FLAG',
    MOVE: 'MOVE',
} as const satisfies Record<MailRuleActionName, MailRuleActionName>

export type MailTitleSourceName = Schemas['MailTitleSource']

/** Where the title of a filed document comes from. */
export const MailTitleSource = {
    SUBJECT: 'SUBJECT',
    FILE_NAME: 'FILE_NAME',
} as const satisfies Record<MailTitleSourceName, MailTitleSourceName>

export type MailImportOutcomeName = Schemas['MailImportOutcome']

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
    NO_SIGNATURE: 'NO_SIGNATURE',
    SIGNATURE_NOT_ALIGNED: 'SIGNATURE_NOT_ALIGNED',
    SIGNATURE_FAILED: 'SIGNATURE_FAILED',
    FAILED: 'FAILED',
} as const satisfies Record<MailImportOutcomeName, MailImportOutcomeName>

/** A mailbox as its editor holds it: every field filled in, the password only when it is being set. */
export type MailboxDraft = Required<MailboxRequest>

/** A rule as its editor holds it: every field filled in. */
export type MailRuleDraft = Required<RuleRequest>

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

/**
 * What the operator has decided, which the page needs before it can offer anything sensible: a page that
 * did not know the floor would offer an interval the server then quietly overrode.
 */
export async function settings(): Promise<InstanceSettingsResponse> {
    const res = await client.get<InstanceSettingsResponse>('/station/mail-import/settings')
    return res.data
}

/** The station's mailboxes. The password is never handed back, not even as a length. */
export async function listMailboxes(): Promise<MailboxResponse[]> {
    const res = await client.get<MailboxResponse[]>('/station/mail-import/mailboxes')
    return res.data
}

export async function createMailbox(data: MailboxRequest): Promise<MailboxResponse> {
    const res = await client.post<MailboxResponse>('/station/mail-import/mailboxes', data)
    return res.data
}

export async function updateMailbox(id: number, data: MailboxRequest): Promise<MailboxResponse> {
    const res = await client.put<MailboxResponse>(`/station/mail-import/mailboxes/${id}`, data)
    return res.data
}

export async function updatePassword(id: number, password: string): Promise<void> {
    await client.put(`/station/mail-import/mailboxes/${id}/password`, {password})
}

export async function deleteMailbox(id: number): Promise<void> {
    await client.delete(`/station/mail-import/mailboxes/${id}`)
}

/** Connects, lists the folders, and sends nothing. */
export async function testMailbox(id: number): Promise<TestResult> {
    const res = await client.post<TestResult>(`/station/mail-import/mailboxes/${id}/test`)
    return res.data
}

export async function runNow(id: number): Promise<CycleResponse> {
    const res = await client.post<CycleResponse>(`/station/mail-import/mailboxes/${id}/run`)
    return res.data
}

export async function resumeMailbox(id: number): Promise<MailboxResponse> {
    const res = await client.post<MailboxResponse>(`/station/mail-import/mailboxes/${id}/resume`)
    return res.data
}

export async function listRules(mailboxId: number): Promise<RuleResponse[]> {
    const res = await client.get<RuleResponse[]>(`/station/mail-import/mailboxes/${mailboxId}/rules`)
    return res.data
}

export async function createRule(mailboxId: number, data: RuleRequest): Promise<RuleResponse> {
    const res = await client.post<RuleResponse>(`/station/mail-import/mailboxes/${mailboxId}/rules`, data)
    return res.data
}

export async function updateRule(ruleId: number, data: RuleRequest): Promise<RuleResponse> {
    const res = await client.put<RuleResponse>(`/station/mail-import/rules/${ruleId}`, data)
    return res.data
}

export async function deleteRule(ruleId: number): Promise<void> {
    await client.delete(`/station/mail-import/rules/${ruleId}`)
}

/** What became of each attachment, newest first, a page at a time. */
export async function log(page = 0, size = 50): Promise<LogPageResponse> {
    const res = await client.get<LogPageResponse>('/station/mail-import/log', {params: {page, size}})
    return res.data
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {ConversationMessageResponse} from '@/api/generated/schema'

/** How close two messages of one author have to be to read as one run, in milliseconds. */
export const RUN_WINDOW_MS = 7 * 60 * 1000

/** One line of a conversation as it is drawn. */
export type TimelineItem =
    | {kind: 'day', key: string, day: string}
    | {kind: 'unread', key: string}
    | {kind: 'message', key: string, message: ConversationMessageResponse, opensRun: boolean}

function dayOf(iso: string): string {
    const moment = new Date(iso)
    return `${moment.getFullYear()}-${String(moment.getMonth() + 1).padStart(2, '0')}-${String(moment.getDate()).padStart(2, '0')}`
}

function continuesRun(previous: ConversationMessageResponse | null, message: ConversationMessageResponse): boolean {
    if (!previous || previous.systemKind || message.systemKind) return false
    if (!previous.author || !message.author || previous.author.uid !== message.author.uid) return false
    return new Date(message.createdAt).getTime() - new Date(previous.createdAt).getTime() <= RUN_WINDOW_MS
}

/**
 * The lines of a conversation: a separator before each new day, the unread line before the first
 * message after the reader's marker, and each message marked as opening a run of one author or
 * following on within seven minutes.
 *
 * @param messages        the messages, oldest first
 * @param unreadAfter     the message the unread part starts after, or null for none
 * @param readerUid       the reader, whose own messages never count as unread
 * @returns the lines in order
 */
export function timelineOf(
    messages: readonly ConversationMessageResponse[],
    unreadAfter: number | null,
    readerUid: string | null,
): TimelineItem[] {
    const items: TimelineItem[] = []
    let previous: ConversationMessageResponse | null = null
    let unreadShown = unreadAfter === null
    for (const message of messages) {
        const day = dayOf(message.createdAt)
        const newDay = !previous || dayOf(previous.createdAt) !== day
        if (newDay) items.push({kind: 'day', key: `day-${day}`, day: message.createdAt})
        const unread = !unreadShown && message.id > (unreadAfter ?? 0) && message.author?.uid !== readerUid
        if (unread) {
            items.push({kind: 'unread', key: 'unread'})
            unreadShown = true
        }
        items.push({
            kind: 'message',
            key: `message-${message.id}`,
            message,
            opensRun: newDay || unread || !continuesRun(previous, message),
        })
        previous = message
    }
    return items
}

/**
 * Puts a message into a conversation's list where it belongs by id, replacing the copy there.
 *
 * @param messages the messages, oldest first
 * @param message  the message as it is now
 * @returns the list with the message in place
 */
export function withMessage(
    messages: readonly ConversationMessageResponse[],
    message: ConversationMessageResponse,
): ConversationMessageResponse[] {
    const others = messages.filter(existing => existing.id !== message.id)
    const index = others.findIndex(existing => existing.id > message.id)
    if (index < 0) return [...others, message]
    return [...others.slice(0, index), message, ...others.slice(index)]
}

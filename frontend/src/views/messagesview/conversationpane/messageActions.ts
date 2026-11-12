/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {ConversationMessageResponse, MessagePersonResponse} from '@/api/generated/schema'

/** The six reactions offered at a click, before the full picker. */
export const QUICK_REACTIONS: readonly string[] = ['👍', '❤️', '😂', '😮', '😢', '🎉']

/**
 * What a message in an open conversation can do and needs to know, handed to every message as one
 * prop so that no message passes events up through the timeline.
 */
export interface MessageActions {
    /** The reader, whose own messages can be changed and deleted. */
    readerUid: string | null
    /** The people the reader blocked, whose messages are folded away. */
    blocked: readonly string[]
    reply: (message: ConversationMessageResponse) => void
    edit: (message: ConversationMessageResponse) => void
    remove: (message: ConversationMessageResponse) => void
    react: (message: ConversationMessageResponse, emoji: string) => void
    block: (person: MessagePersonResponse) => void
    /** Scrolls to a message, as clicking the quote of an answered one does. */
    jumpTo: (messageId: number) => void
    /** Opens the actions of a message as a menu, as resting a finger on it does on a phone. */
    menu: (message: ConversationMessageResponse) => void
}

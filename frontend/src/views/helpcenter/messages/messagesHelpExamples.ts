/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {
    ConversationMessageResponse,
    ConversationSummaryResponse,
    MessagePersonResponse,
} from '@/api/generated/schema'
import type {Translate} from '@/util/failure'
import type {MessageActions} from '@/views/messagesview/conversationpane/messageActions'

/** The reader of the examples, whose own messages show as theirs. */
export const READER_UID = '00000000-0000-0000-0000-0000000000a1'

/** Actions that do nothing, because the examples only show what the page looks like. */
export const STILL_ACTIONS: MessageActions = {
    readerUid: READER_UID,
    blocked: [],
    reply: () => {},
    edit: () => {},
    remove: () => {},
    react: () => {},
    block: () => {},
    jumpTo: () => {},
    menu: () => {},
}

function person(uid: string, firstName: string, lastName: string): MessagePersonResponse {
    return {uid, firstName, lastName}
}

/**
 * The people of the examples, named in the reader's language.
 *
 * @param t the translator
 * @returns the reader and two others
 */
export function examplePeople(t: Translate) {
    return {
        reader: person(READER_UID, t('helpCenter.messages.example.readerFirst'), t('helpCenter.messages.example.readerLast')),
        lena: person('00000000-0000-0000-0000-0000000000a2', t('helpCenter.messages.example.lenaFirst'), t('helpCenter.messages.example.lenaLast')),
        tom: person('00000000-0000-0000-0000-0000000000a3', t('helpCenter.messages.example.tomFirst'), t('helpCenter.messages.example.tomLast')),
    }
}

/**
 * Two rows of the list: an unread direct conversation and a muted group.
 *
 * @param t the translator
 * @returns the rows
 */
export function exampleConversations(t: Translate): ConversationSummaryResponse[] {
    const {lena, tom} = examplePeople(t)
    return [
        {
            uid: '00000000-0000-0000-0000-0000000000c1', kind: 'DIRECT', name: null, others: [lena], muted: false, unread: 2,
            lastActivityAt: new Date(Date.now() - 5 * 60_000).toISOString(),
            newest: {id: 3, author: lena, text: t('helpCenter.messages.example.question'), systemKind: null, subject: null, deleted: false, createdAt: new Date().toISOString()},
        },
        {
            uid: '00000000-0000-0000-0000-0000000000c2', kind: 'GROUP', name: t('helpCenter.messages.example.groupName'), others: [lena, tom], muted: true, unread: 4,
            lastActivityAt: new Date(Date.now() - 3 * 3_600_000).toISOString(),
            newest: {id: 9, author: tom, text: t('helpCenter.messages.example.groupLine'), systemKind: null, subject: null, deleted: false, createdAt: new Date().toISOString()},
        },
    ]
}

function message(id: number, author: MessagePersonResponse, content: string, minutesAgo: number): ConversationMessageResponse {
    return {
        id, author, content, replyTo: null, systemKind: null, subject: null,
        createdAt: new Date(Date.now() - minutesAgo * 60_000).toISOString(), editedAt: null, deleted: false, reactions: [],
    }
}

/**
 * A stretch of a conversation: a run of one author, an answer with reactions, a changed message, a
 * deleted one and a system line.
 *
 * @param t the translator
 * @returns the messages, oldest first
 */
export function exampleMessages(t: Translate): ConversationMessageResponse[] {
    const {reader, lena} = examplePeople(t)
    const question = message(1, lena, t('helpCenter.messages.example.question'), 30)
    return [
        question,
        message(2, lena, t('helpCenter.messages.example.followUp'), 28),
        {
            ...message(3, reader, t('helpCenter.messages.example.answer'), 10),
            replyTo: {id: 1, author: lena, text: question.content, deleted: false},
            reactions: [{emoji: '👍', count: 1, mine: false, people: [lena]}],
            editedAt: new Date().toISOString(),
        },
        {...message(4, lena, '', 5), deleted: true},
        {...message(5, reader, '', 2), systemKind: 'ADDED', subject: examplePeople(t).tom},
    ]
}

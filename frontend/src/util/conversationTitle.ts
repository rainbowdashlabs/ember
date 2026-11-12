/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** Somebody in messages, by the account's register name. */
export interface MessagePersonName {
    uid: string
    firstName: string
    lastName: string
}

/**
 * The full register name of somebody in messages.
 *
 * @param person the person
 * @returns first and last name
 */
export function personName(person: MessagePersonName): string {
    return `${person.firstName} ${person.lastName}`.trim()
}

/**
 * What a conversation is called: a group by its name or, without one, by the first names of the
 * others in it; a direct conversation by the other person's name.
 *
 * @param conversation the conversation's name and the other people in it
 * @param fallback     what to call it when nobody else is left in it
 * @returns the title
 */
export function conversationTitle(
    conversation: {kind: string, name?: string | null, others: readonly MessagePersonName[]},
    fallback: string,
): string {
    if (conversation.name) return conversation.name
    const [first] = conversation.others
    if (!first) return fallback
    if (conversation.kind === 'DIRECT') return personName(first)
    return conversation.others.map(person => person.firstName).join(', ')
}

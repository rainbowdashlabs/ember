/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {MemberCompletion} from '@/api/generated/schema'

/**
 * What a mention completion names in messages instead of a station: messages are between
 * accounts, so the person behind a mention is an account and the station part says so.
 */
export const ACCOUNT_SCOPE = 'account'

/** One piece of a message's text as it is drawn. */
export type MessageSegment =
    | {kind: 'text', text: string}
    | {kind: 'link', text: string, href: string}
    | {kind: 'mention', uid: string, name: string}

const PIECES = /@\[([^/\]]+)\/([^:\]]+):([^\]]+)]|https?:\/\/[^\s<>"]+[^\s<>".,;:!?)\]'"]/g

/**
 * A message's text cut into plain text, links and mentions. Only addresses starting with http or
 * https become links, so nothing in a message can run script when clicked.
 *
 * @param content the text as it is kept
 * @returns the pieces in order
 */
export function segmentsOf(content: string): MessageSegment[] {
    const segments: MessageSegment[] = []
    let last = 0
    for (const match of content.matchAll(PIECES)) {
        const start = match.index ?? 0
        if (start > last) segments.push({kind: 'text', text: content.slice(last, start)})
        if (match[2] && match[3]) segments.push({kind: 'mention', uid: match[2], name: match[3]})
        else segments.push({kind: 'link', text: match[0], href: match[0]})
        last = start + match[0].length
    }
    if (last < content.length) segments.push({kind: 'text', text: content.slice(last)})
    return segments
}

/**
 * Whether a message mentions the given account.
 *
 * @param content the text as it is kept
 * @param uid     the account
 * @returns true when one of its mentions names the account
 */
export function mentions(content: string, uid: string): boolean {
    return segmentsOf(content).some(segment => segment.kind === 'mention' && segment.uid === uid)
}

/**
 * The people of a conversation as the mention completion offers them.
 *
 * @param people the people, each with an account identity and a name
 * @returns one completion per person
 */
export function mentionablesOf(people: readonly {uid: string, firstName: string, lastName: string}[]): MemberCompletion[] {
    return people.map((person, index) => ({
        id: index + 1,
        memberUid: person.uid,
        stationUid: ACCOUNT_SCOPE,
        name: `${person.firstName} ${person.lastName}`.trim(),
        stationName: null,
        nameColor: null,
        displayTag: null,
    }))
}

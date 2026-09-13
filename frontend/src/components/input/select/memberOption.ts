/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {MemberCompletion} from '@/api/stationMembers'
import type {MemberSearchResult} from '@/api/members'
import type {MemberIdentity} from '@/api/types'

/**
 * Somebody a member menu can offer.
 *
 * <p>The menu asks for people rather than for labelled values, because a face, a name colour and a tag
 * arrive with a person and are lost the moment they are flattened into a string. Every call site therefore
 * hands over what it already holds, through one of the adapters below.
 */
export interface MemberOption {
    /** What the menu reads and writes. A member id for most callers, a member UUID for the page editor. */
    value: string
    name: string
    /** Shown beside the name where a call site knows it, which is how two people of one name are told apart. */
    email?: string | null
    /** What kind of member they are, for the optional kind filter. */
    userType?: string | null
    identity?: MemberIdentity | null
}

/** The name to show for somebody whose own name was never filled in. */
function nameOrFallback(name: string | null | undefined, email: string | null | undefined, id: number): string {
    const trimmed = name?.trim()
    if (trimmed) return trimmed
    return email?.trim() || `#${id}`
}

/** A member of the caller's own station, as the completion endpoint hands them over. */
export function fromCompletion(member: MemberCompletion): MemberOption {
    return {
        value: String(member.id),
        name: member.name,
        identity: member,
    }
}

/**
 * Whatever a screen already holds about a member: the station list's shape, a group's, a tag's.
 *
 * <p>Deliberately structural rather than one named type. Half a dozen features carry their own record of
 * a member, all of them a row id with a name and some of what is known beside it, and asking each to
 * convert to a common one first would put the conversion back at every call site.
 */
export interface MemberLike {
    id: number
    name?: string | null
    email?: string | null
    userType?: string | null
    identity?: MemberIdentity | null
}

/**
 * A member as a list of them hands them over, which is the shape most screens already hold.
 *
 * <p>Somebody whose name was never filled in is called by their address, and then the address is not
 * repeated beneath it: a row reading the same thing twice looks like a fault rather than a fallback.
 */
export function fromMember(member: MemberLike): MemberOption {
    const name = nameOrFallback(member.name, member.email, member.id)
    return {
        value: String(member.id),
        name,
        email: member.email === name ? null : member.email,
        userType: member.userType,
        identity: member.identity ?? null,
    }
}

/**
 * A member's identity, as whatever is known about them allows.
 *
 * <p>A menu still draws a face and a colour for somebody a call site knew nothing about but a name, so
 * the name is carried into the identity rather than the row falling back to bare text.
 */
export function identityOf(option: MemberOption): MemberIdentity {
    if (!option.identity) return {name: option.name}
    return {...option.identity, name: option.identity.name ?? option.name}
}

/**
 * The kinds of member present among these people, for the filter beside the search.
 *
 * <p>Offering every kind the product has would let a reader empty the list by choosing one nobody on it
 * belongs to. Offering the kinds actually present cannot.
 */
export function userTypesOf(options: MemberOption[]): string[] {
    return [...new Set(options.map(option => option.userType).filter((type): type is string => !!type))]
}

/**
 * Somebody the server found.
 *
 * <p>The search sends the picture inline and identifies a person by their UUID rather than by the row id,
 * so a stored choice survives a station transfer.
 */
export function fromSearchResult(result: MemberSearchResult): MemberOption {
    return {
        value: result.memberUid,
        name: result.displayName,
        userType: result.userType,
        identity: {
            memberUid: result.memberUid,
            name: result.displayName,
            nameColor: result.nameColor,
            displayTag: result.displayTag && result.displayTagColor
                ? {name: result.displayTag, color: result.displayTagColor}
                : null,
            avatarUrl: result.avatarUrl,
        },
    }
}

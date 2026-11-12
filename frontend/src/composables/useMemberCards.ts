/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {inject, provide, type InjectionKey} from 'vue'
import {getMemberCard} from '@/api/members'
import {getPersonCards} from '@/api/messages'
import type {MemberCard, SharedMemberCard} from '@/api/generated/schema'
import {browserShallowRef} from '@/util/browserState'
import {sessionState} from '@/util/sessionState'

const MEMBER_CARDS_SHOWN: InjectionKey<boolean> = Symbol('memberCardsShown')

interface HeldCards {
    viewer: string
    byMember: Readonly<Record<string, Promise<MemberCard>>>
    byPerson: Readonly<Record<string, Promise<readonly SharedMemberCard[]>>>
}

const held = browserShallowRef<HeldCards>({viewer: '', byMember: {}, byPerson: {}})

/**
 * Who is looking, as far as it changes what a card shows: the account, the profile it acts as, the
 * station and the permissions there. A card read by one viewer is never handed to another.
 */
function currentViewer(): string {
    const info = sessionState().value.info
    if (!info) return ''
    return [info.account.id, info.member?.id ?? '', info.stationId ?? '', [...info.permissions].sort().join(',')].join('|')
}

function cardsOfViewer(): HeldCards {
    const viewer = currentViewer()
    if (held.value.viewer !== viewer) held.value = {viewer, byMember: {}, byPerson: {}}
    return held.value
}

/**
 * Asks once and keeps the answer for this viewer; a request that failed is forgotten again.
 */
function keep<T>(
    field: 'byMember' | 'byPerson',
    key: string,
    ask: () => Promise<T>,
): Promise<T> {
    const known = cardsOfViewer()[field][key] as Promise<T> | undefined
    if (known) return known
    const viewer = held.value.viewer
    const request = ask()
    held.value = {...held.value, [field]: {...held.value[field], [key]: request}}
    request.catch(() => {
        if (held.value.viewer !== viewer) return
        const rest = Object.fromEntries(Object.entries(held.value[field]).filter(([kept]) => kept !== key))
        held.value = {...held.value, [field]: rest}
    })
    return request
}

/**
 * Turns member cards off for everything below, for screens whose names belong to nobody real.
 *
 * <p>The help center draws its examples with the same name component as the app, from made-up
 * members. Asking the server about them would only ever answer that nobody of that name is here.
 */
export function hideMemberCards(): void {
    provide(MEMBER_CARDS_SHOWN, false)
}

/** Whether names below may open a member card, which they do unless a screen above said otherwise. */
export function useMemberCardsShown(): boolean {
    return inject(MEMBER_CARDS_SHOWN, true)
}

/**
 * The card of a member of the reader's station, asked once per member while the page is open.
 *
 * <p>Hovering down a list of twenty names asks for at most twenty cards, and hovering back up asks
 * for none. A request that failed is forgotten, so the next look tries again. The cards are kept per
 * viewer: signing in as somebody else, switching profile or station, or a change of permissions
 * starts over, so nobody sees what the card showed to the one before.
 *
 * @param memberUid the member looked at
 * @return their card
 */
export function loadMemberCard(memberUid: string): Promise<MemberCard> {
    return keep('byMember', memberUid, () => getMemberCard(memberUid))
}

/**
 * The cards of a person met as an account, in messages: one per station the reader shares with
 * them, kept the same way as a member's card.
 *
 * @param accountUid the person looked at
 * @return their cards, one per shared station
 */
export function loadPersonCards(accountUid: string): Promise<readonly SharedMemberCard[]> {
    return keep('byPerson', accountUid, () => getPersonCards(accountUid))
}

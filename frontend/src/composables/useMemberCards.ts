/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {inject, provide, type InjectionKey} from 'vue'
import {getMemberCard} from '@/api/members'
import type {MemberCard} from '@/api/generated/schema'
import {browserShallowRef} from '@/util/browserState'

const MEMBER_CARDS_SHOWN: InjectionKey<boolean> = Symbol('memberCardsShown')

const cards = browserShallowRef<Readonly<Record<string, Promise<MemberCard>>>>({})

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
 * for none. A request that failed is forgotten, so the next look tries again.
 *
 * @param memberUid the member looked at
 * @return their card
 */
export function loadMemberCard(memberUid: string): Promise<MemberCard> {
    const known = cards.value[memberUid]
    if (known) return known
    const request = getMemberCard(memberUid)
    cards.value = {...cards.value, [memberUid]: request}
    request.catch(() => {
        cards.value = Object.fromEntries(Object.entries(cards.value).filter(([uid]) => uid !== memberUid))
    })
    return request
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {useI18n} from 'vue-i18n'
import {events} from '@/api'
import {showToast} from '@/util/toast'

/** A place just given up, and how long the server said it would take it back. */
interface GivenUp {
    id: number
    undoUntil: string
}

/**
 * Giving up every place a household holds with one press, and one offer to put all of them back for as
 * long as the server would take them. The places are collected rather than offered back one at a time,
 * because one offer for the whole household reads better than three.
 *
 * @param registrationOf the standing registration of a member, if they hold one
 * @param reload         reads the screen back after every change
 */
export function useHouseholdSignOff(
    registrationOf: (memberId: number) => {id: number} | undefined,
    reload: () => Promise<void>,
) {
    const {t} = useI18n()

    async function giveUp(memberId: number, givenUp: GivenUp[]) {
        const registration = registrationOf(memberId)
        if (!registration) return
        const withdrawal = await events.withdrawRegistration(registration.id)
        givenUp.push({id: registration.id, undoUntil: withdrawal.undoUntil})
        await reload()
    }

    async function putBack(givenUp: GivenUp[]) {
        for (const place of givenUp) {
            await events.undoWithdrawal(place.id).catch(() => undefined)
        }
        await reload()
    }

    /**
     * Gives up the places of the members named, then offers them back together.
     *
     * @param memberIds the members of the household holding a place
     */
    async function withdrawAll(memberIds: number[]) {
        const givenUp: GivenUp[] = []
        for (const memberId of memberIds) await giveUp(memberId, givenUp)
        if (givenUp.length === 0) return
        const remaining = new Date(givenUp[0]!.undoUntil).getTime() - Date.now()
        if (remaining <= 0) return
        showToast(t('eventsUpcoming.signedOff'), 'info', remaining, {
            label: t('eventsUpcoming.undoSignOff'),
            run: () => putBack(givenUp),
        })
    }

    return {withdrawAll}
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {EventRegistrationFieldValue} from '@/api/generated/schema'
import {eventGroups, partnerEventGroups} from '@/api'

/**
 * Where an event group page sends its sign-ups: the station holding the event group, which is either this one or
 * a partner sharing it. The page and its dialogs read the same either way.
 */
export interface EventGroupSignUpPort {
    signUp(parts: number[], answers: EventRegistrationFieldValue[], memberId?: number): Promise<unknown>
    changeParts(parts: number[], memberId?: number): Promise<unknown>
    withdraw(memberId?: number): Promise<unknown>
}

/** Sign-ups for an event group of this station. */
export function ownEventGroupPort(eventGroupId: number): EventGroupSignUpPort {
    return {
        signUp: (parts, answers, memberId) => eventGroups.signUp(eventGroupId, parts, answers, memberId),
        changeParts: (parts, memberId) => eventGroups.changeParts(eventGroupId, parts, memberId),
        withdraw: memberId => eventGroups.withdraw(eventGroupId, memberId),
    }
}

/** Sign-ups for an event group a partner station shares. Partners ask no questions of visitors. */
export function partnerEventGroupPort(stationUid: string, eventGroupId: number): EventGroupSignUpPort {
    return {
        signUp: (parts, _answers, memberId) => partnerEventGroups.signUp(stationUid, eventGroupId, parts, memberId),
        changeParts: (parts, memberId) => partnerEventGroups.changeParts(stationUid, eventGroupId, parts, memberId),
        withdraw: memberId => partnerEventGroups.withdraw(stationUid, eventGroupId, memberId),
    }
}

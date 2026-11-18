/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {eventGroups, events} from '@/api'
import type {RegistrationStatus} from '@/api/generated/schema'

/**
 * Where the actions on a waiting list go: the sign-ups of an appointment date, or those of an event
 * group. Each kind of sign-up, a member's of the station or a partner's member's, has routes of its
 * own. The ids are whatever each list names its sign-ups by.
 */
export interface QueueRoutes {
    decide(id: number, status: RegistrationStatus, overLimit?: boolean): Promise<unknown>
    decidePartner(id: number, status: RegistrationStatus, overLimit?: boolean): Promise<unknown>
    move(id: number, position: number): Promise<unknown>
    movePartner(id: number, position: number): Promise<unknown>
    queue(ids: number[]): Promise<unknown>
    queuePartners(ids: number[]): Promise<unknown>
}

/** The waiting list of an appointment date, its sign-ups named by their registration. */
export const appointmentQueueRoutes: QueueRoutes = {
    decide: (id, status, overLimit) => events.updateRegistrationStatus(id, status, overLimit),
    decidePartner: (id, status, overLimit) => events.updateFederationRegistrationStatus(id, status, overLimit),
    move: (id, position) => events.moveRegistrationInQueue(id, position),
    movePartner: (id, position) => events.moveFederationRegistrationInQueue(id, position),
    queue: ids => events.moveRegistrationsToQueue(ids),
    queuePartners: ids => events.moveFederationRegistrationsToQueue(ids),
}

/**
 * The waiting list of an event group, the station's sign-ups named by their member and partners'
 * members' by their sign-up.
 *
 * @param eventGroupId the event group
 */
export function eventGroupQueueRoutes(eventGroupId: number): QueueRoutes {
    return {
        decide: (memberId, status, overLimit) => eventGroups.decide(eventGroupId, memberId, status, overLimit),
        decidePartner: (signUpId, status, overLimit) => eventGroups.decidePartner(eventGroupId, signUpId, status, overLimit),
        move: (memberId, position) => eventGroups.moveSignUpInQueue(eventGroupId, memberId, position),
        movePartner: (signUpId, position) => eventGroups.movePartnerSignUpInQueue(eventGroupId, signUpId, position),
        queue: memberIds => eventGroups.queueSignUps(eventGroupId, memberIds),
        queuePartners: signUpIds => eventGroups.queuePartnerSignUps(eventGroupId, signUpIds),
    }
}

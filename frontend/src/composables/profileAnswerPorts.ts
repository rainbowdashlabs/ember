/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {clusterMembers, managedMembers, profileFields} from '@/api'
import {parseFieldConfig, type FieldOriginName} from '@/api/profileFields'
import type {MemberProfileResponse} from '@/api/generated/schema'
import type {ProfileAnswersPort, ProfileAnswersSnapshot} from '@/composables/useProfileAnswers'

/** The origin an association's screen sends as text, read back as one of the two there are. */
function originOf(raw: string): FieldOriginName {
    return raw === 'CLUSTER' ? 'CLUSTER' : 'STATION'
}

/**
 * A member's questions and answers as the association's member screen reads them, for a screen that
 * fetched the profile itself because it shows more of it than the answers.
 *
 * @param profile the member's profile as the association reads it
 */
export function associationSnapshot(profile: MemberProfileResponse): ProfileAnswersSnapshot {
    return {
        fields: profile.fields.map(field => ({
            id: field.id,
            name: field.name,
            fieldType: field.fieldType,
            config: field.config ?? {},
            origin: originOf(field.origin),
        })),
        values: profile.values.map(value => ({
            fieldId: value.fieldId,
            value: value.value,
            origin: originOf(value.origin),
        })),
    }
}

/** A member's questions and answers as the station reads them, its association's included. */
async function stationSnapshot(memberId: number): Promise<ProfileAnswersSnapshot> {
    const [fields, values] = await Promise.all([
        profileFields.getMemberFields(memberId),
        profileFields.getValues(memberId),
    ])
    return {fields, values}
}

/**
 * Somebody who keeps the station's members: every answer but the ones the association keeps to itself,
 * the read-only ones included.
 */
export const stationManagerAnswers: ProfileAnswersPort = {
    load: stationSnapshot,
    async save(memberId, answers) {
        await profileFields.setValues(memberId, {values: answers})
    },
    canEdit: field => !field.readonlyAtStation,
}

/** The member on their own profile: what is neither read-only to them nor kept by the association. */
export const ownProfileAnswers: ProfileAnswersPort = {
    load: stationSnapshot,
    async save(memberId, answers) {
        await profileFields.setValues(memberId, {values: answers})
    },
    canEdit: field => !field.readonly && !field.readonlyAtStation,
}

/**
 * A guardian answering for a member in their care. They see the station's questions only, and write
 * neither one kept to the member management nor one that works itself out from another.
 */
export const guardianAnswers: ProfileAnswersPort = {
    async load(memberId) {
        const profile = await managedMembers.getProfile(memberId)
        return {
            fields: profile.fields.map(field => ({...field, origin: 'STATION'})),
            values: profile.values.map(value => ({...value, origin: 'STATION'})),
        }
    },
    async save(memberId, answers) {
        await managedMembers.setProfile(memberId, answers.map(({fieldId, value}) => ({fieldId, value})))
    },
    canEdit: field => !field.readonly && !parseFieldConfig(field.config).computed,
}

/**
 * The association, through somebody who manages its members. It writes every answer, the ones it keeps
 * from the station included, since it is the party that put that lock there; so the lock is not handed
 * to the layout, which would otherwise show those questions as closed.
 */
export const associationManagerAnswers: ProfileAnswersPort = {
    async load(memberId) {
        return associationSnapshot(await clusterMembers.getManagedMemberProfile(memberId))
    },
    async save(memberId, answers) {
        await clusterMembers.setManagedMemberProfile(memberId, answers)
    },
    canEdit: () => true,
}

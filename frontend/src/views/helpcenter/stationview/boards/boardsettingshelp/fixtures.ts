/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {FederationTargetResponse, PartnerResponse} from '@/api/generated/schema'
import {FieldTypes} from '@/api/fieldTypes'
import type {BoardFieldDraft} from '@/api/boards'
import {StationUserType, StationUserTypeLabels} from '@/api/types'
import type {LaneDraft} from '@/views/stationview/boards/boardsettingsview/BoardLanesSection.vue'
import type {RoleOption} from '@/views/stationview/boards/boardsettingsview/BoardFederationSection.vue'

export const demoGeneral = {
    name: 'Aufgaben Jugendfeuerwehr',
    description: 'Alle Aufgaben rund um die Jugendfeuerwehr',
    hideDoneAfterDays: 7,
    hasBacklog: true,
}

/** Fresh lane drafts, since the lane rows edit the drafts they are given in place. */
export function demoLaneDrafts(): LaneDraft[] {
    return [
        {id: 1, name: 'Zu erledigen', color: '#3b82f6'},
        {id: 2, name: 'In Arbeit', color: '#f59e0b'},
        {id: 3, name: 'Fertig', color: '#22c55e'},
    ]
}

/** Fresh field drafts, since the field rows edit the drafts they are given in place. */
export function demoFieldDrafts(): BoardFieldDraft[] {
    return [
        {name: 'Zeitaufwand (Stunden)', fieldType: FieldTypes.NUMBER, required: false, options: [], laneId: null},
        {name: 'Kategorie', fieldType: FieldTypes.CHOICE, required: true, options: ['Ausrüstung', 'Ausbildung', 'Organisation'], laneId: null},
    ]
}

/** Fresh sharing targets, since the target list edits the targets it is given in place. */
export function demoFederationTargets(): FederationTargetResponse[] {
    return [{partnerId: 1, shareMode: 'FULL', requiredUserType: StationUserType.MEMBER}]
}

const demoPartnerNames: ReadonlyMap<number, string> = new Map([[1, 'Feuerwehr Musterstadt'], [2, 'Jugendfeuerwehr Beispielhausen']])

/** The partner a board could still be shared with, which the add row offers. */
export const demoAvailablePartners: PartnerResponse[] = [{
    partnerStationName: demoPartnerNames.get(2)!,
    partner: {
        id: 2,
        clusterHome: false,
        clusterManaged: false,
        createdAt: '2026-01-15T10:00:00Z',
        federationContract: null,
        inviteCode: null,
        partnerPublicKey: null,
        partnerStationId: 'demo-beispielhausen',
        partnerStationName: demoPartnerNames.get(2)!,
        publicKey: null,
        remote: false,
        remoteHost: null,
        stationId: 'demo-station',
        status: 'ACTIVE',
        updatedAt: '2026-01-15T10:00:00Z',
    },
}]

export const demoRoleOptions: RoleOption[] = Object.values(StationUserType)
    .map(userType => ({value: userType, label: StationUserTypeLabels[userType]}))

/** The demo partner's station name. */
export function demoPartnerName(partnerId: number): string {
    return demoPartnerNames.get(partnerId) ?? ''
}

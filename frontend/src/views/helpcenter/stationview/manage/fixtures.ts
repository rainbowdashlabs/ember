/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {ProfileFieldAssignment, StationModule} from '@/api/generated/schema'
import {StationModules} from '@/api/types'
import {STATION_MODULE_OPTIONS} from '@/data/stationModules'
import {FieldTypes, type FieldTypeName} from '@/api/fieldTypes'
import type {EditableField, FieldSettings} from '@/api/profileFields'
import type {Audience} from '@/composables/useFieldsConfig'

/** Looks up a help text. */
type Translate = (key: string) => string

/** A question of the station's profile with the settings the table shows. */
function question(id: number, name: string, fieldType: FieldTypeName, required: boolean, readonly: boolean,
                  config: FieldSettings = {}): EditableField {
  return {id, name, fieldType, required, readonly, keepOnArchive: true, width: null, config}
}

/** Three questions of a station's profile: a phone number, a birth date and a clothing size. */
export const DEMO_FIELDS: EditableField[] = [
  question(1, 'Telefonnummer', FieldTypes.TEXT, true, false),
  question(2, 'Geburtsdatum', FieldTypes.BIRTH_DATE, true, true),
  question(3, 'Kleidergröße', FieldTypes.CHOICE, false, false, {options: ['S', 'M', 'L', 'XL']}),
]

/** How many audiences each of the questions is put to. */
export const DEMO_AUDIENCE_COUNT: Record<number, number> = {1: 2, 2: 2, 3: 1}

/** How the birth date is put to one kind of member. */
function assignment(id: number, role: 'MEMBER' | 'TEAM', readonlyOverride: boolean | null): ProfileFieldAssignment {
  return {
    id,
    fieldId: 2,
    role,
    groupId: null,
    targetKind: 'ROLE',
    position: 0,
    widthOverride: null,
    requiredOverride: null,
    readonlyOverride,
  }
}

/** The birth date put to members, who may not change it, and to the team, who may. */
export function demoAudiences(t: Translate): Audience[] {
  return [
    {target: {role: 'MEMBER', groupId: null}, assignment: assignment(1, 'MEMBER', true), label: t('membersConfig.roles.MEMBER')},
    {target: {role: 'TEAM', groupId: null}, assignment: assignment(2, 'TEAM', false), label: t('membersConfig.roles.TEAM')},
  ]
}

/** A day the given number of days from today, as an expiry date field stores it. */
function inDays(days: number): string {
  const day = new Date()
  day.setDate(day.getDate() + days)
  return `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, '0')}-${String(day.getDate()).padStart(2, '0')}`
}

/** Three expiry dates: one far off, one inside the warning window and one already passed. */
export function demoExpiryDates(): string[] {
  return [inDays(400), inDays(20), inDays(-12)]
}

const ENABLED_MODULES: ReadonlySet<StationModule> = new Set<StationModule>([
  StationModules.INVENTORY,
  StationModules.NEWS,
  StationModules.EVENTS,
  StationModules.ATTENDANCE,
  StationModules.FORMS,
  StationModules.QUIZ,
  StationModules.KNOWLEDGE_BASE,
  StationModules.DOCUMENTS,
])

/** The modules a typical station has switched off: everything but the everyday ones above. */
export function demoDisabledModules(): ReadonlySet<StationModule> {
  return new Set(STATION_MODULE_OPTIONS.map(option => option.value).filter(module => !ENABLED_MODULES.has(module)))
}

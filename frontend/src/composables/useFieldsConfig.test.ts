/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {mount} from '@vue/test-utils'
import {defineComponent} from 'vue'
import {describe, expect, it} from 'vitest'
import {FieldTypes, type ProfileField} from '@/api/profileFields'
import type {ProfileFieldAssignment} from '@/util/profileFields'
import {STATION_ROLES, useFieldsConfig, type FieldsPort} from './useFieldsConfig'

function field(id: number, name: string, fieldType: string = FieldTypes.BIRTH_DATE): ProfileField {
  return {id, stationId: '1', name, fieldType, config: {}, required: false, width: null}
}

function askedOf(id: number, fieldId: number, role: string, position = 0): ProfileFieldAssignment {
  return {id, fieldId, targetKind: 'ROLE', role: role as never, position}
}

function portOf(fields: ProfileField[], assignments: ProfileFieldAssignment[]): FieldsPort {
  return {
    list: async () => fields,
    listAssignments: async () => assignments,
    create: async () => field(0, 'Neu'),
    update: async () => undefined,
    remove: async () => undefined,
    assign: async () => undefined,
    unassign: async () => undefined,
    reorder: async () => undefined,
    roles: STATION_ROLES,
    types: Object.values(FieldTypes),
    stationReadonly: false,
  }
}

/** The composable reaches for the locale, so it is used from inside a component as the app does. */
function configFor(fields: ProfileField[], assignments: ProfileFieldAssignment[] = []) {
  let api: ReturnType<typeof useFieldsConfig> | null = null
  mount(defineComponent({
    setup() {
      api = useFieldsConfig(portOf(fields, assignments))
      return () => null
    },
  }))
  return api as unknown as ReturnType<typeof useFieldsConfig>
}

describe('useFieldsConfig', () => {
  /**
   * A station has one date of birth, whoever is asked it.
   *
   * <p>There used to be one per kind of member, because a question belonged to the audience it was
   * written for. A manager, who is asked the team's questions as well as their own, then met the
   * same question twice and the two copies collected different answers.
   */
  it('finds the one date of birth of the station, whoever is asked it', async () => {
    const config = configFor(
        [field(1, 'Geburtsdatum')],
        [askedOf(10, 1, 'MEMBER'), askedOf(11, 1, 'TEAM')])
    await config.reload()

    expect(config.questions.value).toHaveLength(1)
    expect(config.birthDateField.value?.id).toBe(1)
  })

  /** Selecting a question shows who it is put to, and offers the kinds it is not put to yet. */
  it('names the audiences of the selected question and what is left to add', async () => {
    const config = configFor(
        [field(1, 'Geburtsdatum'), field(2, 'Telefon', FieldTypes.TEXT)],
        [askedOf(10, 1, 'MEMBER'), askedOf(11, 1, 'TEAM'), askedOf(12, 2, 'MANAGER')])
    await config.reload()

    config.select(1)
    expect(config.audiences.value.map(a => a.assignment.role)).toEqual(['MEMBER', 'TEAM'])
    expect(config.unaskedRoles.value).toEqual(['TRIAL', 'GUARDIAN', 'MANAGER'])
  })

  /**
   * A question put to nobody is written down and reaches no profile, which looks exactly like a
   * question that works until somebody goes looking for it.
   */
  it('points out a question nobody is asked', async () => {
    const config = configFor(
        [field(1, 'Geburtsdatum'), field(2, 'Telefon', FieldTypes.TEXT)],
        [askedOf(10, 1, 'MEMBER')])
    await config.reload()

    expect(config.askedOfNobody.value.map(f => f.id)).toEqual([2])
  })

  /** One form holds what that audience is asked, in the order the assignment gives. */
  it('builds one audience\'s form out of the assignments', async () => {
    const config = configFor(
        [field(1, 'Geburtsdatum'), field(2, 'Telefon', FieldTypes.TEXT)],
        [askedOf(10, 1, 'MEMBER', 1), askedOf(11, 2, 'MEMBER', 0), askedOf(12, 1, 'TEAM', 0)])
    await config.reload()

    config.previewRole.value = 'MEMBER'
    expect(config.previewFields.value.map(f => f.id)).toEqual([2, 1])

    config.previewRole.value = 'TEAM'
    expect(config.previewFields.value.map(f => f.id)).toEqual([1])
  })
})

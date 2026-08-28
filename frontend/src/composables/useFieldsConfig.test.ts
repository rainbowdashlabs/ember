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
import {useFieldsConfig, type FieldsPort} from './useFieldsConfig'

function field(id: number, scope: string, fieldType: string = FieldTypes.BIRTH_DATE): ProfileField {
  return {id, stationId: '1', name: 'Geburtsdatum', fieldType, config: {}, position: 0, scope}
}

function portOf(fields: ProfileField[]): FieldsPort {
  return {
    list: async () => fields,
    create: async () => undefined,
    update: async () => undefined,
    remove: async () => undefined,
    reorder: async () => undefined,
    scopes: ['MEMBER', 'GUARDIAN', 'TEAM', 'MANAGER', 'GROUP'],
    types: Object.values(FieldTypes),
    stationReadonly: false,
  }
}

/** The composable reaches for the locale, so it is used from inside a component as the app does. */
function configFor(fields: ProfileField[]) {
  let api: ReturnType<typeof useFieldsConfig> | null = null
  mount(defineComponent({
    setup() {
      api = useFieldsConfig(portOf(fields))
      return () => null
    },
  }))
  return api as unknown as ReturnType<typeof useFieldsConfig>
}

describe('useFieldsConfig', () => {
  /**
   * The server allows one date of birth per kind of member, because nobody is two kinds at once.
   * This screen took the first one anywhere, so a station that asked its members for a birth date
   * was told its team could not have one, and opening the team's own offered every type but the
   * one it already had, which left the type blank.
   */
  it('reads the date of birth of the kind of member the tab is about', async () => {
    const config = configFor([field(1, 'MEMBER'), field(2, 'TEAM')])
    await config.reload()

    expect(config.birthDateField.value?.id, 'the members tab sees the members one').toBe(1)

    config.activeTab.value = 'TEAM'
    expect(config.birthDateField.value?.id, 'and the team tab sees the team one').toBe(2)

    config.activeTab.value = 'GUARDIAN'
    expect(config.birthDateField.value, 'a kind that has none is free to add one').toBeNull()
  })

  /**
   * A member belongs to any number of groups and to a kind besides, so a date asked of a group
   * meets people who are already asked elsewhere. One of those blocks every other.
   */
  it('treats a date of birth asked of a group as colliding with every kind', async () => {
    const config = configFor([field(1, 'GROUP')])
    await config.reload()

    config.activeTab.value = 'MEMBER'
    expect(config.birthDateField.value?.id).toBe(1)

    config.activeTab.value = 'TEAM'
    expect(config.birthDateField.value?.id).toBe(1)
  })
})

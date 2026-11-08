/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {defineComponent} from 'vue'
import {describe, expect, it, vi} from 'vitest'
import type {EditableField, EditableFieldRequest} from '@/api/profileFields'
import {OfferedFieldTypes} from '@/api/fieldTypes'
import {FieldType, type ProfileFieldAssignment, type ProfileFieldScope} from '@/api/generated/schema'
import {STATION_ROLES, useFieldsConfig, type FieldsPort} from './useFieldsConfig'

function field(id: number, name: string, fieldType: FieldType = FieldType.BIRTH_DATE): EditableField {
  return {id, name, fieldType, config: {}, required: false, readonly: false, keepOnArchive: false, width: null, shownIfFieldId: null, fieldSetId: null, setPosition: 0}
}

function askedOf(id: number, fieldId: number, role: ProfileFieldScope, position = 0): ProfileFieldAssignment {
  return {
    id, fieldId, targetKind: 'ROLE', role, groupId: null, position,
    widthOverride: null, readonlyOverride: null, requiredOverride: null,
  }
}

function portOf(
    fields: EditableField[],
    assignments: ProfileFieldAssignment[],
    created: EditableFieldRequest[] = [],
): FieldsPort {
  return {
    list: async () => fields,
    listAssignments: async () => assignments,
    create: async (request) => {
      created.push(request)
      return field(0, 'Neu')
    },
    update: async () => undefined,
    remove: async () => undefined,
    assign: async () => undefined,
    unassign: async () => undefined,
    reorder: async () => undefined,
    listSets: async () => [],
    createSet: async (data) => ({id: 1, ownerId: 1, name: data.name ?? '', nameAsSection: !!data.nameAsSection}),
    updateSet: async () => undefined,
    dissolveSet: async () => undefined,
    roles: STATION_ROLES,
    types: OfferedFieldTypes.PROFILE,
    stationReadonly: false,
  }
}

/** The composable reaches for the locale, so it is used from inside a component as the app does. */
function configFor(
    fields: EditableField[],
    assignments: ProfileFieldAssignment[] = [],
    created: EditableFieldRequest[] = [],
) {
  let api: ReturnType<typeof useFieldsConfig> | null = null
  mount(defineComponent({
    setup() {
      api = useFieldsConfig(portOf(fields, assignments, created))
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
        [field(1, 'Geburtsdatum'), field(2, 'Telefon', FieldType.TEXT)],
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
        [field(1, 'Geburtsdatum'), field(2, 'Telefon', FieldType.TEXT)],
        [askedOf(10, 1, 'MEMBER')])
    await config.reload()

    expect(config.askedOfNobody.value.map(f => f.id)).toEqual([2])
  })

  /** One form holds what that audience is asked, in the order the assignment gives. */
  it('builds one audience\'s form out of the assignments', async () => {
    const config = configFor(
        [field(1, 'Geburtsdatum'), field(2, 'Telefon', FieldType.TEXT)],
        [askedOf(10, 1, 'MEMBER', 1), askedOf(11, 2, 'MEMBER', 0), askedOf(12, 1, 'TEAM', 0)])
    await config.reload()

    config.previewRole.value = 'MEMBER'
    expect(config.previewFields.value.map(f => f.id)).toEqual([2, 1])

    config.previewRole.value = 'TEAM'
    expect(config.previewFields.value.map(f => f.id)).toEqual([1])
  })

  /**
   * A template's expected and locked answers travel on the field, not in its settings, which the
   * server reads strictly and would refuse.
   */
  it('sends a template\'s required and readonly on the field itself', async () => {
    const created: EditableFieldRequest[] = []
    const config = configFor([], [], created)
    const birthDate = {
      name: 'Geburtsdatum',
      fields: [{name: 'Geburtsdatum', fieldType: FieldType.BIRTH_DATE, config: {}, required: true, readonly: true}],
    }

    await config.applyTemplate(birthDate)

    expect(created).toHaveLength(1)
    expect(created[0]).toMatchObject({required: true, readonly: true, config: {}})
  })

  /**
   * A template only writes its questions; which audiences they are asked of is decided per question.
   * It used to put them to whichever form was shown, which on opening was the trial members'.
   */
  it('writes a template\'s questions without putting them to an audience', async () => {
    const created: EditableFieldRequest[] = []
    const assign = vi.fn(async () => undefined)
    let api: ReturnType<typeof useFieldsConfig> | null = null
    mount(defineComponent({
      setup() {
        api = useFieldsConfig({...portOf([], [], created), assign})
        return () => null
      },
    }))
    const firstAid = {
      name: 'Erste Hilfe Kurs',
      fields: [
        {name: 'Erste Hilfe Kurs', fieldType: FieldType.DATE, config: {}},
        {name: 'Erste Hilfe gültig bis', fieldType: FieldType.EXPIRY_DATE, config: {}},
      ],
    }

    await api!.applyTemplate(firstAid)

    expect(created.map(request => request.name)).toEqual(['Erste Hilfe Kurs', 'Erste Hilfe gültig bis'])
    expect(assign).not.toHaveBeenCalled()
  })

  /** A template of several questions bundles them into a group named after it, its name as the heading. */
  it('bundles a template of several questions into a group', async () => {
    const created: EditableFieldRequest[] = []
    const createSet = vi.fn(async () => ({id: 7, ownerId: 1, name: 'Adresse', nameAsSection: true}))
    let api: ReturnType<typeof useFieldsConfig> | null = null
    mount(defineComponent({
      setup() {
        api = useFieldsConfig({...portOf([], [], created), createSet})
        return () => null
      },
    }))

    await api!.applyTemplate({
      name: 'Adresse',
      fields: [
        {name: 'Straße', fieldType: FieldType.TEXT, config: {}},
        {name: 'Ort', fieldType: FieldType.TEXT, config: {}},
      ],
    })

    expect(createSet).toHaveBeenCalledWith({name: 'Adresse', nameAsSection: true})
    expect(created.map(request => request.fieldSetId)).toEqual([7, 7])
  })

  /**
   * A field of a group is never put to anybody on its own: ticking it ticks its whole group, and putting
   * the ticked fields to an audience asks once for the group.
   */
  it('ticks and assigns a group as one', async () => {
    const street = {...field(1, 'Straße', FieldType.TEXT), fieldSetId: 7}
    const city = {...field(2, 'Ort', FieldType.TEXT), fieldSetId: 7}
    const assign = vi.fn(async () => undefined)
    let api: ReturnType<typeof useFieldsConfig> | null = null
    mount(defineComponent({
      setup() {
        api = useFieldsConfig({...portOf([street, city, field(3, 'Allein', FieldType.TEXT)], [], []), assign})
        return () => null
      },
    }))
    await vi.waitFor(() => expect(api!.allFields.value).toHaveLength(3))

    api!.toggleChecked(2)
    expect([...api!.checkedIds.value].sort()).toEqual([1, 2])

    await api!.assignCheckedTo({role: 'MEMBER', groupId: null})
    expect(assign).toHaveBeenCalledTimes(1)

    api!.toggleChecked(1)
    api!.toggleChecked(1)
    expect(api!.checkedIds.value.size).toBe(0)
  })

  /** A template naming a field the owner already has writes nothing, not even its group. */
  it('writes nothing of a template whose field names are taken', async () => {
    const created: EditableFieldRequest[] = []
    const createSet = vi.fn(async () => ({id: 7, ownerId: 1, name: 'Adresse', nameAsSection: true}))
    let api: ReturnType<typeof useFieldsConfig> | null = null
    mount(defineComponent({
      setup() {
        api = useFieldsConfig({...portOf([field(3, 'Straße', FieldType.TEXT)], [], created), createSet})
        return () => null
      },
    }))
    await vi.waitFor(() => expect(api!.allFields.value).toHaveLength(1))

    await api!.applyTemplate({
      name: 'Adresse',
      fields: [
        {name: 'Straße', fieldType: FieldType.TEXT, config: {}},
        {name: 'Ort', fieldType: FieldType.TEXT, config: {}},
      ],
    })

    expect(createSet).not.toHaveBeenCalled()
    expect(created).toHaveLength(0)
    expect(api!.failure.value?.message).toContain('Straße')
  })

  /** Where the first field is refused anyway, the group made for the template is dissolved again. */
  it('dissolves the group again when no field of the template could be written', async () => {
    const dissolveSet = vi.fn(async () => undefined)
    const createSet = vi.fn(async () => ({id: 7, ownerId: 1, name: 'Adresse', nameAsSection: true}))
    const create = vi.fn(async () => {
      throw new Error('refused')
    })
    let api: ReturnType<typeof useFieldsConfig> | null = null
    mount(defineComponent({
      setup() {
        api = useFieldsConfig({...portOf([], [], []), createSet, create, dissolveSet})
        return () => null
      },
    }))

    await api!.applyTemplate({
      name: 'Adresse',
      fields: [
        {name: 'Straße', fieldType: FieldType.TEXT, config: {}},
        {name: 'Ort', fieldType: FieldType.TEXT, config: {}},
      ],
    })

    expect(dissolveSet).toHaveBeenCalledWith(7)
  })
})

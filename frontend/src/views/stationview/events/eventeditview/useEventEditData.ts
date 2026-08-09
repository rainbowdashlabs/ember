/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref} from 'vue'
import {attendance, events, memberGroups} from '@/api'
import type {AttendanceTemplateField} from '@/api/attendance'
import type {EventTemplate} from '@/api/events'
import type {StationMember} from '@/api/types'
import {useEventEditDeps} from '@/composables/useEventEditDeps'

/**
 * Reference data the event editor offers for selection: categories, event and
 * attendance templates, audiences and the members behind each group.
 */
export function useEventEditData(selectedTemplateId: () => string) {
  const {categories, templates, groups, tags, members: allMembers, reload: reloadDeps} = useEventEditDeps({withMembers: true, autoLoad: false})
  const eventTemplates = ref<EventTemplate[]>([])
  const allTemplateFields = ref<AttendanceTemplateField[]>([])
  const groupMembersMap = ref(new Map<number, StationMember[]>())

  const currentTemplateFields = computed(() => {
    const id = selectedTemplateId()
    if (!id) return []
    return allTemplateFields.value.filter(f => f.templateId === Number(id))
  })

  async function load() {
    const [, evtTpls] = await Promise.all([
      reloadDeps(),
      events.listTemplates(),
    ])
    eventTemplates.value = evtTpls

    const gMap = new Map<number, StationMember[]>()
    for (const g of groups.value) {
      const gMembers = await memberGroups.getGroupMembers(g.id)
      gMap.set(g.id, gMembers)
    }
    groupMembersMap.value = gMap

    const fieldResults = await Promise.all(templates.value.map(t => attendance.listTemplateFields(t.id)))
    allTemplateFields.value = fieldResults.flat()
  }

  const props = computed(() => ({
    eventTemplates: eventTemplates.value,
    categories: categories.value,
    templates: templates.value,
    attendanceFields: allTemplateFields.value,
    groups: groups.value,
    tags: tags.value,
    allMembers: allMembers.value,
    groupMembers: groupMembersMap.value,
    currentTemplateFields: currentTemplateFields.value,
  }))

  return {props, load}
}

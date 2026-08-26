/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { procedures, stationMembers } from '@/api'
import type { ProcedureTemplate, TemplateDetail } from '@/api/procedures'
import type { MemberCompletion } from '@/api/stationMembers'
import type { EditableItem } from '@/views/stationview/procedure/procedurecreateview/types'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { moveWithin } from '@/util/reorder'

/**
 * The editable form behind creating and editing a procedure, including its checklist items and
 * the dependencies between them.
 *
 * Items are identified by a client-side {@code tempId} while the form is open, because an item
 * added here has no server id until the procedure is saved, yet it can already be named as
 * another item's dependency. Saving maps every temp id onto the real one and only then writes
 * the dependency pairs.
 *
 * @param editId           the procedure being edited, or {@code null} when creating a new one
 * @param presetTemplateId a template to start a new procedure from, or {@code null} for a blank one
 */
export function useProcedureForm(editId: Ref<number | null>, presetTemplateId: Ref<number | null>) {
  const { t } = useI18n()

  const name = ref('')
  const description = ref('')
  const dueAt = ref('')
  const isPublic = ref(true)

  const templates = ref<ProcedureTemplate[]>([])
  const selectedTemplateId = ref<number | null>(null)
  const templateDetail = ref<TemplateDetail | null>(null)

  const members = ref<MemberCompletion[]>([])
  const selectedAssigneeIds = ref<number[]>([])

  const items = ref<EditableItem[]>([])
  const existingItemIds = ref<Set<number>>(new Set())
  let nextTempId = 1

  const isEditMode = computed(() => editId.value != null)

  const selectedAssignees = computed(() =>
    selectedAssigneeIds.value
      .map(id => members.value.find(m => m.id === id))
      .filter(Boolean) as MemberCompletion[],
  )

  /**
   * Turns a stored item list plus its dependency pairs into editable items. The pairs reference
   * server ids, so they are translated through the temp id each item is given here.
   */
  function toEditableItems(
    source: {id: number; title: string; description?: string | null; isPublic: boolean; userAssigned: boolean; position: number}[],
    dependencies: [number, number][],
    keepIds: boolean,
  ): EditableItem[] {
    const realToTemp = new Map<number, number>()
    const editable: EditableItem[] = source.map(item => {
      const tempId = nextTempId++
      realToTemp.set(item.id, tempId)
      return {
        ...(keepIds ? {id: item.id} : {}),
        tempId,
        title: item.title,
        description: item.description ?? '',
        isPublic: item.isPublic,
        userAssigned: item.userAssigned,
        position: item.position,
        dependsOn: [],
      }
    })
    for (const [itemId, dependsOnId] of dependencies) {
      const itemTempId = realToTemp.get(itemId)
      const depTempId = realToTemp.get(dependsOnId)
      if (itemTempId == null || depTempId == null) continue
      editable.find(i => i.tempId === itemTempId)?.dependsOn.push(depTempId)
    }
    return editable
  }

  async function loadTemplate(id: number) {
    try {
      const detail = await procedures.getTemplate(id)
      templateDetail.value = detail
      name.value = detail.template.name
      description.value = detail.template.description ?? ''
      items.value = toEditableItems(detail.items, detail.dependencies, false)
    } catch {
      error.value = t('common.error')
    }
  }

  async function handleTemplateChange(idStr: string | undefined) {
    if (!idStr) {
      selectedTemplateId.value = null
      templateDetail.value = null
      items.value = []
      name.value = ''
      description.value = ''
      return
    }
    selectedTemplateId.value = Number(idStr)
    await loadTemplate(Number(idStr))
  }

  const {loading, error, reload} = useAsyncLoader(async () => {
    const [tpls, mbrs] = await Promise.all([
      procedures.getTemplates(),
      stationMembers.listCompletions(),
    ])
    templates.value = tpls.filter(tpl => !tpl.archived)
    members.value = mbrs

    if (isEditMode.value) {
      const detail = await procedures.getProcedure(editId.value!)
      name.value = detail.procedure.name
      description.value = detail.procedure.description ?? ''
      dueAt.value = detail.procedure.dueAt ?? ''
      isPublic.value = detail.procedure.isPublic
      selectedAssigneeIds.value = [...detail.assigneeIds]
      existingItemIds.value = new Set(detail.items.map(i => i.id))
      items.value = toEditableItems(detail.items, detail.dependencies, true)
      return
    }
    if (presetTemplateId.value != null) {
      selectedTemplateId.value = presetTemplateId.value
      await loadTemplate(presetTemplateId.value)
    }
  }, {autoLoad: false})

  function addAssignee(id: number) {
    if (!id || selectedAssigneeIds.value.includes(id)) return
    selectedAssigneeIds.value = [...selectedAssigneeIds.value, id]
  }

  function removeAssignee(id: number) {
    selectedAssigneeIds.value = selectedAssigneeIds.value.filter(a => a !== id)
  }

  /**
   * Appends a step, empty unless something is handed in. An empty one is a row to write in rather
   * than a step: the list edits every field in place, so asking for the title up front only defers
   * what the row does anyway. Steps still without a title when the form is saved are dropped.
   */
  function addItem(title = '', itemDescription = '') {
    items.value = [...items.value, {
      tempId: nextTempId++,
      title: title.trim(),
      description: itemDescription,
      isPublic: true,
      userAssigned: false,
      position: items.value.length,
      dependsOn: [],
    }]
  }

  /**
   * Removes an item and any dependency on it, so no item is left waiting on something that is
   * no longer in the list.
   */
  function removeItem(index: number) {
    const removed = items.value[index]
    if (!removed) return
    items.value = items.value
      .filter((_, i) => i !== index)
      .map(item => ({...item, dependsOn: item.dependsOn.filter(d => d !== removed.tempId)}))
  }

  function reorderItems(fromIndex: number, toIndex: number) {
    items.value = moveWithin(items.value, fromIndex, toIndex)
  }

  function itemPayload(item: EditableItem, position: number) {
    return {
      title: item.title,
      description: item.description || undefined,
      isPublic: item.isPublic,
      userAssigned: item.userAssigned,
      position,
    }
  }

  function buildDependencies(tempToReal: Map<number, number>) {
    const deps: { itemId: number; dependsOnItemId: number }[] = []
    for (const item of items.value) {
      const realId = tempToReal.get(item.tempId)
      if (!realId) continue
      for (const depTempId of item.dependsOn) {
        const realDepId = tempToReal.get(depTempId)
        if (realDepId) deps.push({itemId: realId, dependsOnItemId: realDepId})
      }
    }
    return deps
  }

  async function syncAssignees(pid: number) {
    const current = (await procedures.getProcedure(pid)).assigneeIds
    const toAdd = selectedAssigneeIds.value.filter(id => !current.includes(id))
    const toRemove = current.filter(id => !selectedAssigneeIds.value.includes(id))
    if (toAdd.length) await procedures.addAssignees(pid, toAdd)
    for (const id of toRemove) await procedures.removeAssignee(pid, id)
  }

  async function syncItems(pid: number): Promise<Map<number, number>> {
    // A row nobody wrote a title into never became a step, so it is not saved as one.
    items.value = items.value.filter(item => item.title.trim())
    const keptIds = new Set(items.value.filter(i => i.id).map(i => i.id!))
    for (const oldId of existingItemIds.value) {
      if (!keptIds.has(oldId)) await procedures.deleteItem(pid, oldId)
    }
    const tempToReal = new Map<number, number>()
    for (const [i, item] of items.value.entries()) {
      if (item.id) {
        await procedures.editItem(pid, item.id, itemPayload(item, i))
        tempToReal.set(item.tempId, item.id)
      } else {
        const created = await procedures.addItem(pid, itemPayload(item, i))
        tempToReal.set(item.tempId, created.id)
      }
    }
    return tempToReal
  }

  /**
   * Persists the form and returns the id of the procedure to navigate to.
   */
  async function submit(): Promise<number> {
    if (isEditMode.value) {
      const pid = editId.value!
      await procedures.updateProcedure(pid, {
        name: name.value.trim(),
        description: description.value || undefined,
        dueAt: dueAt.value || null,
        isPublic: isPublic.value,
      })
      await syncAssignees(pid)
      await procedures.setProcedureDependencies(pid, buildDependencies(await syncItems(pid)))
      return pid
    }

    const created = await procedures.createProcedure({
      name: name.value.trim(),
      description: description.value || undefined,
      dueAt: dueAt.value || undefined,
      isPublic: isPublic.value,
      assigneeIds: selectedAssigneeIds.value,
    })
    const tempToReal = new Map<number, number>()
    for (const [i, item] of items.value.entries()) {
      const createdItem = await procedures.addItem(created.id, itemPayload(item, i))
      tempToReal.set(item.tempId, createdItem.id)
    }
    const deps = buildDependencies(tempToReal)
    if (deps.length) await procedures.setProcedureDependencies(created.id, deps)
    return created.id
  }

  return {
    name,
    description,
    dueAt,
    isPublic,
    templates,
    selectedTemplateId,
    templateDetail,
    members,
    selectedAssigneeIds,
    selectedAssignees,
    items,
    isEditMode,
    loading,
    error,
    reload,
    handleTemplateChange,
    addAssignee,
    removeAssignee,
    addItem,
    removeItem,
    reorderItems,
    submit,
  }
}

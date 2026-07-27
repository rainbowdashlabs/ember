/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, computed, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import TemplateSelectorSection from '@/views/stationview/procedure/procedurecreateview/TemplateSelectorSection.vue'
import BasicInfoSection from '@/views/stationview/procedure/procedurecreateview/BasicInfoSection.vue'
import AssigneesSection from '@/views/stationview/procedure/procedurecreateview/AssigneesSection.vue'
import ItemsSection from '@/views/stationview/procedure/procedurecreateview/ItemsSection.vue'
import type {EditableItem} from '@/views/stationview/procedure/procedurecreateview/types'
import {useSession} from '@/composables/useSession'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {procedures, stationMembers} from '@/api'
import type {ProcedureTemplate, TemplateDetail} from '@/api/procedures'
import type {MemberCompletion} from '@/api/stationMembers'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {loaded} = useSession()

const editId = computed(() => {
  const id = route.params.id
  return id ? Number(id) : null
})
const isEditMode = computed(() => editId.value != null)

const name = ref('')
const description = ref('')
const dueAt = ref('')
const isPublic = ref(true)

const templates = ref<ProcedureTemplate[]>([])
const selectedTemplateId = ref<number | null>(null)
const templateDetail = ref<TemplateDetail | null>(null)

const existingItemIds = ref<Set<number>>(new Set())

let nextTempId = 1
const items = ref<EditableItem[]>([])

const members = ref<MemberCompletion[]>([])
const selectedAssigneeIds = ref<number[]>([])
const assigneePickerValue = ref('')

const showAddItemModal = ref(false)
const newItemTitle = ref('')
const newItemDescription = ref('')

const selectedAssignees = computed(() =>
    selectedAssigneeIds.value
        .map(id => members.value.find(m => m.id === id))
        .filter(Boolean) as MemberCompletion[]
)

const {loading, error, reload} = useAsyncLoader(async () => {
  const [tpls, mbrs] = await Promise.all([
    procedures.getTemplates(),
    stationMembers.listCompletions(),
  ])
  templates.value = tpls.filter(t => !t.archived)
  members.value = mbrs

  if (isEditMode.value) {
    const detail = await procedures.getProcedure(editId.value!)
    name.value = detail.procedure.name
    description.value = detail.procedure.description ?? ''
    dueAt.value = detail.procedure.dueAt ?? ''
    isPublic.value = detail.procedure.isPublic
    selectedAssigneeIds.value = [...detail.assigneeIds]
    existingItemIds.value = new Set(detail.items.map(i => i.id))
    const realToTemp = new Map<number, number>()
    const loadedItems: EditableItem[] = detail.items.map(item => {
      const tid = nextTempId++
      realToTemp.set(item.id, tid)
      return {
        id: item.id,
        tempId: tid,
        title: item.title,
        description: item.description ?? '',
        isPublic: item.isPublic,
        userAssigned: item.userAssigned,
        position: item.position,
        dependsOn: [],
      }
    })
    for (const dep of detail.dependencies) {
      const itemTempId = realToTemp.get(dep[0])
      const depTempId = realToTemp.get(dep[1])
      if (itemTempId != null && depTempId != null) {
        const item = loadedItems.find(i => i.tempId === itemTempId)
        if (item) item.dependsOn.push(depTempId)
      }
    }
    items.value = loadedItems
  } else {
    const tplParam = route.query.template
    if (tplParam) {
      selectedTemplateId.value = Number(tplParam)
      await loadTemplate(Number(tplParam))
    }
  }
}, {autoLoad: false})

async function loadTemplate(id: number) {
  try {
    templateDetail.value = await procedures.getTemplate(id)
    name.value = templateDetail.value.template.name
    description.value = templateDetail.value.template.description ?? ''
    const tplToTemp = new Map<number, number>()
    const tplItems: EditableItem[] = templateDetail.value.items.map(item => {
      const tid = nextTempId++
      tplToTemp.set(item.id, tid)
      return {
        tempId: tid,
        title: item.title,
        description: item.description ?? '',
        isPublic: item.isPublic,
        userAssigned: item.userAssigned,
        position: item.position,
        dependsOn: [],
      }
    })
    for (const dep of templateDetail.value.dependencies) {
      const itemTempId = tplToTemp.get(dep[0])
      const depTempId = tplToTemp.get(dep[1])
      if (itemTempId != null && depTempId != null) {
        const item = tplItems.find(i => i.tempId === itemTempId)
        if (item) item.dependsOn.push(depTempId)
      }
    }
    items.value = tplItems
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

function addAssignee() {
  const id = Number(assigneePickerValue.value)
  if (!id || selectedAssigneeIds.value.includes(id)) return
  selectedAssigneeIds.value = [...selectedAssigneeIds.value, id]
  assigneePickerValue.value = ''
}

function removeAssignee(id: number) {
  selectedAssigneeIds.value = selectedAssigneeIds.value.filter(a => a !== id)
}

function addItem() {
  if (!newItemTitle.value.trim()) return
  items.value = [...items.value, {
    tempId: nextTempId++,
    title: newItemTitle.value.trim(),
    description: newItemDescription.value,
    isPublic: true,
    userAssigned: false,
    position: items.value.length,
    dependsOn: [],
  }]
  newItemTitle.value = ''
  newItemDescription.value = ''
  showAddItemModal.value = false
}

function removeItem(index: number) {
  const removedTempId = items.value[index].tempId
  items.value = items.value
      .filter((_, i) => i !== index)
      .map(item => ({...item, dependsOn: item.dependsOn.filter(d => d !== removedTempId)}))
}

function moveItem(index: number, direction: -1 | 1) {
  const target = index + direction
  if (target < 0 || target >= items.value.length) return
  const arr = [...items.value]
  ;[arr[index], arr[target]] = [arr[target], arr[index]]
  items.value = arr
}

function buildDependencies(tempToReal: Map<number, number>): { itemId: number; dependsOnItemId: number }[] {
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

const {running: saving, error: saveError, run: runSubmit} = useAsyncAction(
    async () => {
      if (isEditMode.value) {
        await submitEdit(editId.value!)
      } else {
        await submitCreate()
      }
    },
    {formatError: () => t('common.error')},
)

function handleSubmit() {
  if (!name.value.trim()) return
  error.value = ''
  return runSubmit()
}

async function submitEdit(pid: number) {
  await procedures.updateProcedure(pid, {
    name: name.value.trim(),
    description: description.value || undefined,
    dueAt: dueAt.value || null,
    isPublic: isPublic.value,
  })
  await syncAssignees(pid)
  const tempToReal = await syncItems(pid)
  const deps = buildDependencies(tempToReal)
  await procedures.setProcedureDependencies(pid, deps)
  router.push({name: 'procedure-detail', params: {id: pid}})
}

async function submitCreate() {
  const created = await procedures.createProcedure({
    name: name.value.trim(),
    description: description.value || undefined,
    dueAt: dueAt.value || undefined,
    isPublic: isPublic.value,
    assigneeIds: selectedAssigneeIds.value,
  })
  const tempToReal = new Map<number, number>()
  for (let i = 0; i < items.value.length; i++) {
    const item = items.value[i]
    const createdItem = await procedures.addItem(created.id, {
      title: item.title, description: item.description || undefined,
      isPublic: item.isPublic, userAssigned: item.userAssigned, position: i,
    })
    tempToReal.set(item.tempId, createdItem.id)
  }
  const deps = buildDependencies(tempToReal)
  if (deps.length) await procedures.setProcedureDependencies(created.id, deps)
  router.push({name: 'procedure-detail', params: {id: created.id}})
}

async function syncAssignees(pid: number) {
  const currentAssignees = (await procedures.getProcedure(pid)).assigneeIds
  const toAdd = selectedAssigneeIds.value.filter(id => !currentAssignees.includes(id))
  const toRemove = currentAssignees.filter(id => !selectedAssigneeIds.value.includes(id))
  if (toAdd.length) await procedures.addAssignees(pid, toAdd)
  for (const id of toRemove) await procedures.removeAssignee(pid, id)
}

async function syncItems(pid: number): Promise<Map<number, number>> {
  const currentItemIds = new Set(items.value.filter(i => i.id).map(i => i.id!))
  for (const oldId of existingItemIds.value) {
    if (!currentItemIds.has(oldId)) await procedures.deleteItem(pid, oldId)
  }
  const tempToReal = new Map<number, number>()
  for (let i = 0; i < items.value.length; i++) {
    const item = items.value[i]
    if (item.id) {
      await procedures.editItem(pid, item.id, {
        title: item.title, description: item.description || undefined,
        isPublic: item.isPublic, userAssigned: item.userAssigned, position: i,
      })
      tempToReal.set(item.tempId, item.id)
    } else {
      const created = await procedures.addItem(pid, {
        title: item.title, description: item.description || undefined,
        isPublic: item.isPublic, userAssigned: item.userAssigned, position: i,
      })
      tempToReal.set(item.tempId, created.id)
    }
  }
  return tempToReal
}

watch(loaded, (v) => {
  if (v) reload()
}, {immediate: true})
</script>

<template>
  <ViewContent
      :title="t('pages.procedure-create.title')"
      :subtitle="t('pages.procedure-create.subtitle')"
  >
    <Spinner v-if="loading"/>
    <Alert v-if="error || saveError" variant="error" class="mb-4">{{ error || saveError }}</Alert>

    <template v-if="!loading">
      <div class="space-y-6">
        <TemplateSelectorSection
            v-if="!isEditMode"
            :templates="templates"
            :selected-template-id="selectedTemplateId"
            @change="handleTemplateChange"
        />

        <BasicInfoSection
            v-model:name="name"
            v-model:description="description"
            v-model:due-at="dueAt"
            v-model:is-public="isPublic"
        />

        <AssigneesSection
            v-model:assignee-picker-value="assigneePickerValue"
            :members="members"
            :selected-assignees="selectedAssignees"
            :selected-assignee-ids="selectedAssigneeIds"
            @add="addAssignee"
            @remove="removeAssignee"
        />

        <ItemsSection
            :items="items"
            @add="showAddItemModal = true"
            @move="moveItem"
            @remove="removeItem"
        />

        <div class="flex justify-end gap-2">
          <SecondaryButton @click="router.back()">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton :disabled="!name.trim() || saving" @click="handleSubmit">
            {{ isEditMode ? t('common.save') : t('procedures.createProcedure') }}
          </PrimaryButton>
        </div>
      </div>
    </template>

    <!-- Add Item Modal -->
    <Modal v-model="showAddItemModal">
      <SubHeader class="mb-3">{{ t('procedures.addItem') }}</SubHeader>
      <form @submit.prevent="addItem" class="space-y-3">
        <TextInput v-model="newItemTitle" :placeholder="t('procedures.itemTitle')" required/>
        <TextAreaInput v-model="newItemDescription" :placeholder="t('procedures.itemDescription')"/>
        <div class="flex gap-2 justify-end">
          <PrimaryButton type="submit">{{ t('procedures.addItem') }}</PrimaryButton>
        </div>
      </form>
    </Modal>
  </ViewContent>
</template>

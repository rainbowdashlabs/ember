/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, computed, watch, onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ProcedureItemCard from '@/views/stationview/procedure/procedurecreateview/ProcedureItemCard.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import {useSession} from '@/composables/useSession'
import {procedures, stationMembers} from '@/api'
import type {ProcedureTemplate, ProcedureTemplateItem, ProcedureDetail, TemplateDetail} from '@/api/procedures'
import type {MemberCompletion} from '@/api/stationMembers'
import type {MemberIdentity} from '@/api/types'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {loaded} = useSession()

const editId = computed(() => {
  const id = route.params.id
  return id ? Number(id) : null
})
const isEditMode = computed(() => editId.value != null)

const loading = ref(true)
const error = ref('')
const saving = ref(false)

// Form fields
const name = ref('')
const description = ref('')
const dueAt = ref('')
const isPublic = ref(true)

// Template
const templates = ref<ProcedureTemplate[]>([])
const selectedTemplateId = ref<number | null>(null)
const templateDetail = ref<TemplateDetail | null>(null)

// Existing item IDs (for edit mode — track which items already exist)
const existingItemIds = ref<Set<number>>(new Set())

// Items (editable before submit)
interface EditableItem {
  id?: number // set for existing items in edit mode
  tempId: number // local unique key for dependency tracking
  title: string
  description: string
  isPublic: boolean
  userAssigned: boolean
  position: number
  dependsOn: number[] // tempIds this item depends on
}

let nextTempId = 1
const items = ref<EditableItem[]>([])

// Assignees
const members = ref<MemberCompletion[]>([])
const selectedAssigneeIds = ref<number[]>([])
const assigneePickerValue = ref('')

// Add item modal
const showAddItemModal = ref(false)
const newItemTitle = ref('')
const newItemDescription = ref('')

const selectedAssignees = computed(() =>
    selectedAssigneeIds.value
        .map(id => members.value.find(m => m.id === id))
        .filter(Boolean) as MemberCompletion[]
)

async function loadInitialData() {
  loading.value = true
  try {
    const [tpls, mbrs] = await Promise.all([
      procedures.getTemplates(),
      stationMembers.listCompletions(),
    ])
    templates.value = tpls.filter(t => !t.archived)
    members.value = mbrs

    if (isEditMode.value) {
      // Edit mode: load existing procedure
      const detail = await procedures.getProcedure(editId.value!)
      name.value = detail.procedure.name
      description.value = detail.procedure.description ?? ''
      dueAt.value = detail.procedure.dueAt ?? ''
      isPublic.value = detail.procedure.isPublic
      selectedAssigneeIds.value = [...detail.assigneeIds]
      existingItemIds.value = new Set(detail.items.map(i => i.id))
      // Build a mapping from real item ID to tempId
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
      // Resolve dependencies using tempIds
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
      // Create mode: check for template query param
      const tplParam = route.query.template
      if (tplParam) {
        selectedTemplateId.value = Number(tplParam)
        await loadTemplate(Number(tplParam))
      }
    }
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function loadTemplate(id: number) {
  try {
    templateDetail.value = await procedures.getTemplate(id)
    // Populate name and description from template
    name.value = templateDetail.value.template.name
    description.value = templateDetail.value.template.description ?? ''
    // Populate items from template, mapping template item IDs to tempIds
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

async function handleSubmit() {
  if (!name.value.trim()) return
  saving.value = true
  error.value = ''
  try {
    if (isEditMode.value) {
      await submitEdit(editId.value!)
    } else {
      await submitCreate()
    }
  } catch {
    error.value = t('common.error')
  } finally {
    saving.value = false
  }
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

function toIdentity(m: MemberCompletion): MemberIdentity {
  return {
    stationUid: m.stationUid,
    memberUid: m.memberUid,
    name: m.name,
    nameColor: m.nameColor ?? null,
    displayTag: m.displayTag ?? null,
  }
}

watch(loaded, (v) => {
  if (v) loadInitialData()
}, {immediate: true})
</script>

<template>
  <ViewContent>
    <SectionHeader class="mb-4">{{ isEditMode ? t('procedures.editProcedure') : t('procedures.createProcedure') }}</SectionHeader>

    <Spinner v-if="loading"/>
    <Alert v-if="error" variant="error" class="mb-4">{{ error }}</Alert>

    <template v-if="!loading">
      <div class="space-y-6">
        <!-- Template selector (create mode only) -->
        <NeutralContainer v-if="!isEditMode">
          <FieldLabel class="mb-2">{{ t('procedures.loadFromTemplate') }}</FieldLabel>
          <SelectInput
              :model-value="selectedTemplateId != null ? String(selectedTemplateId) : ''"
              class="w-full"
              @update:model-value="handleTemplateChange($event || undefined)"
          >
            <option value="">{{ t('procedures.noTemplate') }}</option>
            <option v-for="tpl in templates" :key="tpl.id" :value="String(tpl.id)">{{ tpl.name }}</option>
          </SelectInput>
          <MutedText size="sm" class="mt-1">{{ t('procedures.templateHint') }}</MutedText>
        </NeutralContainer>

        <!-- Basic info -->
        <NeutralContainer class="space-y-3">
          <SubHeader>{{ t('procedures.details') }}</SubHeader>
          <div>
            <FieldLabel class="mb-1">{{ t('procedures.name') }}</FieldLabel>
            <TextInput v-model="name" :placeholder="t('procedures.name')" required/>
          </div>
          <div>
            <FieldLabel class="mb-1">{{ t('procedures.description') }}</FieldLabel>
            <TextAreaInput v-model="description" :placeholder="t('procedures.description')"/>
          </div>
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <FieldLabel class="mb-1">{{ t('procedures.dueDate') }}</FieldLabel>
              <DateInput v-model="dueAt"/>
            </div>
            <div class="flex items-center gap-2 mt-4">
              <ToggleInput v-model="isPublic"/>
              <FieldLabel>{{ t('procedures.publicProcedure') }}</FieldLabel>
            </div>
          </div>
        </NeutralContainer>

        <!-- Assignees -->
        <NeutralContainer class="space-y-3">
          <SubHeader>{{ t('procedures.assignees') }}</SubHeader>
          <div class="flex gap-2">
            <div class="flex-1">
              <MemberSelectInput
                  v-model="assigneePickerValue"
                  :members="members.filter(m => !selectedAssigneeIds.includes(m.id))"
                  :placeholder="t('procedures.selectAssignee')"
              />
            </div>
            <PrimaryButton :disabled="!assigneePickerValue" @click="addAssignee">
              <font-awesome-icon :icon="['fas', 'plus']" class="mr-1"/> {{ t('common.add') }}
            </PrimaryButton>
          </div>
          <div v-if="selectedAssignees.length > 0" class="flex flex-wrap gap-2">
            <div v-for="a in selectedAssignees" :key="a.id"
                 class="flex items-center gap-1 bg-(--bg-light-accent) dark:bg-(--bg-dark-accent) rounded-full px-3 py-1">
              <MemberName :identity="toIdentity(a)" size="sm"/>
              <IconButton :icon="['fas', 'xmark']" :label="t('common.remove')" class="!p-0 ml-1"
                          @click="removeAssignee(a.id)"/>
            </div>
          </div>
          <MutedText v-else size="sm">{{ t('procedures.noAssignees') }}</MutedText>
        </NeutralContainer>

        <!-- Items / Steps -->
        <NeutralContainer class="space-y-3">
          <div class="flex items-center justify-between">
            <SubHeader>{{ t('procedures.items') }}</SubHeader>
            <SecondaryButton @click="showAddItemModal = true">
              <font-awesome-icon :icon="['fas', 'plus']" class="mr-1"/> {{ t('procedures.addItem') }}
            </SecondaryButton>
          </div>

          <MutedText v-if="items.length === 0" size="sm">{{ t('procedures.noItems') }}</MutedText>

          <ProcedureItemCard
              v-for="(item, index) in items" :key="item.tempId"
              :item="item" :index="index" :total-items="items.length" :all-items="items"
              @move="moveItem(index, $event)" @remove="removeItem(index)"
          />
        </NeutralContainer>

        <!-- Submit -->
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

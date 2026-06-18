/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import ColorInput from '@/components/input/ColorInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import Modal from '@/components/feedback/Modal.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {EventCategory} from '@/api/types'
import {events} from '@/api'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const {loaded} = useSession()

const categories = ref<EventCategory[]>([])
const loading = ref(true)
const error = ref('')

// Edit modal
const editOpen = ref(false)
const editId = ref<number | null>(null)
const editName = ref('')
const editMaxShown = ref<number | null>(null)
const editPublic = ref(false)
const editColor = ref<string>('')

// Delete modal
const deleteOpen = ref(false)
const deleteTarget = ref<EventCategory | null>(null)

async function loadData() {
  loading.value = true
  try {
    categories.value = await events.listCategories()
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

onMounted(() => { if (loaded.value) loadData() })
watch(loaded, v => { if (v && loading.value) loadData() })

function openCreate() {
  editId.value = null
  editName.value = ''
  editMaxShown.value = null
  editPublic.value = false
  editColor.value = ''
  editOpen.value = true
}

function openEdit(cat: EventCategory) {
  editId.value = cat.id
  editName.value = cat.name ?? ''
  editMaxShown.value = cat.maxShownEvents ?? null
  editPublic.value = cat.isPublic ?? false
  editColor.value = cat.color ?? ''
  editOpen.value = true
}

async function saveCategory() {
  try {
    const data = {
      name: editName.value,
      position: 0,
      maxShownEvents: editMaxShown.value || null,
      isPublic: editPublic.value,
      color: editColor.value ? editColor.value : null,
    }
    if (editId.value) {
      await events.updateCategory(editId.value, data)
    } else {
      await events.createCategory(data)
    }
    editOpen.value = false
    await loadData()
  } catch (e) {
    error.value = t('common.error')
    throw e
  }
}

function requestDelete(cat: EventCategory) {
  deleteTarget.value = cat
  deleteOpen.value = true
}

async function confirmDelete() {
  if (!deleteTarget.value) return
  try {
    await events.deleteCategory(deleteTarget.value.id)
    deleteOpen.value = false
    deleteTarget.value = null
    await loadData()
  } catch {
    error.value = t('common.error')
  }
}

async function moveUp(index: number) {
  if (index <= 0) return
  const ids = categories.value.map(c => c.id)
  ;[ids[index - 1], ids[index]] = [ids[index], ids[index - 1]]
  categories.value = await events.reorderCategories(ids)
}

async function moveDown(index: number) {
  if (index >= categories.value.length - 1) return
  const ids = categories.value.map(c => c.id)
  ;[ids[index], ids[index + 1]] = [ids[index + 1], ids[index]]
  categories.value = await events.reorderCategories(ids)
}
</script>

<template>
  <ViewContent>
    <div class="flex items-center justify-between mb-4">
      <SectionHeader>{{ t('categoryManage.title') }}</SectionHeader>
      <PrimaryButton :icon="['fas', 'plus']" @click="openCreate">
        {{ t('categoryManage.create') }}
      </PrimaryButton>
    </div>

    <Spinner v-if="loading"/>
    <Alert v-if="error" variant="error">{{ error }}</Alert>

    <MutedText v-if="!loading && categories.length === 0" tag="div" size="sm" class="py-4">
      {{ t('categoryManage.empty') }}
    </MutedText>

    <div v-else class="space-y-2">
      <NeutralContainer
          v-for="(cat, index) in categories"
          :key="cat.id"
          class="flex items-center justify-between"
      >
        <div class="flex items-center gap-2">
          <span
              v-if="cat.color"
              class="inline-block w-4 h-4 rounded border border-(--border) shrink-0"
              :style="{ backgroundColor: cat.color }"
              :aria-label="t('categoryManage.color')"
          />
          <span class="font-medium">{{ cat.name }}</span>
          <MutedText v-if="cat.maxShownEvents" tag="span" size="sm" class="ml-2">
            ({{ t('categoryManage.maxShown', {count: cat.maxShownEvents}) }})
          </MutedText>
        </div>
        <div class="flex items-center gap-1">
          <IconButton :icon="['fas', 'chevron-up']" :label="t('common.moveUp')" :disabled="index === 0" @click="moveUp(index)"/>
          <IconButton :icon="['fas', 'chevron-down']" :label="t('common.moveDown')" :disabled="index === categories.length - 1" @click="moveDown(index)"/>
          <IconButton :icon="['fas', 'pen']" :label="t('common.edit')" @click="openEdit(cat)"/>
          <DeleteButton :label="t('common.delete')" @click="requestDelete(cat)"/>
        </div>
      </NeutralContainer>
    </div>

    <!-- Edit/Create Modal -->
    <Modal v-model="editOpen">
      <div class="space-y-4">
        <SectionHeader>{{ editId ? t('categoryManage.edit') : t('categoryManage.create') }}</SectionHeader>
        <div class="space-y-1">
          <FieldLabel>{{ t('categoryManage.name') }}</FieldLabel>
          <TextInput v-model="editName" :placeholder="t('categoryManage.namePlaceholder')"/>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('categoryManage.maxShownLabel') }}</FieldLabel>
          <NumberInput :model-value="editMaxShown ?? 0" @update:model-value="editMaxShown = $event || null"/>
          <MutedText tag="p" size="sm">{{ t('categoryManage.maxShownHint') }}</MutedText>
        </div>
        <div class="flex items-center justify-between">
          <FieldLabel>{{ t('categoryManage.public') }}</FieldLabel>
          <ToggleInput v-model="editPublic"/>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('categoryManage.color') }}</FieldLabel>
          <div class="flex items-center gap-2">
            <ColorInput v-model="editColor"/>
            <SecondaryButton v-if="editColor" @click="editColor = ''">
              <font-awesome-icon :icon="['fas', 'xmark']"/>
            </SecondaryButton>
          </div>
          <MutedText tag="p" size="sm">{{ t('categoryManage.colorHint') }}</MutedText>
        </div>

        <div class="flex justify-end gap-3">
          <SecondaryButton @click="editOpen = false">{{ t('common.cancel') }}</SecondaryButton>
          <SaveButton :disabled="!editName.trim()" :action="saveCategory"/>
        </div>
      </div>
    </Modal>

    <!-- Delete Confirmation Modal -->
    <Modal v-model="deleteOpen">
      <div class="space-y-4">
        <SectionHeader>{{ t('categoryManage.deleteConfirmTitle') }}</SectionHeader>
        <p>{{ t('categoryManage.deleteConfirmText', {name: deleteTarget?.name}) }}</p>
        <div class="flex justify-end gap-3">
          <SecondaryButton @click="deleteOpen = false">{{ t('common.cancel') }}</SecondaryButton>
          <DeleteButton :label="t('common.delete')" @click="confirmDelete"/>
        </div>
      </div>
    </Modal>
  </ViewContent>
</template>

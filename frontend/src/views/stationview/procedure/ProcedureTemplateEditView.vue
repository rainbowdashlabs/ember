/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import Modal from '@/components/feedback/Modal.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TemplateItemCard from '@/views/stationview/procedure/proceduretemplateeditview/TemplateItemCard.vue'
import { useSession } from '@/composables/useSession'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { procedures } from '@/api'
import { StationPermission } from '@/api/types'
import type { TemplateDetail, ProcedureTemplateItem } from '@/api/procedures'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { hasPermission, loaded } = useSession()

const canManage = computed(() => hasPermission(StationPermission.PROCEDURE_MANAGER))

const detail = ref<TemplateDetail | null>(null)

const templateId = computed(() => Number(route.params.id))

const showEditModal = ref(false)
const editName = ref('')
const editDescription = ref('')

const showItemModal = ref(false)
const editingItem = ref<ProcedureTemplateItem | null>(null)
const itemTitle = ref('')
const itemDescription = ref('')
const itemIsPublic = ref(true)
const itemUserAssigned = ref(false)

const showDepModal = ref(false)
const depTargetItem = ref<ProcedureTemplateItem | null>(null)
const depSelectedId = ref<number | null>(null)

const {loading, error, reload} = useAsyncLoader(async () => {
  detail.value = await procedures.getTemplate(templateId.value)
}, {autoLoad: false})

function openEditModal() {
  if (!detail.value) return
  editName.value = detail.value.template.name
  editDescription.value = detail.value.template.description ?? ''
  showEditModal.value = true
}

async function handleEdit() {
  if (!editName.value.trim()) return
  try {
    await procedures.updateTemplate(templateId.value, {
      name: editName.value.trim(),
      description: editDescription.value || undefined,
    })
    showEditModal.value = false
    await reload()
  } catch {
    error.value = t('common.error')
  }
}

function openAddItemModal() {
  editingItem.value = null
  itemTitle.value = ''
  itemDescription.value = ''
  itemIsPublic.value = true
  itemUserAssigned.value = false
  showItemModal.value = true
}

function openEditItemModal(item: ProcedureTemplateItem) {
  editingItem.value = item
  itemTitle.value = item.title
  itemDescription.value = item.description ?? ''
  itemIsPublic.value = item.isPublic
  itemUserAssigned.value = item.userAssigned
  showItemModal.value = true
}

async function handleSaveItem() {
  if (!itemTitle.value.trim()) return
  try {
    if (editingItem.value) {
      await procedures.updateTemplateItem(templateId.value, editingItem.value.id, {
        title: itemTitle.value.trim(),
        description: itemDescription.value || undefined,
        isPublic: itemIsPublic.value,
        userAssigned: itemUserAssigned.value,
      })
    } else {
      await procedures.createTemplateItem(templateId.value, {
        title: itemTitle.value.trim(),
        description: itemDescription.value || undefined,
        isPublic: itemIsPublic.value,
        userAssigned: itemUserAssigned.value,
      })
    }
    showItemModal.value = false
    await reload()
  } catch {
    error.value = t('common.error')
  }
}

async function handleDeleteItem(itemId: number) {
  try {
    await procedures.deleteTemplateItem(templateId.value, itemId)
    await reload()
  } catch {
    error.value = t('common.error')
  }
}

function getDepsForItem(itemId: number): number[] {
  if (!detail.value) return []
  return detail.value.dependencies.filter(d => d[1] === itemId).map(d => d[0])
}

function getItemById(itemId: number): ProcedureTemplateItem | undefined {
  return detail.value?.items.find(i => i.id === itemId)
}

function openDepModal(item: ProcedureTemplateItem) {
  depTargetItem.value = item
  depSelectedId.value = null
  showDepModal.value = true
}

async function addDependency() {
  if (!depTargetItem.value || depSelectedId.value == null || !detail.value) return
  const newDeps = [...detail.value.dependencies, [depSelectedId.value, depTargetItem.value.id]]
  try {
    await procedures.setTemplateDependencies(templateId.value, newDeps)
    depSelectedId.value = null
    await reload()
  } catch {
    error.value = t('common.error')
  }
}

async function removeDependency(fromId: number, toId: number) {
  if (!detail.value) return
  const newDeps = detail.value.dependencies.filter(d => !(d[0] === fromId && d[1] === toId))
  try {
    await procedures.setTemplateDependencies(templateId.value, newDeps)
    await reload()
  } catch {
    error.value = t('common.error')
  }
}

watch(loaded, (v) => { if (v) reload() }, { immediate: true })
</script>

<template>
  <ViewContent
      :title="t('pages.procedure-template-edit.title')"
      :subtitle="t('pages.procedure-template-edit.subtitle')"
  >
    <Spinner v-if="loading" />
    <Alert v-if="error" variant="error" class="mb-4">{{ error }}</Alert>

    <template v-if="detail && !loading">
      <!-- Header -->
      <div class="flex items-start justify-between mb-4 gap-4">
        <div class="flex-1 min-w-0">
          <p v-if="detail.template.description" class="text-[var(--text-muted)] text-sm mt-1">{{ detail.template.description }}</p>
        </div>
        <div v-if="canManage" class="flex gap-2 shrink-0">
          <SecondaryButton @click="openEditModal">
            <font-awesome-icon :icon="['fas', 'pen']" class="mr-1" /> {{ t('common.edit') }}
          </SecondaryButton>
          <SecondaryButton @click="router.push({ name: 'procedure-template-list' })">
            <font-awesome-icon :icon="['fas', 'arrow-left']" class="mr-1" /> {{ t('common.back') }}
          </SecondaryButton>
        </div>
      </div>

      <!-- Items -->
      <div class="flex items-center justify-between mb-3">
        <SubHeader>{{ t('procedures.items') }}</SubHeader>
        <PrimaryButton v-if="canManage" @click="openAddItemModal">
          <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" /> {{ t('procedures.addItem') }}
        </PrimaryButton>
      </div>

      <div class="space-y-2">
        <TemplateItemCard
          v-for="(item, index) in detail.items"
          :key="item.id"
          :item="item"
          :index="index"
          :can-manage="canManage"
          :dependencies="getDepsForItem(item.id)"
          :get-item-by-id="getItemById"
          @edit="openEditItemModal"
          @delete="handleDeleteItem"
          @open-deps="openDepModal"
          @remove-dep="removeDependency"
        />
      </div>

      <div v-if="detail.items.length === 0" class="text-center text-[var(--text-muted)] py-8">
        {{ t('procedures.emptyTemplates') }}
      </div>
    </template>

    <!-- Edit Template Modal -->
    <Modal v-model="showEditModal">
      <SubHeader class="mb-3">{{ t('procedures.editTemplate') }}</SubHeader>
      <form @submit.prevent="handleEdit" class="space-y-3">
        <TextInput v-model="editName" :placeholder="t('procedures.templateName')" required />
        <TextAreaInput v-model="editDescription" :placeholder="t('procedures.templateDescription')" />
        <div class="flex gap-2 justify-end">
          <PrimaryButton type="submit">{{ t('common.save') }}</PrimaryButton>
        </div>
      </form>
    </Modal>

    <!-- Add/Edit Item Modal -->
    <Modal v-model="showItemModal">
      <SubHeader class="mb-3">{{ editingItem ? t('procedures.editItem') : t('procedures.addItem') }}</SubHeader>
      <form @submit.prevent="handleSaveItem" class="space-y-3">
        <TextInput v-model="itemTitle" :placeholder="t('procedures.itemTitle')" required />
        <TextAreaInput v-model="itemDescription" :placeholder="t('procedures.itemDescription')" />
        <div class="flex items-center gap-4">
          <div class="flex items-center gap-2">
            <FieldLabel>{{ t('procedures.itemPublic') }}</FieldLabel>
            <ToggleInput v-model="itemIsPublic" />
          </div>
          <div class="flex items-center gap-2">
            <FieldLabel>{{ t('procedures.itemUserAssigned') }}</FieldLabel>
            <ToggleInput v-model="itemUserAssigned" />
          </div>
        </div>
        <div class="flex gap-2 justify-end">
          <PrimaryButton type="submit">{{ t('common.save') }}</PrimaryButton>
        </div>
      </form>
    </Modal>

    <!-- Dependency Modal -->
    <Modal v-model="showDepModal">
      <SubHeader class="mb-3">{{ t('procedures.dependencies') }}: {{ depTargetItem?.title }}</SubHeader>
      <div v-if="detail" class="space-y-3">
        <div class="flex gap-2">
          <SelectInput :model-value="depSelectedId != null ? String(depSelectedId) : ''" @update:model-value="(v: string | undefined) => { depSelectedId = v ? Number(v) : null }" class="flex-1">
            <option value="">{{ t('procedures.dependsOn') }}...</option>
            <option
              v-for="item in detail.items.filter(i => i.id !== depTargetItem?.id && !getDepsForItem(depTargetItem?.id ?? 0).includes(i.id))"
              :key="item.id"
              :value="String(item.id)"
            >
              {{ item.title }}
            </option>
          </SelectInput>
          <PrimaryButton :disabled="depSelectedId == null" @click="addDependency">
            <font-awesome-icon :icon="['fas', 'plus']" />
          </PrimaryButton>
        </div>
        <div v-if="depTargetItem && getDepsForItem(depTargetItem.id).length > 0" class="space-y-1">
          <div v-for="depId in getDepsForItem(depTargetItem.id)" :key="depId" class="flex items-center justify-between bg-[var(--bg-light-accent)] dark:bg-[var(--bg-dark-accent)] rounded px-2 py-1 text-sm">
            <span>{{ getItemById(depId)?.title ?? depId }}</span>
            <IconButton :icon="['fas', 'trash']" label="Remove" class="!p-0 text-[var(--error)]" @click="removeDependency(depId, depTargetItem!.id)" />
          </div>
        </div>
        <p v-else class="text-sm text-[var(--text-muted)]">{{ t('procedures.empty') }}</p>
      </div>
    </Modal>
  </ViewContent>
</template>

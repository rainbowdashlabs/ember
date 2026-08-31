/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ColorInput from '@/components/input/ColorInput.vue'
import Modal from '@/components/feedback/Modal.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import ColorBadge from '@/components/badge/ColorBadge.vue'
import {inventoryTags} from '@/api'
import type {InventoryTag, RecommendedTag} from '@/api/inventoryTags'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useConfirmDelete} from '@/composables/useConfirmDelete'
import {apiErrorMessage} from '@/util/apiError'

/**
 * The words this station puts on its things.
 *
 * The list also shows what the association above the station recommends. A recommendation the
 * station already uses is marked as such rather than offered again: the two rows are one word
 * already, and nothing here asks anybody to tidy that up.
 */
const {t} = useI18n()

const tags = ref<InventoryTag[]>([])
const recommendations = ref<RecommendedTag[]>([])
const saveError = ref('')

const showModal = ref(false)
const editing = ref<InventoryTag | null>(null)
const name = ref('')
const color = ref('')

const {loading, error, reload} = useAsyncLoader(async () => {
  const [own, recommended] = await Promise.all([inventoryTags.listTags(), inventoryTags.recommendedTags()])
  tags.value = own
  recommendations.value = recommended
})

function openAdd(preset = '') {
  editing.value = null
  name.value = preset
  color.value = ''
  showModal.value = true
}

function openEdit(tag: InventoryTag) {
  editing.value = tag
  name.value = tag.name
  color.value = tag.color ?? ''
  showModal.value = true
}

async function save() {
  saveError.value = ''
  try {
    const body = {name: name.value, color: color.value || null, position: editing.value?.position ?? tags.value.length}
    if (editing.value) {
      await inventoryTags.updateTag(editing.value.id, body)
    } else {
      await inventoryTags.createTag(body)
    }
    showModal.value = false
    await reload()
  } catch (e) {
    saveError.value = apiErrorMessage(e) ?? t('common.error')
    throw e
  }
}

const {show: showDeleteModal, target: deleteTarget, requestDelete, confirm: confirmDelete} =
    useConfirmDelete<InventoryTag>({onDelete: tag => inventoryTags.deleteTag(tag.id), onSuccess: reload, error})
</script>

<template>
  <NeutralContainer class="space-y-4" data-testid="inventory-tags">
    <div class="flex items-center justify-between">
      <SubHeader>{{ t('inventory.tag.title') }}</SubHeader>
      <SecondaryButton :icon="['fas', 'plus']" data-testid="add-tag" @click="openAdd()">
        {{ t('inventory.tag.add') }}
      </SecondaryButton>
    </div>
    <MutedText size="sm">{{ t('inventory.tag.intro') }}</MutedText>

    <Spinner v-if="loading" size="sm"/>
    <Alert v-if="error" variant="error">{{ error }}</Alert>

    <div
        v-for="tag in tags"
        :key="tag.id"
        class="flex items-center justify-between px-3 py-2 border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50"
        data-testid="tag-row"
    >
      <ColorBadge :color="tag.color ?? undefined">{{ tag.name }}</ColorBadge>
      <div class="flex items-center gap-3">
        <MutedText size="sm">{{ t('inventory.tag.itemCount', {count: tag.itemCount}) }}</MutedText>
        <EditButton @click="openEdit(tag)"/>
        <DeleteButton @click="requestDelete(tag)"/>
      </div>
    </div>

    <div v-if="!loading && tags.length === 0" class="text-center text-(--text-muted) py-4 text-sm">
      {{ t('inventory.tag.none2') }}
    </div>

    <div v-if="recommendations.length > 0" class="space-y-2 pt-2" data-testid="tag-recommendations">
      <FieldLabel>{{ t('inventory.tag.recommended') }}</FieldLabel>
      <MutedText size="sm">{{ t('inventory.tag.recommendedHint') }}</MutedText>
      <div class="flex flex-wrap items-center gap-2">
        <template v-for="tag in recommendations" :key="tag.name">
          <ColorBadge v-if="tag.adopted" :color="tag.color ?? undefined">
            {{ t('inventory.tag.recommendedAdopted', {name: tag.name}) }}
          </ColorBadge>
          <SecondaryButton v-else :icon="['fas', 'plus']" @click="openAdd(tag.name)">{{ tag.name }}</SecondaryButton>
        </template>
      </div>
    </div>
  </NeutralContainer>

  <Modal v-model="showModal">
    <div class="space-y-4">
      <SectionHeader>{{ editing ? t('inventory.tag.edit') : t('inventory.tag.add') }}</SectionHeader>
      <Alert v-if="saveError" variant="error">{{ saveError }}</Alert>
      <div class="space-y-1">
        <FieldLabel>{{ t('inventory.tag.name') }}</FieldLabel>
        <TextInput v-model="name" data-testid="tag-name" :placeholder="t('inventory.tag.namePlaceholder')"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('inventory.tag.color') }}</FieldLabel>
        <ColorInput v-model="color"/>
      </div>
      <div class="flex justify-end gap-3">
        <SecondaryButton @click="showModal = false">{{ t('common.cancel') }}</SecondaryButton>
        <SaveButton :disabled="!name.trim()" :action="save"/>
      </div>
    </div>
  </Modal>

  <ConfirmDeleteModal
      v-model="showDeleteModal"
      :message="t('inventory.tag.deleteConfirm', {name: deleteTarget?.name})"
      @confirm="confirmDelete"
  />
</template>

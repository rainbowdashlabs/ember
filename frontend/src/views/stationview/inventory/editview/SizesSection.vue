/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import DragList from '@/components/input/DragList.vue'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'
import type {InventorySize} from '@/api/inventory'
import {inventory} from '@/api'

const {t} = useI18n()

const props = defineProps<{
  inventoryId: number
  sizes: InventorySize[]
}>()

const emit = defineEmits<{
  updated: []
  error: [message: string]
}>()

const showSizeModal = ref(false)
const editingSize = ref<InventorySize | null>(null)
const sizeLabel = ref('')
const sizeNote = ref('')

function openAddSize() {
  editingSize.value = null
  sizeLabel.value = ''
  sizeNote.value = ''
  showSizeModal.value = true
}

function openEditSize(size: InventorySize) {
  editingSize.value = size
  sizeLabel.value = size.label ?? ''
  sizeNote.value = size.note ?? ''
  showSizeModal.value = true
}

async function saveSize() {
  try {
    if (editingSize.value) {
      await inventory.updateSize(props.inventoryId, editingSize.value.id, {
        label: sizeLabel.value,
        position: editingSize.value.position,
        note: sizeNote.value,
      })
    } else {
      await inventory.createSize(props.inventoryId, {
        label: sizeLabel.value,
        position: props.sizes.length,
        note: sizeNote.value,
      })
    }
    showSizeModal.value = false
    emit('updated')
  } catch (e) {
    emit('error', t('common.error'))
    throw e
  }
}

async function deleteSize(size: InventorySize) {
  try {
    await inventory.deleteSize(props.inventoryId, size.id)
    emit('updated')
  } catch {
    emit('error', t('common.error'))
  }
}

async function onSizeReorder(fromIndex: number, toIndex: number) {
  const sizes = [...props.sizes]
  const [moved] = sizes.splice(fromIndex, 1)
  sizes.splice(toIndex, 0, moved)
  try {
    for (let i = 0; i < sizes.length; i++) {
      await inventory.updateSize(props.inventoryId, sizes[i].id, {
        label: sizes[i].label ?? '',
        position: i,
        note: sizes[i].note,
      })
    }
    emit('updated')
  } catch {
    emit('error', t('common.error'))
  }
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="flex items-center justify-between">
      <SubHeader>{{ t('inventory.edit.sizesTitle') }}</SubHeader>
      <SecondaryButton :icon="['fas', 'plus']" @click="openAddSize">
        {{ t('inventory.edit.addSize') }}
      </SecondaryButton>
    </div>

    <DragList :items="sizes" :key-fn="(s) => s.id" @reorder="onSizeReorder">
      <template #default="{ item: size }">
        <div
            class="flex items-center justify-between px-3 py-2 border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50 cursor-grab active:cursor-grabbing">
          <div class="flex items-center gap-2">
            <MutedIcon size="md" :icon="['fas', 'grip-vertical']"/>
            <div>
              <span class="text-sm font-medium">{{ size.label }}</span>
              <MutedText class="ml-2" v-if="size.note">{{ size.note }}</MutedText>
            </div>
          </div>
          <div class="flex items-center gap-1">
            <EditButton @click="openEditSize(size)"/>
            <DeleteButton @click="deleteSize(size)"/>
          </div>
        </div>
      </template>
    </DragList>

    <div v-if="sizes.length === 0" class="text-center text-(--text-muted) py-4 text-sm">
      {{ t('inventory.edit.noSizes') }}
    </div>
  </NeutralContainer>

  <!-- Size modal -->
  <Modal v-model="showSizeModal">
    <div class="space-y-4">
      <SectionHeader>{{ editingSize ? t('inventory.edit.editSize') : t('inventory.edit.addSize') }}</SectionHeader>
      <div class="space-y-1">
        <FieldLabel>{{ t('inventory.edit.sizeLabel') }}</FieldLabel>
        <TextInput v-model="sizeLabel" :placeholder="t('inventory.manage.sizeLabel')"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('inventory.edit.sizeNote') }}</FieldLabel>
        <TextInput v-model="sizeNote" :placeholder="t('inventory.edit.sizeNotePlaceholder')"/>
      </div>
      <div class="flex justify-end gap-3">
        <SecondaryButton @click="showSizeModal = false">{{ t('common.cancel') }}</SecondaryButton>
        <SaveButton :disabled="!sizeLabel.trim()" :action="saveSize"/>
      </div>
    </div>
  </Modal>
</template>

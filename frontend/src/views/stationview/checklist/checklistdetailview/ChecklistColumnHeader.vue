/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, onBeforeUnmount} from 'vue'
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import Modal from '@/components/feedback/Modal.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import type {ChecklistColumnDto} from '@/api/types'

const props = defineProps<{
  column: ChecklistColumnDto
  filter: 'any' | 'checked' | 'unchecked'
  visibleCount: number
}>()

const emit = defineEmits<{
  (e: 'set-filter', value: 'any' | 'checked' | 'unchecked'): void
  (e: 'edit'): void
  (e: 'bulk-tick'): void
  (e: 'bulk-clear'): void
}>()

const {t} = useI18n()
const menuOpen = ref(false)
const confirmTick = ref(false)
const confirmClear = ref(false)

function toggleMenu() {
  menuOpen.value = !menuOpen.value
}

function pickFilter(value: 'any' | 'checked' | 'unchecked') {
  emit('set-filter', value)
  menuOpen.value = false
}

function askBulkTick() {
  menuOpen.value = false
  confirmTick.value = true
}

function askBulkClear() {
  menuOpen.value = false
  confirmClear.value = true
}

function applyBulkTick() {
  emit('bulk-tick')
  confirmTick.value = false
}

function applyBulkClear() {
  emit('bulk-clear')
  confirmClear.value = false
}

function onClickOutside(event: MouseEvent) {
  const target = event.target as HTMLElement
  if (!target.closest(`[data-col-menu="${props.column.id}"]`)) menuOpen.value = false
}

document.addEventListener('click', onClickOutside)
onBeforeUnmount(() => document.removeEventListener('click', onClickOutside))
</script>

<template>
  <th
      class="px-2 py-1 border-b border-bg-light-accent dark:border-bg-dark-accent min-w-[180px] text-left align-top"
      :data-col-menu="column.id"
  >
    <div class="text-left">
      <div class="flex items-center gap-1">
        <span class="font-semibold truncate">{{ column.label }}</span>
        <div class="relative">
          <IconButton :icon="['fas', 'ellipsis']" :label="t('checklist.editColumn')" @click="toggleMenu"/>
          <div
              v-if="menuOpen"
              class="absolute left-0 mt-1 z-10 min-w-[200px] rounded-theme border border-bg-light-accent bg-bg-light shadow dark:border-bg-dark-accent dark:bg-bg-dark"
          >
            <DropdownMenuItem :icon="['fas', 'filter']" @click="pickFilter('any')">{{ t('checklist.filterAny') }}</DropdownMenuItem>
            <DropdownMenuItem :icon="['fas', 'square-check']" @click="pickFilter('checked')">{{ t('checklist.filterChecked') }}</DropdownMenuItem>
            <DropdownMenuItem :icon="['fas', 'square']" @click="pickFilter('unchecked')">{{ t('checklist.filterUnchecked') }}</DropdownMenuItem>
            <DropdownMenuItem :icon="['fas', 'check-double']" @click="askBulkTick">{{ t('checklist.bulkTickShown') }}</DropdownMenuItem>
            <DropdownMenuItem :icon="['fas', 'xmark']" @click="askBulkClear">{{ t('checklist.bulkClearShown') }}</DropdownMenuItem>
            <DropdownMenuItem :icon="['fas', 'pen']" @click="emit('edit'); menuOpen = false">{{ t('checklist.editColumn') }}</DropdownMenuItem>
          </div>
        </div>
      </div>
      <div v-if="column.description" class="text-xs text-(--text-muted) mt-0.5">{{ column.description }}</div>
      <div class="text-xs text-(--text-muted) mt-0.5">
        <span v-if="filter === 'checked'">{{ t('checklist.filterChecked') }}</span>
        <span v-else-if="filter === 'unchecked'">{{ t('checklist.filterUnchecked') }}</span>
        <span v-else>{{ t('checklist.filterAny') }}</span>
      </div>
    </div>

    <Modal v-model="confirmTick" size="sm">
      <div class="space-y-3">
        <div class="font-semibold">{{ t('checklist.bulkConfirmTickTitle') }}</div>
        <p>{{ t('checklist.bulkConfirmMessage', {count: visibleCount}) }}</p>
        <div class="flex justify-end gap-2">
          <SecondaryButton @click="confirmTick = false">{{ t('checklist.cancel') }}</SecondaryButton>
          <PrimaryButton @click="applyBulkTick">{{ t('checklist.bulkTickShown') }}</PrimaryButton>
        </div>
      </div>
    </Modal>

    <Modal v-model="confirmClear" size="sm">
      <div class="space-y-3">
        <div class="font-semibold">{{ t('checklist.bulkConfirmClearTitle') }}</div>
        <p>{{ t('checklist.bulkConfirmMessage', {count: visibleCount}) }}</p>
        <div class="flex justify-end gap-2">
          <SecondaryButton @click="confirmClear = false">{{ t('checklist.cancel') }}</SecondaryButton>
          <PrimaryButton @click="applyBulkClear">{{ t('checklist.bulkClearShown') }}</PrimaryButton>
        </div>
      </div>
    </Modal>
  </th>
</template>

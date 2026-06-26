/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import WaitingSectionToolbar from './waitingsection/WaitingSectionToolbar.vue'
import WaitingSectionDesktop from './waitingsection/WaitingSectionDesktop.vue'
import WaitingSectionMobile from './waitingsection/WaitingSectionMobile.vue'
import type { WaitingListEntryWithScore, WaitingListField } from '@/api/types'
import { ref, computed } from 'vue'

const props = defineProps<{
  entries: WaitingListEntryWithScore[]
  fields: WaitingListField[]
  visibleFieldIds: Set<number>
  isMobile: boolean
  showFieldToggle: boolean
  readonly?: boolean
  canAdd?: boolean
}>()

const emit = defineEmits<{
  invite: [entryId: number]
  moveToTesting: [entryId: number]
  navigateToEntry: [entryId: number]
  deleteEntry: [entry: WaitingListEntryWithScore]
  toggleField: [fieldId: number]
  toggleFieldMenu: []
  addEntry: []
}>()

const { t } = useI18n()

const visibleFields = computed(() => props.fields.filter(f => props.visibleFieldIds.has(f.id)))
const expandedId = ref<number | null>(null)

function toggleExpand(entryId: number) {
  expandedId.value = expandedId.value === entryId ? null : entryId
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <WaitingSectionToolbar
      :entries-count="entries.length"
      :fields="fields"
      :visible-field-ids="visibleFieldIds"
      :is-mobile="isMobile"
      :show-field-toggle="showFieldToggle"
      :can-add="canAdd"
      @toggle-field="(id) => emit('toggleField', id)"
      @toggle-field-menu="emit('toggleFieldMenu')"
      @add-entry="emit('addEntry')"
    />

    <EmptyState compact v-if="entries.length === 0">{{ t('waitingList.noEntries') }}</EmptyState>

    <WaitingSectionDesktop
      v-if="!isMobile && entries.length > 0"
      :entries="entries"
      :visible-fields="visibleFields"
      :expanded-id="expandedId"
      :readonly="readonly"
      @toggle-expand="toggleExpand"
      @invite="(id) => emit('invite', id)"
      @move-to-testing="(id) => emit('moveToTesting', id)"
      @navigate-to-entry="(id) => emit('navigateToEntry', id)"
      @delete-entry="(entry) => emit('deleteEntry', entry)"
    />

    <WaitingSectionMobile
      v-if="isMobile && entries.length > 0"
      :entries="entries"
      :expanded-id="expandedId"
      :readonly="readonly"
      @toggle-expand="toggleExpand"
      @invite="(id) => emit('invite', id)"
      @move-to-testing="(id) => emit('moveToTesting', id)"
      @navigate-to-entry="(id) => emit('navigateToEntry', id)"
      @delete-entry="(entry) => emit('deleteEntry', entry)"
    />
  </NeutralContainer>
</template>

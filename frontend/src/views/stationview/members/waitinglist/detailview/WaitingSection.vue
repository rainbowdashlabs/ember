/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import WaitingSectionToolbar from './waitingsection/WaitingSectionToolbar.vue'
import WaitingSectionDesktop from './waitingsection/WaitingSectionDesktop.vue'
import WaitingSectionMobile from './waitingsection/WaitingSectionMobile.vue'
import type { WaitingListEntryWithScore, WaitingListField } from '@/api/waitingList'
import { WaitingListFieldTypes } from '@/api/waitingList'
import { byDate, byValue, useSortable, type SortComparator } from '@/composables/useSortable'
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

/** The list has at most one, and it earns a column of its own rather than one of the toggles. */
const birthDateField = computed(() =>
    props.fields.find(f => f.fieldType === WaitingListFieldTypes.BIRTH_DATE) ?? null)

const anyBelowJoinAge = computed(() => props.entries.some(e => e.belowJoinAge))
const hideBelowJoinAge = ref(false)

const shown = computed(() =>
    hideBelowJoinAge.value ? props.entries.filter(e => !e.belowJoinAge) : props.entries)

function fieldValue(item: WaitingListEntryWithScore, fieldId: number): string | null {
  const raw = item.values.find(v => v.fieldId === fieldId)?.value
  return raw == null ? null : String(raw)
}

const comparators = (key: string): SortComparator<WaitingListEntryWithScore> | undefined => {
  if (key.startsWith('field-')) {
    const id = Number(key.slice('field-'.length))
    return byValue(item => fieldValue(item, id))
  }
  switch (key) {
    case 'firstname': return byValue(item => item.entry.firstname)
    case 'lastname': return byValue(item => item.entry.lastname)
    case 'createdAt': return byDate(item => item.entry.createdAt)
    case 'status': return byValue(item => item.entry.status)
    case 'score': return byValue(item => item.score)
    case 'birthDate': return byValue(item => item.age ?? null)
    default: return undefined
  }
}

// Held here rather than left to initialDirection, which is also the direction a newly picked
// column starts in: the list opens on the highest score, and a column picked after that starts at
// the top of its own order.
const sortState = { key: ref('score'), direction: ref<'asc' | 'desc'>('desc') }

const { sortKey, direction, sorted, toggle } = useSortable<WaitingListEntryWithScore, string>({
  items: shown,
  comparators,
  initialKey: 'score',
  state: sortState,
  fallback: byDate(item => item.entry.createdAt),
})

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

    <div v-if="anyBelowJoinAge" class="flex items-center gap-2">
      <ToggleInput v-model="hideBelowJoinAge"/>
      <span class="text-sm">{{ t('waitingList.hideBelowJoinAge') }}</span>
    </div>

    <EmptyState compact v-if="entries.length === 0">{{ t('waitingList.noEntries') }}</EmptyState>

    <WaitingSectionDesktop
      v-if="!isMobile && entries.length > 0"
      :birth-date-field="birthDateField"
      :direction="direction"
      :entries="sorted"
      :sort-key="sortKey"
      :visible-fields="visibleFields"
      :expanded-id="expandedId"
      :readonly="readonly"
      @sort="toggle"
      @toggle-expand="toggleExpand"
      @invite="(id) => emit('invite', id)"
      @move-to-testing="(id) => emit('moveToTesting', id)"
      @navigate-to-entry="(id) => emit('navigateToEntry', id)"
      @delete-entry="(entry) => emit('deleteEntry', entry)"
    />

    <WaitingSectionMobile
      v-if="isMobile && entries.length > 0"
      :entries="sorted"
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

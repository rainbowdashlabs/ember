/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import TRow from '@/components/table/TRow.vue'
import WaitingListStatusBadge from '@/components/badge/WaitingListStatusBadge.vue'
import WaitingSectionActions from './WaitingSectionActions.vue'
import WaitingSectionGuardians from './WaitingSectionGuardians.vue'
import type { WaitingListEntryWithScore, WaitingListField } from '@/api/waitingList'
import { formatDate } from '@/util/format'
import { displayFieldValue } from '../fieldDisplay'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  item: WaitingListEntryWithScore
  index: number
  visibleFields: WaitingListField[]
  birthDateField: WaitingListField | null
  expanded: boolean
  readonly?: boolean
}>()

const { t } = useI18n()

const emit = defineEmits<{
  toggleExpand: [entryId: number]
  invite: [entryId: number]
  moveToTesting: [entryId: number]
  navigateToEntry: [entryId: number]
  deleteEntry: [entry: WaitingListEntryWithScore]
}>()

function getEntryFieldValue(item: WaitingListEntryWithScore, fieldId: number): string {
  const v = item.values.find(v => v.fieldId === fieldId)?.value
  return v == null ? '' : String(v)
}
</script>

<template>
  <TRow class="hover:bg-bg-light-accent/30 dark:hover:bg-bg-dark-accent/30 cursor-pointer" @click="emit('toggleExpand', props.item.entry.id)">
    <td class="py-2 px-2 text-(--text-muted)">{{ props.index + 1 }}</td>
    <td class="py-2 px-2">
      <span class="text-primary hover:underline cursor-pointer" role="link" tabindex="0" @click.stop="emit('navigateToEntry', props.item.entry.id)" @keydown.enter="emit('navigateToEntry', props.item.entry.id)">
        {{ props.item.entry.firstname }}
      </span>
    </td>
    <td class="py-2 px-2">{{ props.item.entry.lastname }}</td>
    <td class="py-2 px-2 text-(--text-muted) whitespace-nowrap">{{ formatDate(props.item.entry.createdAt) }}</td>
    <td v-if="props.birthDateField" class="py-2 px-2 whitespace-nowrap"
        :class="props.item.belowJoinAge ? 'text-warning font-medium' : 'text-(--text-muted)'">
      <span v-if="getEntryFieldValue(props.item, props.birthDateField.id)">
        {{ formatDate(getEntryFieldValue(props.item, props.birthDateField.id)) }}
        <span v-if="props.item.age != null" class="text-xs">({{ props.item.age }})</span>
      </span>
      <span v-else>–</span>
    </td>
    <td v-for="vf in props.visibleFields" :key="vf.id" class="py-2 px-2 text-(--text-muted)">{{ displayFieldValue(vf, getEntryFieldValue(props.item, vf.id), t) || '–' }}</td>
    <td class="py-2 px-2 whitespace-nowrap">
      <WaitingListStatusBadge :status="props.item.entry.status" />
      <span v-if="props.item.belowJoinAge" :title="t('waitingList.belowJoinAgeHint')"
            class="ml-1 rounded bg-warning/15 px-1.5 py-0.5 text-xs font-medium text-warning">
        {{ t('waitingList.belowJoinAge') }}
      </span>
    </td>
    <td class="py-2 px-2 text-right font-mono">{{ props.item.score }}</td>
    <td v-if="!props.readonly" class="py-2 px-2">
      <WaitingSectionActions
        :item="props.item"
        @invite="(id) => emit('invite', id)"
        @move-to-testing="(id) => emit('moveToTesting', id)"
        @navigate-to-entry="(id) => emit('navigateToEntry', id)"
        @delete-entry="(entry) => emit('deleteEntry', entry)"
      />
    </td>
  </TRow>
  <tr v-if="props.expanded">
    <td :colspan="6 + props.visibleFields.length + (props.birthDateField ? 1 : 0) + (props.readonly ? 0 : 1)" class="px-4 py-3 bg-bg-light-accent/20 dark:bg-bg-dark-accent/20">
      <WaitingSectionGuardians :item="props.item" />
    </td>
  </tr>
</template>

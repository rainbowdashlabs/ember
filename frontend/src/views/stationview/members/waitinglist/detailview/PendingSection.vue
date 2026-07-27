/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type {WaitingListEntryWithScore, WaitingListField} from '@/api/types'
import {formatDate} from '@/util/format'

const props = defineProps<{
  entries: WaitingListEntryWithScore[]
  fields: WaitingListField[]
  readonly?: boolean
}>()

const emit = defineEmits<{
  approve: [entryId: number]
  reject: [entryId: number]
}>()

const {t} = useI18n()
const expandedId = ref<number | null>(null)

function fullName(item: WaitingListEntryWithScore): string {
  const e = item.entry
  return e.lastname ? `${e.firstname} ${e.lastname}` : e.firstname
}

function fieldName(fieldId: number): string {
  return props.fields.find(f => f.id === fieldId)?.name ?? `#${fieldId}`
}

function formatValue(value: unknown): string {
  return value == null ? '' : String(value)
}

function toggle(id: number) {
  expandedId.value = expandedId.value === id ? null : id
}
</script>

<template>
  <NeutralContainer v-if="entries.length > 0" class="space-y-4">
    <SubHeader>{{ t('waitingList.sectionPending') }}</SubHeader>
    <div class="space-y-2">
      <NeutralContainer v-for="item in entries" :key="item.entry.id" class="p-3">
        <div class="flex items-center justify-between cursor-pointer" @click="toggle(item.entry.id)">
          <div class="flex items-center gap-2 flex-1 min-w-0">
            <font-awesome-icon :icon="['fas', expandedId === item.entry.id ? 'chevron-down' : 'chevron-right']" class="h-3 w-3 text-(--text-muted) shrink-0"/>
            <span class="font-medium text-sm">{{ fullName(item) }}</span>
            <span v-if="item.entry.email" class="text-xs text-(--text-muted) truncate">{{ item.entry.email }}</span>
            <InfoBadge class="shrink-0">{{ t('waitingList.status_PENDING') }}</InfoBadge>
          </div>
          <div v-if="!readonly" class="flex items-center gap-2 shrink-0 ml-2" @click.stop>
            <SuccessButton size="sm" :icon="['fas', 'check']" @click="emit('approve', item.entry.id)">{{ t('waitingList.approve') }}</SuccessButton>
            <ErrorButton size="sm" :icon="['fas', 'xmark']" @click="emit('reject', item.entry.id)">{{ t('waitingList.reject') }}</ErrorButton>
          </div>
        </div>
        <div v-if="expandedId === item.entry.id" class="mt-3 pt-3 border-t border-(--border) space-y-2 text-sm">
          <div v-if="item.entry.notes" class="text-(--text-muted)">
            <span class="font-medium text-(--text)">{{ t('waitingList.notes') }}:</span> {{ item.entry.notes }}
          </div>
          <div v-if="item.guardians.length > 0">
            <span class="font-medium">{{ t('waitingList.guardians') }}:</span>
            <span v-for="(g, gi) in item.guardians" :key="gi">
              {{ g.firstname }} {{ g.lastname }}<template v-if="g.email"> ({{ g.email }})</template><template v-if="g.phone">, {{ g.phone }}</template><template v-if="gi < item.guardians.length - 1">; </template>
            </span>
          </div>
          <div v-for="val in item.values" :key="val.fieldId" class="text-(--text-muted)">
            <span class="font-medium text-(--text)">{{ fieldName(val.fieldId) }}:</span> {{ formatValue(val.value) }}
          </div>
          <div class="text-xs text-(--text-muted)">{{ t('waitingList.createdAt') }}: {{ formatDate(item.entry.createdAt) }}</div>
        </div>
      </NeutralContainer>
    </div>
  </NeutralContainer>
</template>

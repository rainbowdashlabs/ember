/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {EventCategory} from '@/api/types'
import type {MemberRegistrationStats} from '@/api/events'
import {events} from '@/api'

const props = defineProps<{
  eventId: number
  defaultCategoryId?: number | null
  categories: EventCategory[]
}>()

const {t} = useI18n()

const stats = ref<MemberRegistrationStats[]>([])
const categoryId = ref(String(props.defaultCategoryId ?? ''))
const timeFilter = ref('12')
const loading = ref(false)

function getMonths(): number {
  if (timeFilter.value === 'year') {
    const now = new Date()
    return now.getMonth() + 1
  }
  return Number(timeFilter.value) || 12
}

async function loadStats() {
  loading.value = true
  try {
    stats.value = await events.getRegistrationStats(
        props.eventId,
        categoryId.value ? Number(categoryId.value) : undefined,
        getMonths(),
    )
  } finally {
    loading.value = false
  }
}

onMounted(loadStats)
watch(categoryId, loadStats)
watch(timeFilter, loadStats)

function priorityColor(priority: string): string {
  if (priority === 'HIGH') return 'text-[var(--error)]'
  if (priority === 'MEDIUM') return 'text-[var(--info)]'
  return 'text-[var(--success)]'
}

function priorityLabel(priority: string): string {
  if (priority === 'HIGH') return t('registrationStats.priorityHigh')
  if (priority === 'MEDIUM') return t('registrationStats.priorityMedium')
  if (priority === 'NONE') return '–'
  return t('registrationStats.priorityLow')
}

defineExpose({stats})
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div class="flex items-center justify-between flex-wrap gap-2">
      <SubHeader>{{ t('registrationStats.title') }}</SubHeader>
      <div class="flex gap-2">
        <SelectInput v-model="timeFilter" class="w-40">
          <option value="12">{{ t('registrationStats.lastYear') }}</option>
          <option value="year">{{ t('registrationStats.thisYear') }}</option>
        </SelectInput>
        <SelectInput v-model="categoryId" class="w-48">
          <option value="">{{ t('registrationStats.allCategories') }}</option>
          <option v-for="cat in categories" :key="cat.id" :value="String(cat.id)">{{ cat.name }}</option>
        </SelectInput>
      </div>
    </div>

    <MutedText v-if="stats.length === 0 && !loading" tag="div" size="sm">
      {{ t('registrationStats.noData') }}
    </MutedText>

    <div v-else class="overflow-x-auto">
      <table class="w-full text-sm border-collapse">
        <thead>
        <tr class="border-b border-(--border) text-left">
          <th class="p-2">{{ t('registrationStats.member') }}</th>
          <th class="p-2 text-center">{{ t('registrationStats.registered') }}</th>
          <th class="p-2 text-center">{{ t('registrationStats.accepted') }}</th>
          <th class="p-2 text-center">{{ t('registrationStats.denied') }}</th>
          <th class="p-2 text-center">{{ t('registrationStats.rate') }}</th>
          <th class="p-2 text-center">{{ t('registrationStats.priority') }}</th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="s in stats" :key="s.memberId" class="border-b border-(--border)">
          <td class="p-2 font-medium">{{ s.memberName }}</td>
          <td class="p-2 text-center">{{ s.registered }}</td>
          <td class="p-2 text-center"><SuccessBadge>{{ s.accepted }}</SuccessBadge></td>
          <td class="p-2 text-center"><ErrorBadge v-if="s.denied > 0">{{ s.denied }}</ErrorBadge><span v-else>0</span></td>
          <td class="p-2 text-center">{{ Math.round(s.acceptRate * 100) }}%</td>
          <td class="p-2 text-center">
            <span class="font-semibold text-xs" :class="priorityColor(s.priority)">{{ priorityLabel(s.priority) }}</span>
          </td>
        </tr>
        </tbody>
      </table>
    </div>
  </NeutralContainer>
</template>

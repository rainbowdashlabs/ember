/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import Spinner from '@/components/feedback/Spinner.vue'
import type {ProfileFieldChange} from '@/api/profileFieldChanges'
import HistoryChangeCard from './HistoryChangeCard.vue'
import HistoryPagination from './HistoryPagination.vue'

const {t} = useI18n()

defineProps<{
  loading: boolean
  changes: ProfileFieldChange[]
  offset: number
  limit: number
  total: number
  page: number
  totalPages: number
  formatDate: (dateStr?: string) => string
}>()

const emit = defineEmits<{
  prev: []
  next: []
}>()
</script>

<template>
  <Spinner v-if="loading" size="lg"/>
  <template v-if="!loading">
    <div v-if="changes.length === 0" class="text-(--text-muted) text-sm py-4">
      {{ t('memberChanges.noHistory') }}
    </div>
    <div class="space-y-2">
      <HistoryChangeCard
          v-for="change in changes"
          :key="change.id"
          :change="change"
          :format-date="formatDate"
      />
    </div>
    <HistoryPagination
        :offset="offset"
        :limit="limit"
        :total="total"
        :page="page"
        :total-pages="totalPages"
        @prev="emit('prev')"
        @next="emit('next')"
    />
  </template>
</template>

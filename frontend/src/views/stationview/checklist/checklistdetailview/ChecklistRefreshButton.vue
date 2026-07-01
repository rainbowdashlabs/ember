/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {formatDateTime} from '@/util/format'
import type {ChecklistRefreshResult} from '@/api/types'

const props = defineProps<{
  lastRefreshedAt?: string | null
  onRefresh: () => Promise<ChecklistRefreshResult>
}>()

const {t} = useI18n()
const refreshing = ref(false)

const label = computed(() => {
  if (refreshing.value) return t('common.loading')
  if (!props.lastRefreshedAt) return t('checklist.neverRefreshed')
  return t('checklist.lastRefreshed', {when: formatDateTime(props.lastRefreshedAt)})
})

async function run() {
  refreshing.value = true
  try {
    await props.onRefresh()
  } finally {
    refreshing.value = false
  }
}
</script>

<template>
  <SecondaryButton :disabled="refreshing" :title="label" @click="run">
    <font-awesome-icon :icon="['fas', 'arrows-rotate']" :spin="refreshing" class="mr-1"/>
    {{ t('checklist.refresh') }}
  </SecondaryButton>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {formatDateTime} from '@/util/format'
import type {ChecklistRefreshResult} from '@/api/checklists'

const props = defineProps<{
  lastRefreshedAt?: string | null
  /**
   * Whether this list was made from a named set of people rather than from a rule.
   *
   * <p>Then refreshing resolves the very same names again and can never bring anybody in, which is
   * the opposite of what a button called Auffrischen promises. It says so instead of pretending.
   */
  frozen?: boolean
  /**
   * Whether this list follows one evening of an appointment.
   *
   * <p>Then refreshing does bring people in, namely whoever has taken a place since the list was
   * last looked at, and that is worth saying because "follows" invites the stronger reading that
   * it happens on its own.
   */
  followsEvent?: boolean
  onRefresh: () => Promise<ChecklistRefreshResult>
}>()

const {t} = useI18n()

const {running: refreshing, run} = useAsyncAction(() => props.onRefresh())

const label = computed(() => {
  if (refreshing.value) return t('common.loading')
  if (props.frozen) return t('checklist.frozenSetHint')
  if (props.followsEvent) return t('checklist.followsEventRefresh')
  if (!props.lastRefreshedAt) return t('checklist.neverRefreshed')
  return t('checklist.lastRefreshed', {when: formatDateTime(props.lastRefreshedAt)})
})
</script>

<template>
  <SecondaryButton :disabled="refreshing" :title="label" @click="run">
    <font-awesome-icon :icon="['fas', 'arrows-rotate']" :spin="refreshing" class="mr-1"/>
    {{ t('checklist.refresh') }}
  </SecondaryButton>
</template>

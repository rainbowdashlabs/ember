/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import {formatDate} from '@/util/format'
import type {ExpectedRow} from './types'

defineProps<{
  row: ExpectedRow
}>()

const emit = defineEmits<{
  confirm: []
  missing: []
  lost: []
  reset: []
}>()

const {t} = useI18n()
</script>

<template>
  <li class="py-2 flex items-center gap-3">
    <span class="flex-1">
      <span class="font-medium">{{ row.item.name }}</span>
      <span v-if="row.item.internalId" class="text-xs text-(--text-muted) ml-2">{{ row.item.internalId }}</span>
      <span v-if="row.lastCheck" class="block text-xs text-(--text-muted) mt-0.5">
        {{ t('inventory.checkContainer.lastChecked', {
          date: formatDate(row.lastCheck.checkedAt),
          by: row.lastCheck.checkerName || t('common.unknown'),
        }) }}
        -
        {{ t(`inventory.checkContainer.previousResult.${row.lastCheck.result}`) }}
      </span>
      <span v-else class="block text-xs text-(--text-muted) mt-0.5">
        {{ t('inventory.checkContainer.lastCheckedNever') }}
      </span>
    </span>
    <SuccessBadge v-if="row.result === 'CONFIRMED'">{{ t('inventory.checkContainer.statusConfirmed') }}</SuccessBadge>
    <ErrorBadge v-else-if="row.result === 'LOST'">{{ t('inventory.checkContainer.statusLost') }}</ErrorBadge>
    <ErrorBadge v-else-if="row.result === 'NOT_IN_POSSESSION'">{{ t('inventory.checkContainer.statusMissing') }}</ErrorBadge>
    <InfoBadge v-else>{{ t('inventory.checkContainer.statusPending') }}</InfoBadge>
    <div class="flex gap-1">
      <IconButton v-if="row.result === 'PENDING'" :icon="['fas', 'check']" :label="t('inventory.checkContainer.markConfirmed')" @click="emit('confirm')" />
      <IconButton v-if="row.result !== 'NOT_IN_POSSESSION'" :icon="['fas', 'xmark']" :label="t('inventory.checkContainer.markMissing')" @click="emit('missing')" />
      <IconButton v-if="row.result !== 'LOST'" :icon="['fas', 'triangle-exclamation']" :label="t('inventory.checkContainer.markLost')" @click="emit('lost')" />
      <IconButton v-if="row.result !== 'PENDING'" :icon="['fas', 'rotate-left']" :label="t('inventory.checkContainer.reset')" @click="emit('reset')" />
    </div>
  </li>
</template>

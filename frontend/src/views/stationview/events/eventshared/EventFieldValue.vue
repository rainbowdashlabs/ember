/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import {EventFieldTypes} from '@/api/events'
import {formatDate, formatTime} from '@/util/format'

const props = defineProps<{
  fieldType?: string
  value?: string
}>()

const {t} = useI18n()

/**
 * The answer as a station reads it. A date and a clock are stored the way a database wants them and
 * were put on the page exactly like that, so an answer read as 2026-10-12 rather than as the
 * 12.10.2026 it is. An answer nobody can parse is left as written rather than dropped.
 */
const displayValue = computed(() => {
  const v = props.value
  if (!v) return '–'
  switch (props.fieldType) {
    case 'BOOLEAN':
      return v === 'true' ? t('common.yes') : t('common.no')
    case EventFieldTypes.DATE:
      return formatDate(v) || v
    case EventFieldTypes.TIME:
      return formatTime(v) || v
    default:
      return v
  }
})

const isBooleanField = computed(() => props.fieldType === 'BOOLEAN')
const booleanValue = computed(() => props.value === 'true')
</script>

<template>
  <template v-if="isBooleanField">
    <SuccessBadge v-if="booleanValue">{{ t('common.yes') }}</SuccessBadge>
    <ErrorBadge v-else>{{ t('common.no') }}</ErrorBadge>
  </template>
  <span v-else>{{ displayValue }}</span>
</template>

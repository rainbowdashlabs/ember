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

const props = defineProps<{
  fieldType?: string
  value?: string
}>()

const {t} = useI18n()

const displayValue = computed(() => {
  const v = props.value
  if (!v) return '–'
  switch (props.fieldType) {
    case 'BOOLEAN':
      return v === 'true' ? t('common.yes') : t('common.no')
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

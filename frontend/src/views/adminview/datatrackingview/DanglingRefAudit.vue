/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ErrorContainer from '@/components/container/ErrorContainer.vue'

export interface DanglingRef {
  table: string
  column: string
  identityType: string
  hint: string
}

defineProps<{
  refs: DanglingRef[]
}>()

const {t} = useI18n()
</script>

<template>
  <ErrorContainer
      v-if="refs.length"
      class="mb-4 text-sm"
  >
    <div class="font-semibold mb-1">
      <font-awesome-icon :icon="['fas', 'triangle-exclamation']" class="mr-1"/>
      {{ t('adminDataTracking.danglingHeader', {n: refs.length}) }}
    </div>
    <ul class="font-mono text-xs space-y-0.5">
      <li v-for="d in refs" :key="`${d.table}.${d.column}`">
        <span class="font-semibold">{{ d.table }}.{{ d.column }}</span>
        <span class="text-(--text-muted)"> - {{ d.hint }}</span>
      </li>
    </ul>
  </ErrorContainer>
</template>

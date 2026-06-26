/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  headers: string[]
  rows: string[][]
}>()

const { t } = useI18n()

const previewRows = computed(() => props.rows.slice(0, 5))
</script>

<template>
  <div class="space-y-2">
    <label class="text-xs text-(--text-muted) font-medium">{{ t('quiz.csv.preview') }} ({{ rows.length }} {{ t('quiz.csv.rows') }})</label>
    <div class="overflow-x-auto">
      <table class="text-xs w-full">
        <thead>
          <tr>
            <th v-for="h in headers" :key="h" class="text-left px-2 py-1 border-b border-bg-light-accent dark:border-bg-dark-accent">{{ h }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, ri) in previewRows" :key="ri">
            <td v-for="(cell, ci) in row" :key="ci" class="px-2 py-1 border-b border-bg-light-accent dark:border-bg-dark-accent truncate max-w-48">{{ cell }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

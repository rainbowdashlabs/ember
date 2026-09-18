/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import type {MemberTable} from '@/api/memberTable'

const {t} = useI18n()

defineProps<{table: MemberTable}>()
</script>

<template>
  <div class="overflow-x-auto" data-testid="member-table-preview-table">
    <MutedText v-if="table.rows.length === 0" tag="p" size="sm">{{ t('memberTable.nobody') }}</MutedText>
    <table v-else class="w-full text-sm">
      <thead>
        <tr class="border-b border-(--border-muted)">
          <th
              v-for="column in table.columns"
              :key="`${column.kind}-${column.key ?? column.fieldId}`"
              class="px-2 py-1 text-left font-semibold"
          >
            {{ column.label }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in table.rows" :key="row.memberId" class="border-b border-(--border-muted)">
          <td v-for="(value, index) in row.values" :key="index" class="px-2 py-1">{{ value }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import Td from '@/components/table/Td.vue'
import Th from '@/components/table/Th.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'

/**
 * The letters of an own date format, each with what it stands for and how a German template prints the
 * example day, Saturday 3 October 2026 at half past six in the evening. The help centre is read without
 * signing in, so it shows the table as the server prints it rather than asking the server.
 */
const {t} = useI18n()

const TOKENS: readonly {written: string, name: string, example: string}[] = [
  {written: 'T', name: 'day', example: '3'},
  {written: 'TT', name: 'dayTwoDigits', example: '03'},
  {written: 'TTT', name: 'weekdayShort', example: 'Sa.'},
  {written: 'TTTT', name: 'weekday', example: 'Samstag'},
  {written: 'M', name: 'month', example: '10'},
  {written: 'MM', name: 'monthTwoDigits', example: '10'},
  {written: 'MMM', name: 'monthShort', example: 'Okt.'},
  {written: 'MMMM', name: 'monthName', example: 'Oktober'},
  {written: 'JJ', name: 'yearTwoDigits', example: '26'},
  {written: 'JJJJ', name: 'year', example: '2026'},
  {written: 'h', name: 'hour', example: '18'},
  {written: 'hh', name: 'hourTwoDigits', example: '18'},
  {written: 'mm', name: 'minute', example: '30'},
]
</script>

<template>
  <div class="overflow-x-auto">
    <table class="w-full text-sm" data-testid="date-token-table">
      <thead>
        <THead>
          <Th>{{ t('helpCenter.documentTemplateEditor.dateTokensSign') }}</Th>
          <Th>{{ t('helpCenter.documentTemplateEditor.dateTokensMeaning') }}</Th>
          <Th>{{ t('helpCenter.documentTemplateEditor.dateTokensExample') }}</Th>
        </THead>
      </thead>
      <tbody>
        <TRow v-for="token in TOKENS" :key="token.written">
          <Td><code>{{ token.written }}</code></Td>
          <Td>{{ t(`helpCenter.documentTemplateEditor.dateTokens.${token.name}`) }}</Td>
          <Td muted>{{ token.example }}</Td>
        </TRow>
      </tbody>
    </table>
  </div>
</template>

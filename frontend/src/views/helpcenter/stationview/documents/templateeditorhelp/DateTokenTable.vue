/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {DocumentLanguage} from '@/api/generated/schema'
import Td from '@/components/table/Td.vue'
import Th from '@/components/table/Th.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'
import {compileDateFormat, DATE_TOKENS, exampleDate, printDate, type DateToken} from '@/util/dateFormatPattern'

/**
 * The letters of an own date format, each with what it stands for and how it prints the example day. It
 * reads the same table the editor's preview and the server read, so it cannot fall behind them.
 */
const {t} = useI18n()

function example(token: DateToken): string {
  const compiled = compileDateFormat(token.written)
  return compiled.ok ? printDate(compiled.parts, exampleDate(), DocumentLanguage.DE) : ''
}
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
        <TRow v-for="token in DATE_TOKENS" :key="token.written">
          <Td><code>{{ token.written }}</code></Td>
          <Td>{{ t(`helpCenter.documentTemplateEditor.dateTokens.${token.name}`) }}</Td>
          <Td muted>{{ example(token) }}</Td>
        </TRow>
      </tbody>
    </table>
  </div>
</template>

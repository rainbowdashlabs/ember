/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'

const {t} = useI18n()
</script>

<template>
  <div class="overflow-x-auto mt-3">
    <table class="w-full text-sm">
      <THead>
        <Th>{{ t('apiStatus.timestamp') }}</Th>
        <Th align="right">{{ t('apiStatus.statusCode') }}</Th>
        <Th align="right">{{ t('apiStatus.duration') }}</Th>
      </THead>
      <tbody>
        <TRow>
          <Td class="font-mono text-xs">07.06.26, 14:32:10</Td>
          <Td align="right" class="font-semibold text-success tabular-nums">200</Td>
          <Td align="right" class="tabular-nums">8ms</Td>
        </TRow>
        <TRow>
          <Td class="font-mono text-xs">07.06.26, 14:31:55</Td>
          <Td align="right" class="font-semibold text-success tabular-nums">200</Td>
          <Td align="right" class="tabular-nums">14ms</Td>
        </TRow>
        <TRow>
          <Td class="font-mono text-xs">07.06.26, 14:30:22</Td>
          <Td align="right" class="font-semibold text-error tabular-nums">500</Td>
          <Td align="right" class="tabular-nums">203ms</Td>
        </TRow>
      </tbody>
    </table>
  </div>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import Td from '@/components/table/Td.vue'
import TRow from '@/components/table/TRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'

defineProps<{
  station: string
  item: string
  quantity: number
  available: 'available' | 'partiallyAvailable'
}>()

const {t} = useI18n()
</script>

<template>
  <TRow>
    <Td class="font-medium">{{ station }}</Td>
    <Td>{{ item }}</Td>
    <Td muted>{{ quantity }}</Td>
    <Td>
      <SuccessBadge v-if="available === 'available'">{{ t('helpCenter.inventoryLendingBrowse.available') }}</SuccessBadge>
      <InfoBadge v-else>{{ t('helpCenter.inventoryLendingBrowse.partiallyAvailable') }}</InfoBadge>
    </Td>
    <Td align="right">
      <PrimaryButton :icon="['fas', 'paper-plane']">
        {{ t('helpCenter.inventoryLendingBrowse.dummyRequest') }}
      </PrimaryButton>
    </Td>
  </TRow>
</template>

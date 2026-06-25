/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import Td from '@/components/table/Td.vue'
import TRow from '@/components/table/TRow.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'

defineProps<{
  member: string
  lastChecked: string
  checkedBy: string
  showEye?: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <TRow>
    <Td class="font-medium">{{ member }}</Td>
    <Td muted>{{ lastChecked }}</Td>
    <Td muted>{{ checkedBy }}</Td>
    <Td><slot name="status"/></Td>
    <Td align="right">
      <div class="flex items-center justify-end gap-1">
        <MutedIconButton v-if="showEye ?? true" :icon="['fas', 'eye']" :label="t('inventory.check.showLastCheck')"/>
        <slot name="action"/>
      </div>
    </Td>
  </TRow>
</template>

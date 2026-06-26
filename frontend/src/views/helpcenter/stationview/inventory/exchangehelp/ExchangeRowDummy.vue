/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import Td from '@/components/table/Td.vue'
import TRow from '@/components/table/TRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

defineProps<{
  managerView: boolean
  member: string
  inventory: string
  showDelete?: boolean
  showForward?: boolean
}>()
</script>

<template>
  <TRow>
    <Td v-if="managerView" class="text-primary">{{ member }}</Td>
    <Td class="font-medium">{{ inventory }}</Td>
    <slot/>
    <Td align="right">
      <div class="flex items-center justify-end gap-1">
        <SecondaryButton><font-awesome-icon :icon="['fas', 'clock-rotate-left']"/></SecondaryButton>
        <template v-if="managerView">
          <SecondaryButton v-if="showForward"><font-awesome-icon :icon="['fas', 'arrow-right']"/></SecondaryButton>
          <DeleteButton v-if="showDelete ?? true"/>
        </template>
      </div>
    </Td>
  </TRow>
</template>

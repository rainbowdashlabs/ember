/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import DesktopHeaderIcons from './DesktopHeaderIcons.vue'
import {fieldGrid} from './fieldGrid'
import {DRAG_CONTROL_COLUMN} from '@/components/input/dragControls'
import {useFieldsCapabilities} from '@/composables/useFieldsConfig'

const {t} = useI18n()

const capabilities = useFieldsCapabilities()
const gridClass = computed(() => fieldGrid(capabilities.writability))
</script>

<template>
  <div class="flex gap-2">
    <div :class="[DRAG_CONTROL_COLUMN, 'shrink-0']"></div>
    <div
        :class="gridClass"
        class="flex-1 gap-0 items-center border-b border-bg-light-accent dark:border-bg-dark-accent text-sm px-1 py-2">
      <div class="font-medium px-2">{{ t('membersConfig.colName') }}</div>
      <div class="font-medium px-2">{{ t('membersConfig.colType') }}</div>
      <div class="font-medium px-2">{{ t('membersConfig.colWidth') }}</div>
      <DesktopHeaderIcons/>
      <div></div>
    </div>
  </div>
</template>

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
import {useFieldsCapabilities} from '@/composables/useFieldsConfig'

const {t} = useI18n()

const capabilities = useFieldsCapabilities()
const gridClass = computed(() => fieldGrid(capabilities.writability))
</script>

<template>
  <div
      :class="gridClass"
      class="gap-0 items-center border-b border-bg-light-accent dark:border-bg-dark-accent text-sm px-1 py-2">
    <div></div>
    <div class="font-medium px-2">{{ t('membersConfig.colName') }}</div>
    <div class="font-medium px-2">{{ t('membersConfig.colType') }}</div>
    <div class="font-medium px-2">{{ t('membersConfig.colWidth') }}</div>
    <DesktopHeaderIcons/>
    <div></div>
  </div>
</template>

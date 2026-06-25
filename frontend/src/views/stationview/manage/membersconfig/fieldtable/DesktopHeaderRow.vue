/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import DesktopHeaderIcons from './DesktopHeaderIcons.vue'

const {t} = useI18n()
</script>

<template>
  <div
      class="grid grid-cols-[2rem_1fr_6rem_2.5rem_2.5rem_2.5rem_2.5rem_2.5rem_5rem] gap-0 items-center border-b border-bg-light-accent dark:border-bg-dark-accent text-sm px-1 py-2">
    <div></div>
    <div class="font-medium px-2">{{ t('membersConfig.colName') }}</div>
    <div class="font-medium px-2">{{ t('membersConfig.colType') }}</div>
    <DesktopHeaderIcons/>
    <div></div>
  </div>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import DummySidebarFields from './DummySidebarFields.vue'

const {t} = useI18n()
</script>

<template>
  <div class="space-y-4">
    <div class="px-3 py-2 rounded-theme text-sm font-medium text-center text-white cursor-pointer"
         style="background-color: #3694FF">
      {{ t('helpCenter.ticketDetail.laneInProgress') }}
    </div>

    <DummySidebarFields/>

    <div class="text-xs text-(--text-muted) space-y-1 pt-4 border-t border-(--border)">
      <p>{{ t('helpCenter.ticketDetail.createdAt') }}: 01.06.26, 09:00</p>
      <p>{{ t('helpCenter.ticketDetail.updatedAt') }}: 05.06.26, 15:10</p>
    </div>
  </div>
</template>

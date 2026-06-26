/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'

const {t} = useI18n()

const groups = [
  {labelKey: 'linkBlocks', refId: 'JF-15', textKey: 'linkedTicket1'},
  {labelKey: 'linkRelatesTo', refId: 'JF-38', textKey: 'linkedTicket2'},
]
</script>

<template>
  <div>
    <SubHeader class="text-sm mb-2">{{ t('boards.linkedTickets') }}</SubHeader>
    <div v-for="g in groups" :key="g.refId" class="mb-2">
      <div class="text-xs font-semibold text-(--text-muted) uppercase tracking-wide mb-1">
        {{ t(`boards.${g.labelKey}`) }}
      </div>
      <div class="border border-(--border) rounded-theme overflow-hidden">
        <div class="flex items-center gap-3 px-3 py-2 text-sm">
          <span class="font-mono text-(--text-muted) shrink-0">{{ g.refId }}</span>
          <span class="truncate">{{ t(`helpCenter.ticketDetail.${g.textKey}`) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

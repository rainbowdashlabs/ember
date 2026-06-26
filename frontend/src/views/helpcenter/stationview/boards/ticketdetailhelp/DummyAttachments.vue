/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'

const {t} = useI18n()

const attachments = [
  {icon: 'image', textKey: 'attachmentExample'},
  {icon: 'file-pdf', textKey: 'attachmentPdf'},
]
</script>

<template>
  <div>
    <SubHeader class="text-sm mb-2">{{ t('boards.attachments') }}</SubHeader>
    <div class="grid grid-cols-3 sm:grid-cols-4 gap-2">
      <div v-for="a in attachments" :key="a.icon"
           class="relative rounded-lg border border-(--border) overflow-hidden bg-(--bg-accent)"
           style="aspect-ratio: 3/4">
        <div class="w-full h-full flex items-center justify-center">
          <font-awesome-icon :icon="['fas', a.icon]" class="text-3xl text-(--text-muted)"/>
        </div>
        <div class="absolute top-0 left-0 right-0 bg-black/50 px-2 py-1">
          <p class="text-[0.65rem] text-white truncate">{{ t(`helpCenter.ticketDetail.${a.textKey}`) }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

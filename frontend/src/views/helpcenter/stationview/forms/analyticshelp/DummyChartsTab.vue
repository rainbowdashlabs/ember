/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

const {t} = useI18n()
</script>

<template>
  <div class="space-y-4">
    <NeutralContainer>
      <div class="space-y-3">
        <SubHeader class="font-medium">Was hat dir zuletzt besonders gut gefallen?</SubHeader>
        <p class="text-xs text-(--text-muted)">12 {{ t('forms.responses') }}</p>
        <div class="flex items-center justify-center gap-6 py-4">
          <div class="w-24 h-24 rounded-full border-8 border-primary/40 border-t-primary border-r-secondary"></div>
          <div class="space-y-1 text-sm">
            <div class="flex items-center gap-2"><span class="w-3 h-3 rounded-full bg-primary inline-block"></span> Übungen (7)</div>
            <div class="flex items-center gap-2"><span class="w-3 h-3 rounded-full bg-secondary inline-block"></span> Gemeinschaft (3)</div>
            <div class="flex items-center gap-2"><span class="w-3 h-3 rounded-full bg-info inline-block"></span> Ausflüge (2)</div>
          </div>
        </div>
      </div>
    </NeutralContainer>

    <NeutralContainer>
      <div class="space-y-3">
        <SubHeader class="font-medium">Wie zufrieden bist du insgesamt?</SubHeader>
        <p class="text-xs text-(--text-muted)">12 {{ t('forms.responses') }}</p>
        <div class="flex items-end gap-3 h-24 px-2">
          <div v-for="bar in [{n: 1, h: 15}, {n: 2, h: 10}, {n: 3, h: 30}, {n: 4, h: 55}, {n: 5, h: 80}]" :key="bar.n" class="flex flex-col items-center gap-1 flex-1">
            <div class="w-full bg-primary rounded-t" :style="`height: ${bar.h}%`"></div>
            <span class="text-xs text-(--text-muted)">{{ bar.n }}</span>
          </div>
        </div>
      </div>
    </NeutralContainer>
  </div>
</template>

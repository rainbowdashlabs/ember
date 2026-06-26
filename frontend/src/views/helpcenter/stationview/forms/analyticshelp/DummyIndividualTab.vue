/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import IconButton from '@/components/button/IconButton.vue'
import MemberName from '@/components/avatar/MemberName.vue'

const {t} = useI18n()
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center justify-between">
      <span class="text-sm text-(--text-muted)">
        {{ t('forms.analytics.responseOf', {current: 1, total: 12}) }}
      </span>
      <div class="flex items-center gap-2">
        <IconButton :icon="['fas', 'chevron-left']" label="Zurück" disabled/>
        <IconButton :icon="['fas', 'chevron-right']" label="Weiter" disabled/>
      </div>
    </div>

    <NeutralContainer>
      <div class="space-y-1 mb-4">
        <p class="font-medium"><MemberName :identity="{ stationUid: '', memberUid: '', name: 'Max Mustermann' }"/></p>
        <p class="text-xs text-(--text-muted)">15.05.2026, 14:32</p>
      </div>
      <div class="space-y-4">
        <div class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50 pb-3">
          <p class="text-xs text-(--text-muted) mb-1">Was hat dir zuletzt besonders gut gefallen?</p>
          <p class="text-sm font-medium">Übungen</p>
        </div>
        <div class="pb-3">
          <p class="text-xs text-(--text-muted) mb-1">Wie zufrieden bist du insgesamt?</p>
          <p class="text-sm font-medium">4</p>
        </div>
      </div>
    </NeutralContainer>
  </div>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import IconButton from '@/components/button/IconButton.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-2">
    <NeutralContainer class="flex gap-2">
      <div class="flex-1 border border-dashed border-[var(--border)] rounded p-3 text-center text-sm text-[var(--text-muted)]">
        {{ t('stationPages.contentType.markdown') }}
      </div>
      <div class="flex-1 border border-dashed border-[var(--border)] rounded p-3 text-center text-sm text-[var(--text-muted)]">
        {{ t('stationPages.contentType.image') }}
      </div>
    </NeutralContainer>
    <div class="flex items-center justify-center gap-2 py-1">
      <div class="flex-1 h-px bg-[var(--border)]"/>
      <IconButton :icon="['fas', 'plus']" :label="t('stationPages.editor.addRowHere')"/>
      <div class="flex-1 h-px bg-[var(--border)]"/>
    </div>
    <NeutralContainer class="border border-dashed border-[var(--border)] rounded p-3 text-center text-sm text-[var(--text-muted)]">
      {{ t('stationPages.contentType.video') }}
    </NeutralContainer>
  </NeutralContainer>
</template>

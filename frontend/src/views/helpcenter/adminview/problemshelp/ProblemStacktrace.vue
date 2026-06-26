/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

defineProps<{ trace: string }>()

const {t} = useI18n()
</script>

<template>
  <div class="mt-3 pt-3 border-t border-[var(--border)]">
    <div class="flex items-center gap-2 mb-1">
      <SectionHeader class="!text-xs !mb-0">Stacktrace</SectionHeader>
      <IconButton :icon="['fas', 'copy']" :label="t('adminProblems.copyStacktrace')" class="text-(--text-muted)"/>
    </div>
    <pre class="text-xs font-mono bg-[var(--bg)] rounded p-2 overflow-x-auto max-h-32 whitespace-pre-wrap">{{ trace }}</pre>
  </div>
</template>

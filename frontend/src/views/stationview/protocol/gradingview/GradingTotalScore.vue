/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'

defineProps<{
  score: number
  maxPoints: number
}>()

const { t } = useI18n()
</script>

<template>
  <div class="flex items-center justify-between text-sm mb-4">
    <span class="text-[var(--text-muted)]">{{ t('protocol.totalScore') }}:</span>
    <span class="font-mono font-bold text-lg">{{ score }} / {{ maxPoints }}P</span>
  </div>
</template>

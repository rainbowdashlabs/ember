/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import Alert from '@/components/feedback/Alert.vue'
import MutedText from '@/components/typography/MutedText.vue'

/**
 * Says that a screen keeps what is changed on it without being asked to.
 *
 * <p>Worth saying out loud wherever there is also a save button for something else, because the
 * reader otherwise has to guess which half of the page the button covers. A failed write says so
 * loudly: it is the one case where walking away does lose the change.
 */
defineProps<{
  saving: boolean
  failed: boolean
  /** What is kept without asking, in the reader's words. */
  label: string
  savingLabel: string
  failedLabel: string
}>()
</script>

<template>
  <div class="space-y-2">
    <MutedText tag="p" size="sm">{{ saving ? savingLabel : label }}</MutedText>
    <Alert v-if="failed" variant="error">{{ failedLabel }}</Alert>
  </div>
</template>

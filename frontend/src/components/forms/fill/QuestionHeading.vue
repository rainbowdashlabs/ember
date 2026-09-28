/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import MutedText from '@/components/typography/MutedText.vue'

/**
 * What a question asks, as every screen a form is filled in on shows it: the title, a star where an
 * answer is required, the description where there is one, and what is wrong with the answer where
 * something is.
 */
defineProps<{
  title: string
  description?: string
  required: boolean
  error?: string
  /** A smaller title, for a form set into a page among other things. */
  compact?: boolean
}>()
</script>

<template>
  <div>
    <span class="font-medium" :class="compact ? 'text-sm' : ''">{{ title }}</span>
    <span v-if="required" class="ml-1 text-error">*</span>
    <MutedText v-if="description" tag="p" class="mt-0.5">{{ description }}</MutedText>
    <p v-if="error" class="mt-1 text-sm text-error" data-testid="question-error">{{ error }}</p>
  </div>
</template>

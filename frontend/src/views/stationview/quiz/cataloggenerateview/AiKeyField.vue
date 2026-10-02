/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {AiCredentialSummary} from '@/api/generated/schema'

/**
 * The field a new AI key is typed into, and what is known about the one already stored: its ending
 * where it is a key for the chosen provider, or that it no longer works.
 */
defineProps<{
  stored: AiCredentialSummary | null
  /** Whether the stored key is for the provider now chosen and still opens. */
  keptKey: boolean
}>()

const key = defineModel<string>({required: true})

const {t} = useI18n()
</script>

<template>
  <div>
    <FieldLabel hint class="mb-1">{{ t('quiz.ai.apiKey') }}</FieldLabel>
    <TextInput v-model="key" type="password" autocomplete="off" placeholder="sk-..."/>
    <MutedText v-if="keptKey" tag="p" class="mt-1">
      {{ t('quiz.ai.keyStoredEnding', {ending: stored?.keyEnding ?? ''}) }}
    </MutedText>
    <MutedText v-else-if="stored?.provider && !stored.usable" tag="p" class="mt-1">
      {{ t('quiz.ai.keyUnusable') }}
    </MutedText>
    <MutedText tag="p" class="mt-1">{{ t('quiz.ai.keyStoredHint') }}</MutedText>
  </div>
</template>

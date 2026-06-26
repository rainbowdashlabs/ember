/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'

const { t } = useI18n()

const config = defineModel<Record<string, unknown>>('config', {required: true})

function updateConfig(patch: Record<string, unknown>) {
  config.value = { ...config.value, ...patch }
}
</script>

<template>
  <TextInput :model-value="(config.answer as string)" :placeholder="t('quiz.questions.config.correctAnswer')" @update:model-value="(v: string | undefined) => updateConfig({ answer: v ?? '' })" />
</template>

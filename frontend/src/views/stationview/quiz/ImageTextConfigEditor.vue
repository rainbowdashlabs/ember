/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'

const { t } = useI18n()

const props = defineProps<{
  config: Record<string, unknown>
}>()

const emit = defineEmits<{
  'update:config': [value: Record<string, unknown>]
}>()

function updateConfig(patch: Record<string, unknown>) {
  emit('update:config', { ...props.config, ...patch })
}
</script>

<template>
  <TextInput :model-value="(config.answer as string)" :placeholder="t('quiz.questions.config.correctAnswer')" @update:model-value="(v: string | undefined) => updateConfig({ answer: v ?? '' })" />
</template>

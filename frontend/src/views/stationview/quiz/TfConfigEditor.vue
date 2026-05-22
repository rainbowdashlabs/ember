/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

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
  <label class="flex items-center gap-2 text-sm">
    <ToggleInput :model-value="(config.correctAnswer as boolean)" @update:model-value="(v: boolean) => updateConfig({ correctAnswer: v })" />
    {{ t('quiz.questions.config.correctAnswer') }}
  </label>
</template>

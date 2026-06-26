/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const { t } = useI18n()

const config = defineModel<Record<string, unknown>>('config', {required: true})

function updateConfig(patch: Record<string, unknown>) {
  config.value = { ...config.value, ...patch }
}
</script>

<template>
  <FieldLabel inline>
    <ToggleInput :model-value="(config.correctAnswer as boolean)" @update:model-value="(v: boolean) => updateConfig({ correctAnswer: v })" />
    {{ t('quiz.questions.config.correctAnswer') }}
  </FieldLabel>
</template>

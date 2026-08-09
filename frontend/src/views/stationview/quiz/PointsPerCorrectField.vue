/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import DecimalInput from '@/components/input/number/DecimalInput.vue'
import FieldHint from '@/components/typography/FieldHint.vue'

/**
 * How many points each correct part of a multi-part question scores. Half points are allowed, so
 * the step is 0.5 rather than 1, and an unset value scores one point per part.
 */
const points = defineModel<number>({required: true})

const {t} = useI18n()
</script>

<template>
  <div class="flex items-center gap-2">
    <FieldHint>{{ t('quiz.questions.config.pointsPerCorrect') }}</FieldHint>
    <DecimalInput :model-value="points" step="0.5" class="w-20"
                  @update:model-value="(v: number | undefined) => points = v ?? 1"/>
  </div>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import QuestionBoundsEditor from '@/components/input/questionsettings/QuestionBoundsEditor.vue'
import ToggleSetting from '@/components/input/toggle/ToggleSetting.vue'
import type {QuestionLimits} from '@/api/generated/schema'

/**
 * The lowest and highest number a number question takes, and whether it takes a fraction.
 */
const limits = defineModel<QuestionLimits>({required: true})

const {t} = useI18n()
</script>

<template>
  <div class="space-y-3">
    <QuestionBoundsEditor :min="limits.min" :max="limits.max"
                          @update:min="min => limits = {...limits, min: min ?? null}"
                          @update:max="max => limits = {...limits, max: max ?? null}"/>
    <ToggleSetting :model-value="limits.whole" :label="t('documentTemplates.questions.limits.whole')"
                   :hint="t('documentTemplates.questions.limits.wholeHint')"
                   @update:model-value="whole => limits = {...limits, whole: Boolean(whole)}"/>
  </div>
</template>

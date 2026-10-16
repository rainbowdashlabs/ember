/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import LabelledField from '@/components/input/LabelledField.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import {TextFormat, type QuestionLimits} from '@/api/generated/schema'

/**
 * The longest answer a text question takes and the format it asks for, either left open.
 */
const limits = defineModel<QuestionLimits>({required: true})

const {t} = useI18n()

const FORMATS: readonly TextFormat[] = Object.values(TextFormat)

function setMaxLength(value: unknown) {
  limits.value = {...limits.value, maxLength: value === '' || value === undefined ? null : Math.trunc(Number(value))}
}

function setFormat(value: string | number | null | undefined) {
  limits.value = {...limits.value, format: FORMATS.find(format => format === value) ?? null}
}
</script>

<template>
  <div class="grid gap-3 sm:grid-cols-2">
    <LabelledField :label="t('documentTemplates.questions.limits.maxLength')" :help="t('documentTemplates.questions.limits.openHelp')">
      <NumberInput :model-value="limits.maxLength ?? undefined" data-testid="question-max-length"
                   @update:model-value="setMaxLength"/>
    </LabelledField>
    <LabelledField :label="t('documentTemplates.questions.limits.format')">
      <SelectInput :model-value="limits.format ?? ''" class="w-full" data-testid="question-format"
                   @update:model-value="setFormat">
        <option value="">{{ t('documentTemplates.questions.limits.anyFormat') }}</option>
        <option v-for="format in FORMATS" :key="format" :value="format">{{ t(`documentTemplates.questions.limits.formatName.${format}`) }}</option>
      </SelectInput>
    </LabelledField>
  </div>
</template>

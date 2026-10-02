/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import LabelledField from '@/components/input/LabelledField.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import QuestionBoundsEditor from '@/components/input/questionsettings/QuestionBoundsEditor.vue'
import type {NumberConfig} from '@/api/generated/schema'

/**
 * The bounds and the step of a number on a piece of equipment, written the way every number field
 * writes them, and the unit, which is this feature's own.
 */
const config = defineModel<NumberConfig>('config', {required: true})

const {t} = useI18n()

function update(patch: Partial<NumberConfig>) {
    config.value = {...config.value, ...patch}
}
</script>

<template>
    <div class="flex flex-wrap items-start gap-4 mt-3">
        <QuestionBoundsEditor
            :max="config.max"
            :min="config.min"
            :step="config.step"
            with-step
            @update:max="max => update({max: max ?? null})"
            @update:min="min => update({min: min ?? null})"
            @update:step="step => update({step: step ?? null})"
        />
        <LabelledField :label="t('inventory.fields.number.unit')" class="w-32">
            <TextInput :model-value="config.unit" @update:model-value="unit => update({unit: String(unit ?? '')})"/>
        </LabelledField>
    </div>
</template>

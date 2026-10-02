/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import FieldAnswerInput from '@/components/input/FieldAnswerInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type {WaitingListField} from '@/api/generated/schema'

/**
 * One question of a waiting list with its name above it, wherever it is answered: on the station's
 * entry form, on an entry being corrected, behind an invitation and on the public sign-up page.
 *
 * <p>Those four places each kept a copy of this that differed in nothing but its name.
 */
const value = defineModel<string>({required: true})

defineProps<{
  field: WaitingListField
}>()
</script>

<template>
  <div class="space-y-1">
    <FieldLabel>
      {{ field.name }}
      <span v-if="field.required" class="text-error">*</span>
    </FieldLabel>
    <FieldAnswerInput
        v-model="value"
        :field-type="field.fieldType"
        :options="field.config?.options ?? []"
        :required="field.required"
    />
  </div>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import QuestionValueInput from '@/components/input/QuestionValueInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type {WaitingListField} from '@/api/waitingList'
import {QuestionKinds, questionKindOf} from '@/util/questions'

/** One question of a waiting list, on the entry as the station reads and corrects it. */
const value = defineModel<string>('value', {required: true})

const props = defineProps<{
  field: WaitingListField
}>()

const kind = computed(() => questionKindOf(props.field.fieldType) ?? QuestionKinds.TEXT)
</script>

<template>
  <div class="space-y-1">
    <FieldLabel>
      {{ field.name }}
      <span v-if="field.required" class="text-error text-xs ml-1">*</span>
    </FieldLabel>
    <QuestionValueInput
        v-model="value"
        :kind="kind"
        :options="field.config?.options ?? []"
        :required="field.required"
    />
  </div>
</template>

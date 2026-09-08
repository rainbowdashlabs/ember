/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import QuestionValueInput from '@/components/input/QuestionValueInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import {QuestionKinds, questionKindOf} from '@/util/questions'
import type { WaitingListField } from '@/api/waitingList'

const props = defineProps<{
  field: WaitingListField
}>()

const value = defineModel<string>('value', {required: true})

const kind = computed(() => questionKindOf(props.field.fieldType) ?? QuestionKinds.TEXT)
</script>

<template>
  <div class="space-y-1">
    <FieldLabel>
      {{ props.field.name }}
      <span v-if="props.field.required" class="text-error">*</span>
    </FieldLabel>

    <QuestionValueInput
      v-model="value"
      :kind="kind"
      :options="props.field.config?.options ?? []"
      :required="props.field.required"
    />

  </div>
</template>

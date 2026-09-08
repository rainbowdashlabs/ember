/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import QuestionValueInput from '@/components/input/QuestionValueInput.vue'
import type {WaitingListField} from '@/api/waitingList'
import {QuestionKinds, questionKindOf} from '@/util/questions'

/** One question of a waiting list, on the form somebody at the station fills in for a family. */
const props = defineProps<{
  field: WaitingListField
  value: string
}>()

const emit = defineEmits<{
  (e: 'update', value: string): void
}>()

const kind = computed(() => questionKindOf(props.field.fieldType) ?? QuestionKinds.TEXT)
</script>

<template>
  <QuestionValueInput
      :kind="kind"
      :model-value="value"
      :options="field.config?.options ?? []"
      :required="field.required"
      @update:model-value="emit('update', $event)"
  />
</template>

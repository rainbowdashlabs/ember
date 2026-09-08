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

/**
 * One question of a waiting list, on the page a family fills in without signing in.
 *
 * <p>The box is the one every other screen uses. This carries the label and hands the answer back,
 * because the public page keeps its own form state.
 */
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
  <div class="space-y-1">
    <FormLabel>{{ field.name }}{{ field.required ? ' *' : '' }}</FormLabel>
    <QuestionValueInput
        :kind="kind"
        :model-value="value"
        :options="field.config?.options ?? []"
        :required="field.required"
        @update:model-value="emit('update', $event)"
    />
  </div>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import QuestionValueDisplay from '@/components/display/QuestionValueDisplay.vue'
import type { WaitingListPublicStatus } from '@/api/generated/schema'

/** The answers a family gave, as the public status page shows them back: a yes as yes, a day as a day. */
const props = defineProps<{ status: WaitingListPublicStatus }>()

function answerOf(fieldId: number): unknown {
  return props.status.values?.find(value => value.fieldId === fieldId)?.value
}
</script>

<template>
  <div class="border-t border-bg-light-accent dark:border-bg-dark-accent pt-3 space-y-2">
    <div v-for="field in props.status.fields" :key="field.id" class="text-sm">
      <span class="text-(--text-muted)">{{ field.name }}:</span>
      <span class="ml-1 font-medium">
        <QuestionValueDisplay :field-type="field.fieldType" :value="answerOf(field.id)"/>
      </span>
    </div>
  </div>
</template>

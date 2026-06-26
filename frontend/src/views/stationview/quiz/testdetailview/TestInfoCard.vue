/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import TestInfoStats from './TestInfoStats.vue'
import TestInfoTimes from './TestInfoTimes.vue'
import type { QuizTestDetail } from '@/api/types'

const editStartAt = defineModel<string>('editStartAt', {required: true})
const editEndAt = defineModel<string>('editEndAt', {required: true})

defineProps<{
  test: QuizTestDetail['test']
  canConfigure: boolean
  timesDirty: boolean
  saveTimes: () => Promise<void>
}>()

defineEmits<{
  'mark-dirty': []
}>()
</script>

<template>
  <NeutralContainer>
    <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4 text-sm">
      <TestInfoStats :test="test" />
      <TestInfoTimes
          :test="test"
          :can-configure="canConfigure"
          v-model:edit-start-at="editStartAt"
          v-model:edit-end-at="editEndAt"
          @mark-dirty="$emit('mark-dirty')"
      />
    </div>
    <div v-if="timesDirty" class="flex justify-end mt-3">
      <SaveButton :action="saveTimes"/>
    </div>
  </NeutralContainer>
</template>

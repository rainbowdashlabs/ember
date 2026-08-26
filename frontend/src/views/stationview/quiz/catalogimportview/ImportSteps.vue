/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import ImportSourceStep from './ImportSourceStep.vue'
import ImportMappingStep from './ImportMappingStep.vue'
import ImportPreviewStep from './ImportPreviewStep.vue'
import ImportDoneStep from './ImportDoneStep.vue'
import type {useCatalogImport} from './useCatalogImport'

const props = defineProps<{
  wizard: ReturnType<typeof useCatalogImport>
  leaveLabel: string
}>()

const emit = defineEmits<{
  leave: []
}>()

const step = props.wizard
</script>

<template>
  <ImportSourceStep
      v-if="step.step.value === 'source'"
      v-model:separator="step.separator.value"
      v-model:ai-enabled="step.generateWrongAnswers.value"
      v-model:ai-count="step.wrongAnswerCount.value"
      v-model:ai-prompt="step.aiPrompt.value"
      :file-name="step.file.value?.name ?? ''"
      :is-sheet="step.isSheet.value"
      :loading="step.loading.value"
      :offer-ai="step.hasAiKey.value"
      @select="step.selectFile"
      @next="step.advanceFromSource"
  />

  <ImportMappingStep
      v-else-if="step.step.value === 'mapping'"
      v-model:mapping="step.mapping.value"
      :headers="step.headers.value"
      :loading="step.loading.value"
      @back="step.goBack"
      @advance="step.advanceFromMapping"
  />

  <ImportPreviewStep
      v-else-if="step.step.value === 'preview'"
      v-model:name="step.catalogName.value"
      v-model:description="step.catalogDescription.value"
      v-model:training-enabled="step.trainingEnabled.value"
      :drafts="step.drafts.value"
      :categories="step.categories.value"
      :appending="step.appending.value"
      :status="step.aiStatus.value"
      :loading="step.loading.value"
      @back="step.goBack"
      @commit="step.commit"
  />

  <ImportDoneStep
      v-else
      :count="step.includedCount.value"
      :leave-label="leaveLabel"
      @leave="emit('leave')"
      @start-over="step.startOver"
  />
</template>

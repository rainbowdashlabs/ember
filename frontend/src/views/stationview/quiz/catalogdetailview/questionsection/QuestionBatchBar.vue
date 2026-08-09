/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'

defineProps<{
  selectedCount: number
  hasMultipleChoice: boolean
}>()

const emit = defineEmits<{
  selectAll: []
  deselectAll: []
  action: [action: string]
}>()

const { t } = useI18n()
</script>

<template>
  <div class="flex items-center gap-2 flex-wrap mb-3 p-2 rounded bg-primary/10 border border-primary/30">
    <MutedText size="sm" class="font-medium">{{ selectedCount }} {{ t('quiz.batch.selected') }}</MutedText>
    <SecondaryButton compact @click="emit('selectAll')">{{ t('quiz.batch.selectAll') }}</SecondaryButton>
    <SecondaryButton compact @click="emit('deselectAll')">{{ t('quiz.batch.deselectAll') }}</SecondaryButton>
    <span class="border-l border-primary/30 h-4"/>
    <SecondaryButton compact @click="emit('action', 'autoPoints')">{{ t('quiz.batch.toggleAutoPoints') }}</SecondaryButton>
    <SecondaryButton compact @click="emit('action', 'setPoints')">{{ t('quiz.batch.setPoints') }}</SecondaryButton>
    <SecondaryButton compact v-if="hasMultipleChoice" @click="emit('action', 'pointsPerCorrect')">{{ t('quiz.batch.setPointsPerCorrect') }}</SecondaryButton>
    <SecondaryButton compact @click="emit('action', 'setCategory')">{{ t('quiz.batch.setCategory') }}</SecondaryButton>
    <SecondaryButton compact :icon="['fas', 'brain']" @click="emit('action', 'generate')">{{ t('quiz.batch.generate') }}</SecondaryButton>
  </div>
</template>

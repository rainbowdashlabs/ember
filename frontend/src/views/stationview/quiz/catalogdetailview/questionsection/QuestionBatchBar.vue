/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ActionsMenu from '@/components/button/ActionsMenu.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
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
  <!--
    Selecting and unselecting stay where they are: they say what the bar is acting on, which is the
    same reason a row of filters is never collapsed. What is done to the selection is a row of
    actions, and setting the points is the one it is opened for.
  -->
  <div class="flex items-center gap-2 flex-wrap mb-3 p-2 rounded bg-primary/10 border border-primary/30">
    <MutedText size="sm" class="font-medium">{{ selectedCount }} {{ t('quiz.batch.selected') }}</MutedText>
    <SecondaryButton compact @click="emit('selectAll')">{{ t('quiz.batch.selectAll') }}</SecondaryButton>
    <SecondaryButton compact @click="emit('deselectAll')">{{ t('quiz.batch.deselectAll') }}</SecondaryButton>
    <span class="border-l border-primary/30 h-4"/>
    <PrimaryButton compact @click="emit('action', 'setPoints')">{{ t('quiz.batch.setPoints') }}</PrimaryButton>
    <ActionsMenu :label="t('common.actions')" test-id="question-batch-actions">
      <DropdownMenuItem :icon="['fas', 'toggle-on']" @click="emit('action', 'autoPoints')">
        {{ t('quiz.batch.toggleAutoPoints') }}
      </DropdownMenuItem>
      <DropdownMenuItem v-if="hasMultipleChoice" :icon="['fas', 'check-double']"
                        @click="emit('action', 'pointsPerCorrect')">
        {{ t('quiz.batch.setPointsPerCorrect') }}
      </DropdownMenuItem>
      <DropdownMenuItem :icon="['fas', 'tag']" @click="emit('action', 'setCategory')">
        {{ t('quiz.batch.setCategory') }}
      </DropdownMenuItem>
      <DropdownMenuItem :icon="['fas', 'brain']" @click="emit('action', 'generate')">
        {{ t('quiz.batch.generate') }}
      </DropdownMenuItem>
    </ActionsMenu>
  </div>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import FloatingPanel from '@/components/feedback/FloatingPanel.vue'
import { useEditorActionsMenu } from '@/components/content/blockeditor/useEditorActionsMenu'
import type { FormQuestionType } from '@/api/generated/schema'

/**
 * Adds a question where the button stands, after asking which kind.
 *
 * <p>One button that opens the kinds rather than a row of six at the very end of the form: with pages,
 * a question belongs somewhere in particular, and reaching the bottom of a long form to add one and
 * then moving it up a step at a time was the slow way to put it there.
 */
defineProps<{
  questionTypes: FormQuestionType[]
}>()

const emit = defineEmits<{
  add: [type: FormQuestionType]
}>()

const { t } = useI18n()

const { open, toggle, close } = useEditorActionsMenu()

function add(type: FormQuestionType) {
  emit('add', type)
  close()
}
</script>

<template>
  <FloatingPanel v-model:open="open" :label="t('forms.addQuestion')" role="menu" align="start" panel-class="w-56 py-1"
                 class="inline-block">
    <template #trigger="{ triggerAttrs }">
      <SecondaryButton :icon="['fas', 'plus']" data-testid="add-question" v-bind="triggerAttrs" @click="toggle">
        {{ t('forms.addQuestion') }}
      </SecondaryButton>
    </template>
    <DropdownMenuItem v-for="type in questionTypes" :key="type" :icon="['fas', 'plus']" @click="add(type)">
      {{ t(`forms.questionTypes.${type}`) }}
    </DropdownMenuItem>
  </FloatingPanel>
</template>

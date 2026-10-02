/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
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

const { open, rootRef, close } = useEditorActionsMenu()

function toggle(event: MouseEvent) {
  event.stopPropagation()
  open.value = !open.value
}

function add(type: FormQuestionType) {
  emit('add', type)
  close()
}
</script>

<template>
  <div ref="rootRef" class="relative inline-block">
    <SecondaryButton :icon="['fas', 'plus']" data-testid="add-question" @click="toggle">
      {{ t('forms.addQuestion') }}
    </SecondaryButton>
    <div v-if="open" class="absolute left-0 top-full z-20 mt-1 w-56 rounded-theme border border-(--border) bg-(--bg) py-1 shadow-lg">
      <DropdownMenuItem v-for="type in questionTypes" :key="type" :icon="['fas', 'plus']" @click="add(type)">
        {{ t(`forms.questionTypes.${type}`) }}
      </DropdownMenuItem>
    </div>
  </div>
</template>

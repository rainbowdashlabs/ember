/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import {useEditorActionsMenu} from '@/components/content/blockeditor/useEditorActionsMenu'
import QuestionSettings from './QuestionSettings.vue'
import type {QuestionDraft} from '../types'
import type {PageChoice} from '../pageChoice'

/**
 * Everything about a question that is not its title, its content or whether it is required, behind
 * the button in the corner of its tile.
 *
 * <p>The button is always there, unlike the page editor's cell menu that waits for a hover: a form is
 * edited on a phone as often as anywhere, and a setting only reachable by hovering is not reachable
 * there at all. The menu stays open while its switches and numbers are changed and closes on a click
 * anywhere else, or once an action has been taken.
 */
defineProps<{
  /** Whether the question stands first, which leaves nothing above it to move past. */
  first: boolean
  /** Whether the question stands last, which leaves nothing below it to move past. */
  last: boolean
  /** Whether the description field is already shown, which makes offering it pointless. */
  describing: boolean
  /** The other pages of the form, which the question can be moved to. */
  otherPages: PageChoice[]
}>()

const question = defineModel<QuestionDraft>('question', {required: true})

const emit = defineEmits<{
  describe: []
  move: [direction: -1 | 1]
  moveToPage: [pageIndex: number]
  duplicate: []
  remove: []
}>()

const {t} = useI18n()

const {open, rootRef, toggle, close} = useEditorActionsMenu()

function take(action: () => void) {
  action()
  close()
}

/** Opens the menu from outside, which is what a chip under the title does. */
function show(event: MouseEvent) {
  event.stopPropagation()
  open.value = true
}

defineExpose({show})
</script>

<template>
  <div ref="rootRef" class="relative">
    <IconButton :icon="['fas', 'ellipsis']" :label="t('forms.questionMenu.open')" data-testid="question-menu-trigger"
                class="text-(--text-muted) hover:text-(--text)" @click="toggle"/>
    <div v-if="open" data-testid="question-menu"
         class="absolute right-0 top-full z-20 mt-1 w-80 max-w-[calc(100vw-2rem)] rounded-theme border border-(--border) bg-(--bg) py-1 shadow-lg">
      <QuestionSettings v-model:question="question"/>
      <DropdownMenuItem v-if="!describing" :icon="['fas', 'align-left']" @click="take(() => emit('describe'))">
        {{ t('forms.questionMenu.describe') }}
      </DropdownMenuItem>
      <DropdownMenuItem :icon="['fas', 'copy']" data-testid="question-duplicate" @click="take(() => emit('duplicate'))">
        {{ t('forms.questionMenu.duplicate') }}
      </DropdownMenuItem>
      <DropdownMenuItem :icon="['fas', 'chevron-up']" :disabled="first" @click="take(() => emit('move', -1))">
        {{ t('forms.questionMenu.moveUp') }}
      </DropdownMenuItem>
      <DropdownMenuItem :icon="['fas', 'chevron-down']" :disabled="last" @click="take(() => emit('move', 1))">
        {{ t('forms.questionMenu.moveDown') }}
      </DropdownMenuItem>
      <DropdownMenuItem v-for="page in otherPages" :key="page.index" :icon="['fas', 'arrow-right']"
                        @click="take(() => emit('moveToPage', page.index))">
        {{ t('forms.questionMenu.moveToPage', {page: page.label}) }}
      </DropdownMenuItem>
      <DropdownMenuItem :icon="['fas', 'trash']" destructive @click="take(() => emit('remove'))">
        {{ t('forms.questionMenu.remove') }}
      </DropdownMenuItem>
    </div>
  </div>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type { QuestionDraft } from './types'
import type { PageChoice } from './pageChoice'
import QuestionHeader from './QuestionHeader.vue'
import QuestionChips from './QuestionChips.vue'
import QuestionContent from './QuestionContent.vue'
import QuestionMenu from './questionmenu/QuestionMenu.vue'

/**
 * One question of the form being edited, as a tile.
 *
 * <p>What stays in sight is what every question needs: its kind, its title, its content and whether
 * it is required. A description shows once one has been written or asked for. Everything else has a
 * default and only refines the question, so it sits behind the menu in the corner, and whatever of it
 * differs from its default shows as a chip under the title.
 */
defineProps<{
  /** The number the question is shown with, counted across every page. */
  number: number
  first: boolean
  last: boolean
  /** The other pages it could be moved to, empty for a form of one page. */
  otherPages: PageChoice[]
}>()

const question = defineModel<QuestionDraft>('question', {required: true})

const emit = defineEmits<{
  move: [direction: -1 | 1]
  remove: []
  moveToPage: [pageIndex: number]
  duplicate: []
}>()

const { t } = useI18n()

const describing = ref(!!question.value.description)
const menu = ref<InstanceType<typeof QuestionMenu> | null>(null)
</script>

<template>
  <NeutralContainer>
    <div class="space-y-3" data-testid="question-tile">
      <QuestionHeader :question-type="question.questionType" :number="number">
        <QuestionMenu ref="menu" v-model:question="question" :first="first" :last="last" :other-pages="otherPages"
                      :describing="describing" @describe="describing = true"
                      @move="direction => emit('move', direction)" @remove="emit('remove')"
                      @move-to-page="pageIndex => emit('moveToPage', pageIndex)" @duplicate="emit('duplicate')"/>
      </QuestionHeader>

      <TextInput v-model="question.title" :placeholder="t('forms.questionTitle')" />
      <TextInput v-if="describing" v-model="question.description" :placeholder="t('forms.questionDescription')" />
      <QuestionChips :question="question" @open="menu?.show($event)"/>

      <QuestionContent v-model:question="question"/>

      <FieldLabel inline>
        <ToggleInput v-model="question.required" />
        {{ t('forms.questionRequired') }}
      </FieldLabel>
    </div>
  </NeutralContainer>
</template>

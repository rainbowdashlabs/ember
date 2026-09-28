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
const props = defineProps<{
  question: QuestionDraft
  index: number
  totalQuestions: number
}>()

const emit = defineEmits<{
  move: [index: number, direction: -1 | 1]
  remove: [index: number]
}>()

const { t } = useI18n()

const describing = ref(!!props.question.description)
const menu = ref<InstanceType<typeof QuestionMenu> | null>(null)
</script>

<template>
  <NeutralContainer>
    <div class="space-y-3" data-testid="question-tile">
      <QuestionHeader :question-type="question.questionType" :index="index">
        <QuestionMenu ref="menu" :question="question" :first="index === 0" :last="index === totalQuestions - 1"
                      :describing="describing" @describe="describing = true"
                      @move="direction => emit('move', index, direction)" @remove="emit('remove', index)"/>
      </QuestionHeader>

      <TextInput v-model="question.title" :placeholder="t('forms.questionTitle')" />
      <TextInput v-if="describing" v-model="question.description" :placeholder="t('forms.questionDescription')" />
      <QuestionChips :question="question" @open="menu?.show($event)"/>

      <QuestionContent :question="question"/>

      <FieldLabel inline>
        <ToggleInput v-model="question.required" />
        {{ t('forms.questionRequired') }}
      </FieldLabel>
    </div>
  </NeutralContainer>
</template>

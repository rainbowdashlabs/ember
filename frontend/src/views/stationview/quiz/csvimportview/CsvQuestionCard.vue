/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import AnswerSeparatorPicker from './AnswerSeparatorPicker.vue'
import {answerList, hasAnswerList, hasCorrectness, isCorrect, type ImportDraft} from './quizCsvImport'

const props = defineProps<{
  draft: ImportDraft
  index: number
  categoryName: string
}>()

const emit = defineEmits<{
  toggleInclude: [index: number]
  resplit: [index: number]
  setSeparator: [index: number, value: string]
  toggleCorrect: [index: number, optionIndex: number]
  updateAnswer: [index: number, optionIndex: number, value: string]
  removeAnswer: [index: number, optionIndex: number]
}>()

const {t} = useI18n()

const type = computed(() => props.draft.question.quizQuestionType)
const answers = computed(() => answerList(props.draft))
const editable = computed(() => hasAnswerList(type.value))
const markable = computed(() => hasCorrectness(type.value))
const splittable = computed(() => editable.value && props.draft.rawAnswer.length > 0)
</script>

<template>
  <NeutralContainer class="space-y-3 transition-opacity" :class="{'opacity-40': !draft.included}">
    <div class="flex items-start justify-between gap-2 flex-wrap">
      <div class="flex-1 min-w-0">
        <p class="font-medium text-sm truncate">{{ draft.question.title || t('quiz.csv.noTitle') }}</p>
        <div class="flex items-center gap-2 mt-0.5 flex-wrap">
          <span class="text-xs text-(--text-muted)">{{ t(`quiz.questionTypes.${type}`) }}</span>
          <span class="text-xs text-(--text-muted)">&bull;</span>
          <span class="text-xs text-(--text-muted)">{{ draft.question.points }} {{ t('quiz.points') }}</span>
          <span v-if="categoryName" class="text-xs text-(--text-muted)">&bull; {{ categoryName }}</span>
        </div>
      </div>
      <IconButton
        :icon="['fas', draft.included ? 'eye-slash' : 'eye']"
        :label="draft.included ? t('quiz.csv.exclude') : t('quiz.csv.include')"
        @click="emit('toggleInclude', index)"
      />
    </div>

    <template v-if="draft.included && editable">
      <div v-if="splittable" class="flex items-center gap-1 flex-wrap">
        <AnswerSeparatorPicker
          inline
          :separator="draft.answerSeparator"
          @update:separator="emit('setSeparator', index, $event)"
        />
        <SecondaryButton class="text-xs" @click="emit('resplit', index)">
          {{ t('quiz.csv.resplit') }}
        </SecondaryButton>
      </div>

      <div v-if="answers.length > 0" class="space-y-1">
        <div v-for="(answer, answerIndex) in answers" :key="answerIndex" class="flex items-center gap-2">
          <SelectionToggleButton
            v-if="markable"
            :selected="isCorrect(draft, answerIndex)"
            @toggle="emit('toggleCorrect', index, answerIndex)"
          >
            <font-awesome-icon :icon="['fas', isCorrect(draft, answerIndex) ? 'check' : 'xmark']" />
          </SelectionToggleButton>
          <TextInput
            :model-value="answer"
            class="flex-1 text-xs"
            @update:model-value="emit('updateAnswer', index, answerIndex, $event ?? '')"
          />
          <IconButton
            :icon="['fas', 'trash']"
            :label="t('common.delete')"
            class="text-error"
            @click="emit('removeAnswer', index, answerIndex)"
          />
        </div>
      </div>
      <div v-else class="text-xs text-(--text-muted) italic">
        {{ t('quiz.csv.noSplitItems') }}
      </div>
    </template>

    <p v-else-if="draft.included && draft.rawAnswer" class="text-xs text-(--text-muted)">
      {{ draft.rawAnswer }}
    </p>
  </NeutralContainer>
</template>

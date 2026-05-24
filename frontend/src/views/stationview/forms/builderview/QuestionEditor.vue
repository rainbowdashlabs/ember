/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FieldHint from '@/components/typography/FieldHint.vue'
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import { QuestionTypes } from '@/api/types'
import type { QuestionDraft } from './types'

const { t } = useI18n()

const props = defineProps<{
  question: QuestionDraft
  index: number
  totalQuestions: number
}>()

const emit = defineEmits<{
  move: [index: number, direction: -1 | 1]
  remove: [index: number]
}>()

const q = props.question

// CHOICE helpers
function setMultiSelect(val: boolean) {
  q.config.multiSelect = val
  if (val) q.config.dropdown = false
}

function setDropdown(val: boolean) {
  q.config.dropdown = val
  if (val) {
    q.config.multiSelect = false
    q.config.multiLimitType = 'NONE'
    q.config.multiLimit = null
  }
}

// Option helpers (shared for CHOICE and RANKING)
function addOption() {
  const opts = (q.config.options as string[]) || []
  opts.push('')
  q.config.options = opts
}

function removeOption(index: number) {
  const opts = (q.config.options as string[]) || []
  opts.splice(index, 1)
  q.config.options = opts
}

function updateOption(index: number, value: string) {
  const opts = [...((q.config.options as string[]) || [])]
  opts[index] = value
  q.config.options = opts
}

function moveOption(index: number, direction: -1 | 1) {
  const opts = (q.config.options as string[]) || []
  const newIdx = index + direction
  if (newIdx < 0 || newIdx >= opts.length) return
  const temp = opts[index]
  opts[index] = opts[newIdx]
  opts[newIdx] = temp
  q.config.options = [...opts]
}

// Likert helpers
function addStatement() {
  const stmts = (q.config.statements as string[]) || []
  stmts.push('')
  q.config.statements = stmts
}

function removeStatement(index: number) {
  const stmts = (q.config.statements as string[]) || []
  stmts.splice(index, 1)
  q.config.statements = stmts
}

function updateStatement(index: number, value: string) {
  const stmts = [...((q.config.statements as string[]) || [])]
  stmts[index] = value
  q.config.statements = stmts
}

function moveStatement(index: number, direction: -1 | 1) {
  const stmts = (q.config.statements as string[]) || []
  const newIdx = index + direction
  if (newIdx < 0 || newIdx >= stmts.length) return
  const temp = stmts[index]
  stmts[index] = stmts[newIdx]
  stmts[newIdx] = temp
  q.config.statements = [...stmts]
}
</script>

<template>
  <NeutralContainer>
    <div class="space-y-3">
      <div class="flex items-center justify-between">
        <span class="text-xs font-semibold text-(--text-muted) uppercase">
          {{ index + 1 }}. {{ t(`forms.questionTypes.${q.questionType}`) }}
        </span>
        <div class="flex gap-1">
          <IconButton :icon="['fas', 'chevron-up']" label="Up" :disabled="index === 0" class="text-(--text-muted) hover:text-primary" @click="emit('move', index, -1)" />
          <IconButton :icon="['fas', 'chevron-down']" label="Down" :disabled="index === totalQuestions - 1" class="text-(--text-muted) hover:text-primary" @click="emit('move', index, 1)" />
          <DeleteButton @click="emit('remove', index)" />
        </div>
      </div>

      <TextInput v-model="q.title" :placeholder="t('forms.questionTitle')" />
      <TextInput v-model="q.description" :placeholder="t('forms.questionDescription')" />

      <div class="flex gap-4">
        <label class="flex items-center gap-2 text-sm">
          <ToggleInput v-model="q.required" />
          {{ t('forms.questionRequired') }}
        </label>
        <label v-if="q.questionType === QuestionTypes.CHOICE || q.questionType === QuestionTypes.RANKING || q.questionType === QuestionTypes.LIKERT"
               class="flex items-center gap-2 text-sm">
          <ToggleInput v-model="q.shuffle" />
          {{ t('forms.questionShuffle') }}
        </label>
      </div>

      <!-- CHOICE -->
      <template v-if="q.questionType === QuestionTypes.CHOICE">
        <div class="flex gap-4 flex-wrap">
          <label class="flex items-center gap-2 text-sm">
            <ToggleInput :model-value="!!q.config.multiSelect" @update:model-value="setMultiSelect($event)" />
            {{ t('forms.choice.multiSelect') }}
          </label>
          <label class="flex items-center gap-2 text-sm">
            <ToggleInput :model-value="!!q.config.dropdown" @update:model-value="setDropdown($event)" />
            {{ t('forms.choice.dropdown') }}
          </label>
          <label class="flex items-center gap-2 text-sm">
            <ToggleInput v-model="(q.config.allowOther as boolean)" />
            {{ t('forms.choice.allowOther') }}
          </label>
        </div>
        <div v-if="q.config.multiSelect" class="flex gap-4 items-center">
          <SelectInput v-model="(q.config.multiLimitType as string)" class="w-40">
            <option value="NONE">{{ t('forms.choice.limitNone') }}</option>
            <option value="EQUAL_TO">{{ t('forms.choice.limitEqual') }}</option>
            <option value="AT_MOST">{{ t('forms.choice.limitAtMost') }}</option>
            <option value="AT_LEAST">{{ t('forms.choice.limitAtLeast') }}</option>
          </SelectInput>
          <NumberInput v-if="q.config.multiLimitType !== 'NONE'" v-model="(q.config.multiLimit as number)" :placeholder="t('forms.choice.limitValue')" class="w-24" />
        </div>
        <div class="space-y-1">
          <FieldHint>{{ t('forms.choice.options') }}</FieldHint>
          <div v-for="(opt, oi) in (q.config.options as string[])" :key="oi" class="flex gap-2 items-center">
            <TextInput :model-value="opt" class="flex-1" @update:model-value="(v: string | undefined) => updateOption(oi, v ?? '')" />
            <IconButton :icon="['fas', 'chevron-up']" label="Up" class="text-(--text-muted) hover:text-primary" @click="moveOption(oi, -1)" />
            <IconButton :icon="['fas', 'chevron-down']" label="Down" class="text-(--text-muted) hover:text-primary" @click="moveOption(oi, 1)" />
            <DeleteButton @click="removeOption(oi)" />
          </div>
          <SecondaryButton @click="addOption">{{ t('forms.choice.addOption') }}</SecondaryButton>
        </div>
      </template>

      <!-- TEXT -->
      <template v-if="q.questionType === QuestionTypes.TEXT">
        <label class="flex items-center gap-2 text-sm">
          <ToggleInput v-model="(q.config.longAnswer as boolean)" />
          {{ t('forms.text.longAnswer') }}
        </label>
      </template>

      <!-- RATING -->
      <template v-if="q.questionType === QuestionTypes.RATING">
        <div class="flex gap-4 items-center">
          <label class="text-sm">{{ t('forms.rating.scale') }}</label>
          <NumberInput v-model="(q.config.scale as number)" class="w-20" />
          <SelectInput v-model="(q.config.icon as string)" class="w-40">
            <option value="STAR">{{ t('forms.rating.iconStar') }}</option>
            <option value="NUMBER">{{ t('forms.rating.iconNumber') }}</option>
            <option value="HEART">{{ t('forms.rating.iconHeart') }}</option>
            <option value="THUMB_UP">{{ t('forms.rating.iconThumbUp') }}</option>
          </SelectInput>
        </div>
      </template>

      <!-- RANKING -->
      <template v-if="q.questionType === QuestionTypes.RANKING">
        <div class="space-y-1">
          <FieldHint>{{ t('forms.ranking.options') }}</FieldHint>
          <div v-for="(opt, oi) in (q.config.options as string[])" :key="oi" class="flex gap-2 items-center">
            <TextInput :model-value="opt" class="flex-1" @update:model-value="(v: string | undefined) => updateOption(oi, v ?? '')" />
            <IconButton :icon="['fas', 'chevron-up']" label="Up" class="text-(--text-muted) hover:text-primary" @click="moveOption(oi, -1)" />
            <IconButton :icon="['fas', 'chevron-down']" label="Down" class="text-(--text-muted) hover:text-primary" @click="moveOption(oi, 1)" />
            <DeleteButton @click="removeOption(oi)" />
          </div>
          <SecondaryButton @click="addOption">{{ t('forms.ranking.addOption') }}</SecondaryButton>
        </div>
      </template>

      <!-- LIKERT -->
      <template v-if="q.questionType === QuestionTypes.LIKERT">
        <div class="flex gap-4 items-center">
          <label class="text-sm">{{ t('forms.likert.scaleMin') }}</label>
          <NumberInput v-model="(q.config.scaleMin as number)" class="w-20" />
          <label class="text-sm">{{ t('forms.likert.scaleMax') }}</label>
          <NumberInput v-model="(q.config.scaleMax as number)" class="w-20" />
        </div>
        <div class="space-y-1">
          <FieldHint>{{ t('forms.likert.statements') }}</FieldHint>
          <div v-for="(stmt, si) in (q.config.statements as string[])" :key="si" class="flex gap-2 items-center">
            <TextInput :model-value="stmt" class="flex-1" @update:model-value="(v: string | undefined) => updateStatement(si, v ?? '')" />
            <IconButton :icon="['fas', 'chevron-up']" label="Up" class="text-(--text-muted) hover:text-primary" @click="moveStatement(si, -1)" />
            <IconButton :icon="['fas', 'chevron-down']" label="Down" class="text-(--text-muted) hover:text-primary" @click="moveStatement(si, 1)" />
            <DeleteButton @click="removeStatement(si)" />
          </div>
          <SecondaryButton @click="addStatement">{{ t('forms.likert.addStatement') }}</SecondaryButton>
        </div>
      </template>
    </div>
  </NeutralContainer>
</template>

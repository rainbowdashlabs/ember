/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import GenderAnswerPronouns from './GenderAnswerPronouns.vue'
import type {AnswerPronouns, GenderPronouns} from './genderPronouns'

/**
 * The pronouns every answer of a gender field stands for, which documents write for the member. A field
 * turned from a choice keeps its answers; each is mapped here once.
 */
const pronouns = defineModel<GenderPronouns>({required: true})

defineProps<{
  answers: readonly string[]
}>()

const {t} = useI18n()

function setAnswer(answer: string, value: AnswerPronouns | undefined) {
  const others = Object.fromEntries(Object.entries(pronouns.value).filter(([key]) => key !== answer))
  pronouns.value = value ? {...others, [answer]: value} : others
}
</script>

<template>
  <div class="space-y-3" data-testid="gender-pronouns">
    <SubHeader>{{ t('membersConfig.gender.title') }}</SubHeader>
    <MutedText size="sm" tag="p">{{ t('membersConfig.gender.hint') }}</MutedText>
    <GenderAnswerPronouns v-for="answer in answers" :key="answer" :answer="answer" :model-value="pronouns[answer]"
                          @update:model-value="value => setAnswer(answer, value)"/>
  </div>
</template>

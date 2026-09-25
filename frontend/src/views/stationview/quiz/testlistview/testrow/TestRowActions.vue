/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import {QuizTestStatus, type QuizTest} from '@/api/quiz'

/** What can be done with a test sheet from the list, which is the same on a phone and on a desktop. */
const props = defineProps<{
  test: QuizTest
  canConfigure: boolean
  canReadResults: boolean
  submitted: boolean
}>()

const emit = defineEmits<{
  take: [test: QuizTest]
  edit: [test: QuizTest]
  remove: [test: QuizTest]
}>()

const {t} = useI18n()
</script>

<template>
  <PrimaryButton
    v-if="props.test.status === QuizTestStatus.ACTIVE && !props.canReadResults && !props.submitted"
    @click="emit('take', props.test)"
  >
    {{ t('quiz.tests.takeTest') }}
  </PrimaryButton>
  <template v-if="props.canConfigure">
    <SecondaryButton @click="emit('edit', props.test)">{{ t('common.edit') }}</SecondaryButton>
    <ErrorButton @click="emit('remove', props.test)">{{ t('common.delete') }}</ErrorButton>
  </template>
</template>

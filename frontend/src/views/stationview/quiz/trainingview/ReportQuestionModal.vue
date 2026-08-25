/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import Alert from '@/components/feedback/Alert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {quiz} from '@/api'

const open = defineModel<boolean>({required: true})

const props = defineProps<{
  questionId: number
  questionTitle: string
}>()

const {t} = useI18n()

const note = ref('')
const saving = ref(false)
const error = ref('')
const sent = ref(false)

watch(open, isOpen => {
  if (!isOpen) return
  note.value = ''
  error.value = ''
  sent.value = false
})

async function submit() {
  if (!note.value.trim()) return
  saving.value = true
  error.value = ''
  try {
    await quiz.reportQuestion(props.questionId, note.value.trim())
    sent.value = true
  } catch {
    error.value = t('common.error')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <Modal v-model="open">
    <div class="space-y-4">
      <SubHeader>{{ t('quiz.report.title') }}</SubHeader>

      <template v-if="!sent">
        <MutedText class="block text-sm">{{ questionTitle }}</MutedText>
        <MutedText class="block text-xs">{{ t('quiz.report.hint') }}</MutedText>
        <TextAreaInput v-model="note" :placeholder="t('quiz.report.placeholder')" :rows="5" />
        <Alert v-if="error" variant="error">{{ error }}</Alert>
        <div class="flex justify-end gap-3">
          <SecondaryButton @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton :disabled="!note.trim() || saving" :icon="['fas', 'flag']" @click="submit">
            {{ saving ? t('common.loading') : t('quiz.report.submit') }}
          </PrimaryButton>
        </div>
      </template>

      <template v-else>
        <MutedText class="block text-sm">{{ t('quiz.report.thanks') }}</MutedText>
        <div class="flex justify-end">
          <PrimaryButton @click="open = false">{{ t('common.close') }}</PrimaryButton>
        </div>
      </template>
    </div>
  </Modal>
</template>

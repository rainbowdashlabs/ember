/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import EmptyHint from '@/components/typography/EmptyHint.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import MailFallbackRow from '@/components/mail/MailFallbackRow.vue'
import type {MailFallback} from '@/api/mailFallbacks'

/**
 * The order mail is tried through, for whoever owns the chain.
 *
 * The same screen serves the instance and a station, because the question is the same one: which
 * provider first, how many attempts before giving up on it, and who takes over then. The first
 * provider is not edited here - it is the one already configured on the page above - so this list
 * starts at the second.
 */
const props = defineProps<{
  /** Whether the owner may also set how many attempts the first provider gets. */
  showPrimaryAttempts?: boolean
  save: () => Promise<unknown>
}>()

const fallbacks = defineModel<MailFallback[]>('fallbacks', {required: true})
const primaryAttempts = defineModel<number>('primaryAttempts', {default: 2})

const {t} = useI18n()

function add() {
  fallbacks.value = [...fallbacks.value, {
    provider: 'SMTP',
    smtpHost: '',
    smtpPort: 587,
    smtpSsl: false,
    smtpUser: '',
    smtpPassword: '',
    apiKey: '',
    senderAddress: '',
    senderName: '',
    attempts: 2,
  }]
}

function replaceAt(index: number, value: MailFallback) {
  fallbacks.value = fallbacks.value.map((entry, i) => (i === index ? value : entry))
}

function remove(index: number) {
  fallbacks.value = fallbacks.value.filter((_, i) => i !== index)
}

function move(index: number, direction: number) {
  const target = index + direction
  if (target < 0 || target >= fallbacks.value.length) return
  const next = [...fallbacks.value]
  const [moved] = next.splice(index, 1)
  if (!moved) return
  next.splice(target, 0, moved)
  fallbacks.value = next
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('mailFallbacks.title') }}</SectionHeader>
    <MutedText tag="p" size="sm">{{ t('mailFallbacks.hint') }}</MutedText>

    <div v-if="props.showPrimaryAttempts" class="space-y-1 max-w-xs">
      <FieldLabel>{{ t('mailFallbacks.primaryAttempts') }}</FieldLabel>
      <NumberInput v-model="primaryAttempts" :min="1" :max="10"/>
      <MutedText tag="p" size="sm">{{ t('mailFallbacks.primaryAttemptsHint') }}</MutedText>
    </div>

    <EmptyHint v-if="fallbacks.length === 0">{{ t('mailFallbacks.empty') }}</EmptyHint>

    <MailFallbackRow
        v-for="(entry, index) in fallbacks"
        :key="index"
        :model-value="entry"
        @update:model-value="(value: MailFallback) => replaceAt(index, value)"
        :position="index + 2"
        :is-first="index === 0"
        :is-last="index === fallbacks.length - 1"
        @remove="remove(index)"
        @move="(direction: number) => move(index, direction)"
    />

    <div class="flex justify-between gap-2 flex-wrap border-t border-(--border) pt-4">
      <SecondaryButton :icon="['fas', 'plus']" @click="add">{{ t('mailFallbacks.add') }}</SecondaryButton>
      <SaveButton :action="props.save"/>
    </div>
  </NeutralContainer>
</template>

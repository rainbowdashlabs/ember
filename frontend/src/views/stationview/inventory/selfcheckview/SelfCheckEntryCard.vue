/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Alert from '@/components/feedback/Alert.vue'
import {answersFor, borrowed, recordedLost, type SelfCheckDraft, type SelfCheckEntry} from '@/composables/useSelfCheck'
import {SelfCheckAnswer, type SelfCheckAnswerName} from '@/api/selfChecks'

/**
 * One thing the member is asked about, and everything they may say about it.
 *
 * <p>Two of the answers are not answers at all: saying a piece cannot be found and asking for
 * another size both take effect the moment they are given, through the screens that already accept
 * them, so they are offered as the acts they are rather than as boxes to tick.
 */
const props = defineProps<{
  entry: SelfCheckEntry
  draft: SelfCheckDraft
  sizeLabel: string
  /** What the member already set going about this piece, so it is not raised twice by accident. */
  raised: string[]
  /** Why this answer came back from the reviewer, empty where it did not. */
  refusedReason: string
  readOnly: boolean
}>()

const emit = defineEmits<{
  setAnswer: [key: string, answer: SelfCheckAnswerName]
  setNote: [key: string, note: string]
  setTypedInternalId: [key: string, typed: string]
  reportLost: [entry: SelfCheckEntry]
  requestExchange: [entry: SelfCheckEntry]
}>()

const {t} = useI18n()

const answers = computed(() => answersFor(props.entry))
const piece = computed(() => (props.entry.type === 'piece' ? props.entry.item : null))
const title = computed(() =>
  piece.value ? (piece.value.name ?? props.entry.req.inventoryName) : t('selfCheck.emptyPlace'),
)

/** A loss is a member's own to state, but never about a piece a partner owns or one already gone. */
const mayReportLost = computed(
  () => piece.value != null && !borrowed(piece.value) && !recordedLost(piece.value) && !props.readOnly,
)

/** An exchange needs an inventory holding one thing in many copies, and a piece the station owns. */
const mayRequestExchange = computed(
  () => piece.value != null && !borrowed(piece.value) && !recordedLost(piece.value) && props.entry.req.homogeneous && !props.readOnly,
)

const lossRaised = computed(() => props.raised.includes('LOSS'))
const exchangeRaised = computed(() => props.raised.includes('EXCHANGE'))

function answerLabel(answer: SelfCheckAnswerName): string {
  return t(`selfCheck.answer.${answer}`)
}
</script>

<template>
  <div
      class="rounded border border-bg-light-accent/50 dark:border-bg-dark-accent/50 p-3 space-y-2 transition-all"
      :class="draft.answer ? 'ring-2 ring-success bg-success/10' : ''"
      :data-testid="`self-check-entry-${entry.key}`"
  >
    <div class="flex flex-col sm:flex-row sm:items-start gap-2">
      <div class="flex-1 min-w-0">
        <div class="font-medium text-sm truncate">
          <span v-if="entry.total > 1" class="text-(--text-muted) tabular-nums">{{ entry.position }}/{{ entry.total }}</span>
          {{ title }}
          <SizeBadge v-if="sizeLabel">{{ sizeLabel }}</SizeBadge>
          <InfoBadge v-if="piece && borrowed(piece)">{{ t('selfCheck.borrowed') }}</InfoBadge>
          <ErrorBadge v-if="piece && recordedLost(piece)">{{ t('selfCheck.recordedLost') }}</ErrorBadge>
        </div>
        <MutedText v-if="piece?.internalId" size="xs">{{ piece.internalId }}</MutedText>
        <MutedText v-else-if="entry.type === 'place'" size="xs">{{ entry.req.inventoryName }}</MutedText>
      </div>
    </div>

    <Alert v-if="refusedReason" variant="error" :data-testid="`self-check-refused-${entry.key}`">
      {{ t('selfCheck.cameBack', {reason: refusedReason}) }}
    </Alert>

    <div class="flex flex-wrap gap-1">
      <SecondaryButton
          v-for="answer in answers"
          :key="answer"
          class="text-xs px-3 py-1.5"
          :class="draft.answer === answer ? 'ring-2 ring-primary' : 'opacity-70'"
          :disabled="readOnly"
          :data-testid="`self-check-answer-${entry.key}-${answer}`"
          @click="emit('setAnswer', entry.key, answer)"
      >
        {{ answerLabel(answer) }}
      </SecondaryButton>
    </div>

    <div v-if="mayReportLost || mayRequestExchange" class="flex flex-wrap gap-1">
      <PrimaryButton
          v-if="mayReportLost"
          class="text-xs px-3 py-1.5"
          :disabled="lossRaised"
          :data-testid="`self-check-lost-${entry.key}`"
          @click="emit('reportLost', entry)"
      >
        {{ lossRaised ? t('selfCheck.lossRaised') : t('selfCheck.reportLost') }}
      </PrimaryButton>
      <PrimaryButton
          v-if="mayRequestExchange"
          class="text-xs px-3 py-1.5"
          :disabled="exchangeRaised"
          :data-testid="`self-check-exchange-${entry.key}`"
          @click="emit('requestExchange', entry)"
      >
        {{ exchangeRaised ? t('selfCheck.exchangeRaised') : t('selfCheck.requestExchange') }}
      </PrimaryButton>
      <MutedText size="xs" class="w-full">{{ t('selfCheck.raisedAtOnce') }}</MutedText>
    </div>

    <TextInput
        v-if="entry.type === 'place' && draft.answer === SelfCheckAnswer.HAVE_ONE"
        :model-value="draft.typedInternalId"
        :placeholder="t('selfCheck.identifierPlaceholder')"
        :disabled="readOnly"
        :data-testid="`self-check-identifier-${entry.key}`"
        @update:model-value="emit('setTypedInternalId', entry.key, ($event as string) ?? '')"
    />

    <TextInput
        :model-value="draft.note"
        :placeholder="t('selfCheck.notePlaceholder')"
        :disabled="readOnly"
        :data-testid="`self-check-note-${entry.key}`"
        @update:model-value="emit('setNote', entry.key, ($event as string) ?? '')"
    />
  </div>
</template>

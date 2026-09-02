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
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import {
  answersFor,
  borrowed,
  ExchangeCause,
  recordedLost,
  type ExchangeCauseName,
  type SelfCheckDraft,
  type SelfCheckEntry,
} from '@/composables/useSelfCheck'
import {SelfCheckAnswer, type SelfCheckAnswerName} from '@/api/selfChecks'

/**
 * One thing the member is asked about, and everything they may say about it.
 *
 * <p>Three of the answers are not answers at all: saying a piece cannot be found, saying it no
 * longer fits and saying it is broken all take effect the moment they are given, through the screens
 * that already accept them, so they are offered as the acts they are rather than as boxes to tick.
 * The last two are one exchange raised for two different reasons.
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
  setSizeId: [key: string, sizeId: string]
  reportLost: [entry: SelfCheckEntry]
  requestExchange: [entry: SelfCheckEntry, cause: ExchangeCauseName]
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

const sizes = computed(() => props.entry.req.sizes)

const keepsSizes = computed(() => props.entry.req.hasSizes && sizes.value.length > 0)

/**
 * Whether the member may name the size of a piece nobody wrote down. Offered and never demanded:
 * they get through without it, and the station reads that they did not say.
 */
const mayGiveASize = computed(
  () => props.entry.type === 'place' && props.draft.answer === SelfCheckAnswer.HAVE_ONE && keepsSizes.value,
)

/**
 * Whether the member may put the size of a recorded piece right, which is offered wherever saying
 * the record is wrong is offered at all: a piece already written off asks one question, and a
 * partner's gear is not this station's record to correct.
 */
const mayCorrectTheSize = computed(
  () => piece.value != null && keepsSizes.value && answers.value.includes(SelfCheckAnswer.WRONG_RECORD),
)

/** The size the station has written down, which is where the member starts from. */
const recordedSize = computed(() => (piece.value?.sizeId == null ? '' : String(piece.value.sizeId)))

/**
 * What the size box shows: what the member has said, or what the record says while they have said
 * nothing.
 */
const shownSize = computed(() => props.draft.sizeId || recordedSize.value)

/**
 * Moving the size away from the recorded one is the member saying the record is wrong, so it is
 * taken as that answer rather than asked for a second time. Putting it back where it was says
 * nothing, and leaves whatever answer they gave standing.
 */
function chooseActualSize(chosen: string) {
  const differs = chosen !== recordedSize.value
  emit('setSizeId', props.entry.key, differs ? chosen : '')
  if (differs) emit('setAnswer', props.entry.key, SelfCheckAnswer.WRONG_RECORD)
}

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
        <MutedText v-if="piece?.internalId" size="xs" tag="p">{{ piece.internalId }}</MutedText>
        <MutedText v-else-if="entry.type === 'place'" size="xs" tag="p">{{ entry.req.inventoryName }}</MutedText>
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
          @click="emit('requestExchange', entry, ExchangeCause.DOES_NOT_FIT)"
      >
        {{ exchangeRaised ? t('selfCheck.exchangeRaised') : t('selfCheck.requestExchange') }}
      </PrimaryButton>
      <PrimaryButton
          v-if="mayRequestExchange"
          class="text-xs px-3 py-1.5"
          :disabled="exchangeRaised"
          :data-testid="`self-check-broken-${entry.key}`"
          @click="emit('requestExchange', entry, ExchangeCause.BROKEN)"
      >
        {{ exchangeRaised ? t('selfCheck.exchangeRaised') : t('selfCheck.reportBroken') }}
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

    <SelectInput
        v-if="mayGiveASize"
        :model-value="draft.sizeId"
        :disabled="readOnly"
        class="w-full"
        :data-testid="`self-check-size-${entry.key}`"
        @update:model-value="emit('setSizeId', entry.key, ($event as string) ?? '')"
    >
      <option value="">{{ t('selfCheck.sizeUnknown') }}</option>
      <option v-for="size in sizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
    </SelectInput>

    <div v-if="mayCorrectTheSize" class="space-y-1">
      <FieldLabel>{{ t('selfCheck.actualSize') }}</FieldLabel>
      <SelectInput
          :model-value="shownSize"
          :disabled="readOnly"
          class="w-full"
          :data-testid="`self-check-actual-size-${entry.key}`"
          @update:model-value="chooseActualSize(($event as string) ?? '')"
      >
        <option value="">{{ t('selfCheck.sizeUnknown') }}</option>
        <option v-for="size in sizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
      </SelectInput>
    </div>

    <TextInput
        :model-value="draft.note"
        :placeholder="t('selfCheck.notePlaceholder')"
        :disabled="readOnly"
        :data-testid="`self-check-note-${entry.key}`"
        @update:model-value="emit('setNote', entry.key, ($event as string) ?? '')"
    />
  </div>
</template>

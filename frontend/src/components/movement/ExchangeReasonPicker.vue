/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {InventorySize} from '@/api/generated/schema'
import {
  EXCHANGE_REASONS,
  ExchangeReason,
  type ExchangeReasonName,
  guessSize,
  reasonText,
  runsOffTheEnd,
  sizeRelation,
} from './exchangeReason'

/**
 * Why a piece is swapped and which size comes back, asked the same way wherever a swap is started.
 *
 * <p>Too small, too large and broken are buttons, because they are what nearly every swap is, and each
 * sets the wanted size: one up, one down, the same. Anything else is said in words. A note can follow a
 * button too. The size the piece has now stands next to the wanted one, with how the two relate, so the
 * reader sees the step at a glance and corrects the guess where it is wrong.
 *
 * <p>{@code text} is what goes on the movement as its reason, and is empty until a reason is complete,
 * which is how a caller knows it may submit.
 */
const props = defineProps<{
  /** The sizes the inventory keeps, smallest first. */
  sizes: InventorySize[]
  currentSizeId?: number | null
  /** Prefixes the test ids, so two screens can tell their pickers apart. */
  testId?: string
}>()

const newSizeId = defineModel<number | null>('newSizeId', {required: true})
const text = defineModel<string>('text', {required: true})

const {t} = useI18n()

const reason = ref<ExchangeReasonName | null>(null)
const note = ref('')

const id = computed(() => props.testId ?? 'exchange')

const currentLabel = computed(() =>
    props.sizes.find(size => size.id === props.currentSizeId)?.label ?? t('exchangeReason.noSize'))

const sizeChoice = computed({
  get: () => (newSizeId.value != null ? String(newSizeId.value) : ''),
  set: value => {
    newSizeId.value = value ? Number(value) : null
  },
})

const relation = computed(() => sizeRelation(props.sizes, props.currentSizeId, newSizeId.value))
const offTheEnd = computed(() => reason.value !== null && runsOffTheEnd(reason.value, props.sizes, props.currentSizeId))

function choose(chosen: ExchangeReasonName) {
  reason.value = chosen
  newSizeId.value = guessSize(chosen, props.sizes, props.currentSizeId, newSizeId.value)
}

function label(of: ExchangeReasonName): string {
  return t(`exchangeReason.reasons.${of}`)
}

watch([reason, note], () => {
  text.value = reason.value === null ? '' : reasonText(reason.value, label(reason.value), note.value)
}, {immediate: true})
</script>

<template>
  <div class="space-y-3">
    <div class="space-y-1">
      <FieldLabel>{{ t('exchangeReason.reason') }}</FieldLabel>
      <ButtonRow>
        <SecondaryButton
            v-for="option in EXCHANGE_REASONS"
            :key="option"
            :class="{'ring-2 ring-(--color-primary)': reason === option}"
            :aria-pressed="reason === option"
            :data-testid="`${id}-reason-${option.toLowerCase().replace('_', '-')}`"
            @click="choose(option)"
        >
          {{ label(option) }}
        </SecondaryButton>
      </ButtonRow>
    </div>

    <div v-if="reason !== null" class="space-y-1">
      <FieldLabel>
        {{ reason === ExchangeReason.OTHER ? t('exchangeReason.ownReason') : t('exchangeReason.note') }}
      </FieldLabel>
      <TextInput v-model="note" class="w-full" :data-testid="`${id}-note`"/>
    </div>

    <div v-if="props.sizes.length > 0" class="space-y-1">
      <FieldLabel>{{ t('exchangeReason.newSize') }}</FieldLabel>
      <div class="flex flex-wrap items-center gap-2 text-sm">
        <span>{{ t('exchangeReason.currentSize') }}</span>
        <SizeBadge :data-testid="`${id}-current-size`">{{ currentLabel }}</SizeBadge>
      </div>
      <SelectInput v-model="sizeChoice" class="w-full" :data-testid="`${id}-new-size`">
        <option value="">{{ t('exchangeReason.noSize') }}</option>
        <option v-for="size in props.sizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
      </SelectInput>
      <MutedText v-if="offTheEnd" size="xs" tag="p" :data-testid="`${id}-off-the-end`">
        {{ reason === ExchangeReason.TOO_SMALL ? t('exchangeReason.noLarger') : t('exchangeReason.noSmaller') }}
      </MutedText>
      <MutedText v-else-if="relation !== 'unknown'" size="xs" tag="p" :data-testid="`${id}-relation`">
        {{ t(`exchangeReason.relation.${relation}`) }}
      </MutedText>
    </div>
  </div>
</template>

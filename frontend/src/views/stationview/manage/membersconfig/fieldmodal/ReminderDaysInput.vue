/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import IconButton from '@/components/button/IconButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import MutedText from '@/components/typography/MutedText.vue'

/**
 * The days before an expiry date on which a reminder goes out, furthest first, each once.
 *
 * <p>A set of numbers rather than a list the reader orders: the order is the calendar's, so a day is
 * added or taken away and never moved.
 */
const days = defineModel<number[]>({required: true})

const {t} = useI18n()

const next = ref<number | undefined>(undefined)

const furthestFirst = computed(() => [...days.value].sort((a, b) => b - a))

const canAdd = computed(() =>
    typeof next.value === 'number' && Number.isInteger(next.value) && next.value >= 0
    && !days.value.includes(next.value))

function add() {
  if (!canAdd.value) return
  days.value = [...days.value, next.value as number]
  next.value = undefined
}

function remove(day: number) {
  days.value = days.value.filter(held => held !== day)
}

function wordsOf(day: number): string {
  return day === 0 ? t('membersConfig.expiry.onTheDay') : t('membersConfig.expiry.daysBefore', {days: day})
}
</script>

<template>
  <div class="space-y-2">
    <div class="flex flex-wrap items-center gap-1" data-testid="reminder-days">
      <SecondaryBadge v-for="day in furthestFirst" :key="day" class="inline-flex items-center gap-1">
        {{ wordsOf(day) }}
        <IconButton :icon="['fas', 'xmark']" :label="t('membersConfig.expiry.removeDay')" class="p-0!"
                    @click="remove(day)"/>
      </SecondaryBadge>
      <MutedText v-if="furthestFirst.length === 0" class="text-xs">{{ t('membersConfig.expiry.noReminders') }}</MutedText>
    </div>
    <div class="flex items-center gap-2">
      <NumberInput v-model="next" class="w-28" data-testid="reminder-day-input"
                   :placeholder="t('membersConfig.expiry.dayPlaceholder')"/>
      <SecondaryButton :icon="['fas', 'plus']" :disabled="!canAdd" class="text-sm" data-testid="reminder-day-add"
                       @click="add">
        {{ t('membersConfig.expiry.addDay') }}
      </SecondaryButton>
    </div>
  </div>
</template>

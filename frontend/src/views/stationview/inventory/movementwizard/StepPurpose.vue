/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {MovementPurposeName} from '@/api/movements'

/**
 * What the reader wants to do, which is the one question that decides everything after it.
 *
 * <p>Only the purposes this station can start are offered. A station with nobody above it has nobody
 * to ask for anything, and offering the question anyway would end in a refusal three steps later.
 */
const props = defineProps<{
  purposes: MovementPurposeName[]
}>()

const purpose = defineModel<MovementPurposeName | null>({required: true})

const emit = defineEmits<{
  picked: []
}>()

const {t} = useI18n()

function pick(chosen: MovementPurposeName) {
  purpose.value = chosen
  emit('picked')
}
</script>

<template>
  <div class="space-y-3">
    <SubHeader>{{ t('movements.wizard.purpose.title') }}</SubHeader>
    <button
        v-for="option in props.purposes"
        :key="option"
        :class="[
          'w-full rounded-theme border px-3 py-2 text-left transition-colors',
          purpose === option ? 'border-primary bg-primary/10' : 'border-(--border) hover:border-primary',
        ]"
        :data-testid="`wizard-purpose-${option}`"
        type="button"
        @click="pick(option)"
    >
      <span class="block text-sm font-medium">{{ t(`movements.wizard.purpose.${option}`) }}</span>
      <MutedText size="sm">{{ t(`movements.wizard.purpose.${option}Hint`) }}</MutedText>
    </button>
  </div>
</template>

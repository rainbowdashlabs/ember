/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import BareButton from '@/components/button/BareButton.vue'
import type {MovementPurpose} from '@/api/generated/schema'

/**
 * What the reader wants to do, which is the one question that decides everything after it.
 *
 * <p>Only the purposes this station can start are offered. A station with nobody above it has nobody
 * to ask for anything, and offering the question anyway would end in a refusal three steps later.
 */
const props = defineProps<{
  purposes: MovementPurpose[]
}>()

const purpose = defineModel<MovementPurpose | null>({required: true})

const emit = defineEmits<{
  picked: []
}>()

const {t} = useI18n()

function pick(chosen: MovementPurpose) {
  purpose.value = chosen
  emit('picked')
}
</script>

<template>
  <div class="space-y-3">
    <SubHeader>{{ t('movements.wizard.purpose.title') }}</SubHeader>
    <BareButton
        v-for="option in props.purposes"
        :key="option"
        :class="[
          'w-full rounded-theme border px-3 py-2 text-left transition-colors',
          purpose === option ? 'border-primary bg-primary/10' : 'border-(--border) hover:border-primary',
        ]"
        :data-testid="`wizard-purpose-${option}`"
        @click="pick(option)"
    >
      <span class="block text-sm font-medium">{{ t(`movements.wizard.purpose.${option}`) }}</span>
      <MutedText size="sm">{{ t(`movements.wizard.purpose.${option}Hint`) }}</MutedText>
    </BareButton>
  </div>
</template>

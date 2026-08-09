/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import DecimalInput from '@/components/input/number/DecimalInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldHint from '@/components/typography/FieldHint.vue'

const { t } = useI18n()

const config = defineModel<Record<string, unknown>>('config', {required: true})

function updateConfig(patch: Record<string, unknown>) {
  config.value = { ...config.value, ...patch }
}

function addConnectPair() {
  const pairs = [...((config.value.pairs as { left: string; right: string }[]) || [])]
  pairs.push({ left: '', right: '' })
  updateConfig({ pairs })
}

function removeConnectPair(idx: number) {
  const pairs = [...((config.value.pairs as { left: string; right: string }[]) || [])]
  pairs.splice(idx, 1)
  updateConfig({ pairs })
}

function updateConnectPairLeft(idx: number, value: string) {
  const pairs = [...((config.value.pairs as { left: string; right: string }[]) || [])]
  const pair = pairs[idx]
  if (!pair) return
  pairs[idx] = { ...pair, left: value }
  updateConfig({ pairs })
}

function updateConnectPairRight(idx: number, value: string) {
  const pairs = [...((config.value.pairs as { left: string; right: string }[]) || [])]
  const pair = pairs[idx]
  if (!pair) return
  pairs[idx] = { ...pair, right: value }
  updateConfig({ pairs })
}
</script>

<template>
  <div class="flex items-center gap-2">
    <FieldHint>{{ t('quiz.questions.config.pointsPerCorrect') }}</FieldHint>
    <DecimalInput :model-value="(config.pointsPerCorrect as number) || 1" step="0.5" class="w-20" @update:model-value="(v: number | undefined) => updateConfig({ pointsPerCorrect: v ?? 1 })"/>
  </div>
  <SubHeader>{{ t('quiz.questions.config.pairs') }}</SubHeader>
  <div class="space-y-2">
    <div v-for="(pair, idx) in (config.pairs as { left: string; right: string }[])" :key="idx" class="flex items-center gap-2">
      <TextInput :model-value="pair.left" class="flex-1" :placeholder="t('quiz.questions.config.left')" @update:model-value="(v: string | undefined) => updateConnectPairLeft(idx, v ?? '')" />
      <font-awesome-icon :icon="['fas', 'arrow-right']" class="text-(--text-muted) shrink-0" />
      <TextInput :model-value="pair.right" class="flex-1" :placeholder="t('quiz.questions.config.right')" @update:model-value="(v: string | undefined) => updateConnectPairRight(idx, v ?? '')" />
      <DeleteButton @click="removeConnectPair(idx)" />
    </div>
    <SecondaryButton @click="addConnectPair"><font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />{{ t('quiz.questions.config.addPair') }}</SecondaryButton>
  </div>
</template>

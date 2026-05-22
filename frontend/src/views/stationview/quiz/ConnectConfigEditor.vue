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
import SubHeader from '@/components/typography/SubHeader.vue'

const { t } = useI18n()

const props = defineProps<{
  config: Record<string, unknown>
}>()

const emit = defineEmits<{
  'update:config': [value: Record<string, unknown>]
}>()

function updateConfig(patch: Record<string, unknown>) {
  emit('update:config', { ...props.config, ...patch })
}

// --- Connect helpers ---
function addConnectPair() {
  const pairs = [...((props.config.pairs as { left: string; right: string }[]) || [])]
  pairs.push({ left: '', right: '' })
  updateConfig({ pairs })
}

function removeConnectPair(idx: number) {
  const pairs = [...((props.config.pairs as { left: string; right: string }[]) || [])]
  pairs.splice(idx, 1)
  updateConfig({ pairs })
}

function updateConnectPairLeft(idx: number, value: string) {
  const pairs = [...((props.config.pairs as { left: string; right: string }[]) || [])]
  pairs[idx] = { ...pairs[idx], left: value }
  updateConfig({ pairs })
}

function updateConnectPairRight(idx: number, value: string) {
  const pairs = [...((props.config.pairs as { left: string; right: string }[]) || [])]
  pairs[idx] = { ...pairs[idx], right: value }
  updateConfig({ pairs })
}
</script>

<template>
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

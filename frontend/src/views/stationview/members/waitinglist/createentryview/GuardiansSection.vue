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
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type { GuardianInput } from '@/api/types'

const props = defineProps<{
  guardians: GuardianInput[]
}>()

const emit = defineEmits<{
  (e: 'update:guardians', value: GuardianInput[]): void
  (e: 'add'): void
  (e: 'remove', index: number): void
}>()

const { t } = useI18n()

function updateGuardian(index: number, key: keyof GuardianInput, value: string) {
  const next = props.guardians.map((g, i) => i === index ? { ...g, [key]: value } : g)
  emit('update:guardians', next)
}
</script>

<template>
  <div class="space-y-3">
    <FieldLabel>{{ t('waitingList.guardians') }} <span class="text-error">*</span></FieldLabel>
    <NeutralContainer v-for="(g, i) in guardians" :key="i" class="space-y-2">
      <div class="flex items-center justify-between">
        <span class="text-sm font-medium">{{ t('waitingList.guardian') }} {{ i + 1 }}</span>
        <DeleteButton v-if="guardians.length > 1" @click="emit('remove', i)"/>
      </div>
      <div class="grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
        <TextInput :model-value="g.firstname" @update:model-value="updateGuardian(i, 'firstname', $event)" :placeholder="t('waitingList.firstnamePlaceholder')"/>
        <TextInput :model-value="g.lastname" @update:model-value="updateGuardian(i, 'lastname', $event)" :placeholder="t('waitingList.lastnamePlaceholder')"/>
        <TextInput :model-value="g.email" @update:model-value="updateGuardian(i, 'email', $event)" :placeholder="t('waitingList.guardianEmailPlaceholder')"/>
        <TextInput :model-value="g.phone" @update:model-value="updateGuardian(i, 'phone', $event)" :placeholder="t('waitingList.guardianPhonePlaceholder')"/>
      </div>
    </NeutralContainer>
    <SecondaryButton :icon="['fas', 'plus']" @click="emit('add')">{{ t('waitingList.addGuardian') }}
    </SecondaryButton>
  </div>
</template>

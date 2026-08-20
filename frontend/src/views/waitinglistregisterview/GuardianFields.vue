/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type { GuardianInput } from '@/api/waitingList'

const props = defineProps<{ guardians: GuardianInput[] }>()
const emit = defineEmits<{
  (e: 'add'): void
  (e: 'remove', index: number): void
}>()

const { t } = useI18n()
</script>

<template>
  <div class="space-y-3">
    <FieldLabel>{{ t('waitingList.guardians') }} <span class="text-error">*</span></FieldLabel>
    <div v-for="(g, i) in props.guardians" :key="i" class="space-y-2 rounded-lg border border-bg-light-accent dark:border-bg-dark-accent p-3">
      <div class="flex items-center justify-between">
        <span class="text-sm font-medium">{{ t('waitingList.guardian') }}</span>
        <DeleteButton v-if="props.guardians.length > 1" @click="emit('remove', i)" />
      </div>
      <TextInput v-model="g.firstname" :placeholder="t('waitingList.firstnamePlaceholder')" />
      <TextInput v-model="g.lastname" :placeholder="t('waitingList.lastnamePlaceholder')" />
      <TextInput v-model="g.email" :placeholder="t('waitingList.guardianEmailPlaceholder')" />
      <TextInput v-model="g.phone" :placeholder="t('waitingList.guardianPhonePlaceholder')" />
    </div>
    <SecondaryButton :icon="['fas', 'plus']" @click="emit('add')">{{ t('waitingList.addGuardian') }}</SecondaryButton>
  </div>
</template>

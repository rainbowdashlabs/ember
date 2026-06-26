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

defineProps<{
  guardians: GuardianInput[]
}>()

const emit = defineEmits<{
  add: []
  remove: [index: number]
}>()

const { t } = useI18n()
</script>

<template>
  <div class="space-y-3">
    <FieldLabel>{{ t('waitingList.guardians') }}</FieldLabel>
    <NeutralContainer v-for="(g, i) in guardians" :key="i" class="space-y-2">
      <div class="flex items-center justify-between">
        <span class="text-sm font-medium">{{ t('waitingList.guardian') }} {{ i + 1 }}</span>
        <DeleteButton v-if="guardians.length > 1" @click="emit('remove', i)" />
      </div>
      <div class="grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
        <TextInput v-model="g.firstname" :placeholder="t('waitingList.firstnamePlaceholder')" />
        <TextInput v-model="g.lastname" :placeholder="t('waitingList.lastnamePlaceholder')" />
        <TextInput v-model="g.email" :placeholder="t('waitingList.guardianEmailPlaceholder')" />
        <TextInput v-model="g.phone" :placeholder="t('waitingList.guardianPhonePlaceholder')" />
      </div>
    </NeutralContainer>
    <SecondaryButton :icon="['fas', 'plus']" @click="emit('add')">{{ t('waitingList.addGuardian') }}</SecondaryButton>
  </div>
</template>

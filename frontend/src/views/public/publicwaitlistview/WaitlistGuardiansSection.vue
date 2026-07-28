/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import type {GuardianInput} from '@/api/waitingList'

defineProps<{
  guardians: GuardianInput[]
}>()

const emit = defineEmits<{
  (e: 'add'): void
  (e: 'remove', index: number): void
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-3">
    <SubHeader>{{ t('waitingList.guardians') }}</SubHeader>
    <NeutralContainer v-for="(g, i) in guardians" :key="i" class="space-y-2">
      <div class="flex items-center justify-between">
        <span class="text-sm font-medium">{{ t('waitingList.guardian') }} {{ i + 1 }}</span>
        <DeleteButton v-if="guardians.length > 1" @click="emit('remove', i)"/>
      </div>
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-2">
        <TextInput v-model="g.firstname" :placeholder="t('waitingList.firstnamePlaceholder')"/>
        <TextInput v-model="g.lastname" :placeholder="t('waitingList.lastnamePlaceholder')"/>
        <TextInput v-model="g.email" :placeholder="t('waitingList.guardianEmailPlaceholder')"/>
        <TextInput v-model="g.phone" :placeholder="t('waitingList.guardianPhonePlaceholder')"/>
      </div>
    </NeutralContainer>
    <SecondaryButton :icon="['fas', 'plus']" @click="emit('add')">{{ t('waitingList.addGuardian') }}</SecondaryButton>
  </div>
</template>

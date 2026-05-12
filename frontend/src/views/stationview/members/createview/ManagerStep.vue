/*
*     SPDX-License-Identifier: AGPL-3.0-only
*
*     Copyright (C) RainbowDashLabs and Contributor
*/
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type {StationMember} from '@/api/types'

const {t} = useI18n()

defineProps<{
  members: StationMember[]
  selectedIds: Set<number>
  createdManagers: Array<{ id: number; memberId: number; firstName: string; lastName: string; email: string }>
  saving: boolean
}>()

const emit = defineEmits<{
  next: []
  back: []
  toggleManager: [id: number]
  createManager: [data: { firstName: string; lastName: string; email: string }]
}>()

const newFirstName = ref('')
const newLastName = ref('')
const newEmail = ref('')
const creating = ref(false)

function memberDisplayName(m: StationMember): string {
  return m.name && m.name.trim() ? m.name : m.email ?? `#${m.id}`
}

async function submitCreate() {
  creating.value = true
  emit('createManager', {firstName: newFirstName.value, lastName: newLastName.value, email: newEmail.value})
  newFirstName.value = ''
  newLastName.value = ''
  newEmail.value = ''
  creating.value = false
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('membersCreate.stepManager') }}</SectionHeader>
    <p class="text-sm text-(--text-muted)">{{ t('membersCreate.stepManagerHint') }}</p>

    <!-- Existing members -->
    <div v-if="members.length > 0" class="space-y-2">
      <h3 class="text-sm font-semibold uppercase text-(--text-muted)">{{ t('membersCreate.existingManagers') }}</h3>
      <div
          v-for="member in members"
          :key="member.id"
          :class="selectedIds.has(member.id) ? 'border-primary bg-primary/10 ring-2 ring-primary/30' : 'border-bg-light-accent dark:border-bg-dark-accent hover:border-primary'"
          class="flex items-center justify-between rounded-lg px-4 py-3 border cursor-pointer transition-colors"
          @click="emit('toggleManager', member.id)"
      >
        <span class="font-medium">{{ memberDisplayName(member) }}</span>
        <font-awesome-icon v-if="selectedIds.has(member.id)" :icon="['fas', 'check']" class="text-primary"/>
      </div>
    </div>

    <!-- Create new manager -->
    <div class="space-y-3 pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent">
      <h3 class="text-sm font-semibold uppercase text-(--text-muted)">{{ t('membersCreate.createNewManager') }}</h3>
      <p class="text-xs text-(--text-muted)">{{ t('membersCreate.createNewManagerHint') }}</p>
      <div class="grid gap-3 sm:grid-cols-3">
        <TextInput v-model="newFirstName" :placeholder="t('membersCreate.firstName')"/>
        <TextInput v-model="newLastName" :placeholder="t('membersCreate.lastName')"/>
        <TextInput v-model="newEmail" :placeholder="t('membersCreate.email')"/>
      </div>
      <SecondaryButton :disabled="!newEmail || !newFirstName || !newLastName || creating" @click="submitCreate">
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1"/>
        {{ creating ? t('common.loading') : t('membersCreate.addManager') }}
      </SecondaryButton>

      <!-- Newly created managers -->
      <div v-if="createdManagers.length > 0" class="space-y-1">
        <div v-for="mgr in createdManagers" :key="mgr.id"
             class="flex items-center gap-2 rounded-lg px-3 py-2 bg-primary/10 border border-primary">
          <font-awesome-icon :icon="['fas', 'check']" class="text-primary"/>
          <span class="text-sm font-medium">{{ mgr.firstName }} {{ mgr.lastName }}</span>
          <span class="text-xs text-(--text-muted)">{{ mgr.email }}</span>
        </div>
      </div>
    </div>

    <div class="flex justify-between">
      <SecondaryButton @click="emit('back')">{{ t('membersCreate.back') }}</SecondaryButton>
      <PrimaryButton :disabled="saving" @click="emit('next')">
        {{ saving ? t('common.loading') : t('membersCreate.create') }}
      </PrimaryButton>
    </div>
  </NeutralContainer>
</template>

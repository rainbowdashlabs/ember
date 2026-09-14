/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import {fromMember, userTypesOf} from '@/components/input/select/memberOption'
import {StationUserType, type StationMember} from '@/api/types'

const {t} = useI18n()

const props = defineProps<{
  members: StationMember[]
  selectedIds: Set<number>
  createdManagers: Array<{ id: number; memberId: number; firstName: string; lastName: string; email: string }>
  saving: boolean
}>()

const emit = defineEmits<{
  next: []
  back: []
  setManagers: [ids: number[]]
  createManager: [data: { firstName: string; lastName: string; email: string }]
}>()

const newFirstName = ref('')
const newLastName = ref('')
const newEmail = ref('')

const candidates = computed(() => props.members.map(fromMember))
const userTypes = computed(() => userTypesOf(candidates.value))

/**
 * The menu speaks in strings and a member id out of the database is a number, which is the one
 * translation between them.
 */
const chosen = computed({
  get: () => [...props.selectedIds].map(String),
  set: values => emit('setManagers', values.map(Number)),
})

function submitCreate() {
  emit('createManager', {firstName: newFirstName.value, lastName: newLastName.value, email: newEmail.value})
  newFirstName.value = ''
  newLastName.value = ''
  newEmail.value = ''
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('membersCreate.stepManager') }}</SectionHeader>
    <p class="text-sm text-(--text-muted)">{{ t('membersCreate.stepManagerHint') }}</p>

    <div v-if="members.length > 0" class="space-y-2">
      <SubHeader class="text-sm font-semibold uppercase text-(--text-muted)">{{ t('membersCreate.existingManagers') }}</SubHeader>
      <MemberSelectInput
          v-model:selected="chosen"
          multiple
          :members="candidates"
          :user-types="userTypes"
          :opening-user-type="StationUserType.GUARDIAN"
          :placeholder="t('membersCreate.existingManagers')"
      />
    </div>

    <div class="space-y-3 pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent">
      <SubHeader class="text-sm font-semibold uppercase text-(--text-muted)">{{ t('membersCreate.createNewManager') }}</SubHeader>
      <p class="text-xs text-(--text-muted)">{{ t('membersCreate.createNewManagerHint') }}</p>
      <div class="grid gap-3 sm:grid-cols-3">
        <TextInput v-model="newFirstName" :placeholder="t('membersCreate.firstName')"/>
        <TextInput v-model="newLastName" :placeholder="t('membersCreate.lastName')"/>
        <TextInput v-model="newEmail" :placeholder="t('membersCreate.email')"/>
      </div>
      <SecondaryButton :icon="['fas', 'plus']" :disabled="!newEmail || !newFirstName || !newLastName" @click="submitCreate">
        {{ t('membersCreate.addManager') }}
      </SecondaryButton>

      <div v-if="createdManagers.length > 0" class="space-y-1">
        <div v-for="mgr in createdManagers" :key="mgr.id"
             class="flex items-center gap-2 rounded-lg px-3 py-2 bg-primary/10 border border-primary">
          <font-awesome-icon :icon="['fas', 'check']" class="text-primary"/>
          <span class="text-sm font-medium">{{ mgr.firstName }} {{ mgr.lastName }}</span>
          <span class="text-xs text-(--text-muted)">{{ mgr.email }}</span>
        </div>
      </div>
    </div>

    <ButtonRow align="between">
      <SecondaryButton @click="emit('back')">{{ t('membersCreate.back') }}</SecondaryButton>
      <PrimaryButton :disabled="saving" @click="emit('next')">
        {{ saving ? t('common.loading') : t('membersCreate.create') }}
      </PrimaryButton>
    </ButtonRow>
  </NeutralContainer>
</template>

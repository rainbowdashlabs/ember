/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import UserTypeStep from './UserTypeStep.vue'
import IdentityStep from './IdentityStep.vue'
import FieldsStep from './FieldsStep.vue'
import GroupsStep from './GroupsStep.vue'
import ManagerStep from './ManagerStep.vue'
import DoneStep from './DoneStep.vue'
import type { ProfileField } from '@/api/profileFields'
import type { MemberGroup, StationMember } from '@/api/types'
import { StationUserType } from '@/api/types'

type Step = 'userType' | 'identity' | 'fields' | 'groups' | 'manager' | 'done'

const props = defineProps<{
  step: Step
  selectedUserType: 'TRIAL' | 'MEMBER' | 'GUARDIAN' | 'TEAM'
  canLogin: boolean
  email: string
  firstName: string
  lastName: string
  scopeFields: ProfileField[]
  fieldValues: Map<number, string>
  allGroups: MemberGroup[]
  selectedGroupIds: Set<number>
  allMembers: StationMember[]
  selectedManagerIds: Set<number>
  createdManagers: Array<{ id: number; memberId: number; firstName: string; lastName: string; email: string }>
  saving: boolean
}>()

const emit = defineEmits<{
  (e: 'update:step', value: Step): void
  (e: 'update:selectedUserType', value: 'TRIAL' | 'MEMBER' | 'GUARDIAN' | 'TEAM'): void
  (e: 'update:canLogin', value: boolean): void
  (e: 'update:email', value: string): void
  (e: 'update:firstName', value: string): void
  (e: 'update:lastName', value: string): void
  (e: 'next-from-identity'): void
  (e: 'next-from-groups'): void
  (e: 'set-field-value', fieldId: number, val: string): void
  (e: 'toggle-group', id: number): void
  (e: 'toggle-manager', id: number): void
  (e: 'create-manager', data: { firstName: string; lastName: string; email: string }): void
  (e: 'create-account'): void
  (e: 'start-over'): void
  (e: 'to-list'): void
}>()

const { t } = useI18n()

const submitLabel = () =>
  props.selectedUserType === StationUserType.MEMBER ? t('membersCreate.next') : t('membersCreate.create')
</script>

<template>
  <UserTypeStep
    v-if="step === 'userType'"
    :model-value="selectedUserType"
    @update:model-value="emit('update:selectedUserType', $event as 'TRIAL' | 'MEMBER' | 'GUARDIAN' | 'TEAM')"
    @next="emit('update:step', 'identity')"
  />

  <IdentityStep
    v-if="step === 'identity'"
    :can-login="canLogin"
    :email="email"
    :first-name="firstName"
    :last-name="lastName"
    @update:can-login="emit('update:canLogin', $event)"
    @update:email="emit('update:email', $event)"
    @update:first-name="emit('update:firstName', $event)"
    @update:last-name="emit('update:lastName', $event)"
    @back="emit('update:step', 'userType')"
    @next="emit('next-from-identity')"
  />

  <FieldsStep
    v-if="step === 'fields'"
    :fields="scopeFields"
    :values="fieldValues"
    @back="emit('update:step', 'identity')"
    @next="emit('update:step', 'groups')"
    @set-value="(id, v) => emit('set-field-value', id, v)"
  />

  <GroupsStep
    v-if="step === 'groups'"
    :groups="allGroups"
    :selected-ids="selectedGroupIds"
    :submit-label="submitLabel()"
    @back="emit('update:step', 'fields')"
    @next="emit('next-from-groups')"
    @toggle="(id) => emit('toggle-group', id)"
  />

  <ManagerStep
    v-if="step === 'manager'"
    :created-managers="createdManagers"
    :members="allMembers"
    :saving="saving"
    :selected-ids="selectedManagerIds"
    @back="emit('update:step', 'groups')"
    @next="emit('create-account')"
    @toggle-manager="(id) => emit('toggle-manager', id)"
    @create-manager="(data) => emit('create-manager', data)"
  />

  <DoneStep
    v-if="step === 'done'"
    @create-another="emit('start-over')"
    @to-list="emit('to-list')"
  />
</template>

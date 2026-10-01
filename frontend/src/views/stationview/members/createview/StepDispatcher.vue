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
import type { MemberWithName, ProfileField } from '@/api/generated/schema'
import {StationUserType, type MemberGroup} from '@/api/types'

type Step = 'userType' | 'identity' | 'fields' | 'groups' | 'manager' | 'done'

const props = defineProps<{
  step: Step
  selectedUserType: 'TRIAL' | 'MEMBER' | 'GUARDIAN' | 'TEAM'
  canLogin: boolean
  sendSetupMail: boolean
  email: string
  firstName: string
  lastName: string
  scopeFields: ProfileField[]
  fieldValues: Map<number, string>
  allGroups: MemberGroup[]
  selectedGroupIds: Set<number>
  allMembers: MemberWithName[]
  selectedManagerIds: Set<number>
  createdManagers: Array<{ id: number; memberId: number; firstName: string; lastName: string; email: string }>
  saving: boolean
}>()

const emit = defineEmits<{
  'update:step': [value: Step]
  'update:selectedUserType': [value: 'TRIAL' | 'MEMBER' | 'GUARDIAN' | 'TEAM']
  'update:canLogin': [value: boolean]
  'update:sendSetupMail': [value: boolean]
  'update:email': [value: string]
  'update:firstName': [value: string]
  'update:lastName': [value: string]
  'next-from-identity': []
  'next-from-groups': []
  'set-field-value': [fieldId: number, val: string]
  'toggle-group': [id: number]
  'set-managers': [ids: number[]]
  'create-manager': [data: { firstName: string; lastName: string; email: string }]
  'create-account': []
  'start-over': []
  'to-list': []
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
    :send-setup-mail="sendSetupMail"
    :email="email"
    :first-name="firstName"
    :last-name="lastName"
    @update:can-login="emit('update:canLogin', $event)"
    @update:send-setup-mail="emit('update:sendSetupMail', $event)"
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
    @set-managers="(ids) => emit('set-managers', ids)"
    @create-manager="(data) => emit('create-manager', data)"
  />

  <DoneStep
    v-if="step === 'done'"
    @create-another="emit('start-over')"
    @to-list="emit('to-list')"
  />
</template>

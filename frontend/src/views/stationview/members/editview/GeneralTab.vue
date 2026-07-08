/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import PermissionPicker from '@/components/input/PermissionPicker.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Alert from '@/components/feedback/Alert.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type {MemberGroup, PermissionGrant, StationMember, UserTag} from '@/api/types'
import {StationUserType} from '@/api/types'
import {stationMembers, memberGroups, userTags} from '@/api'
import type {MyInventoryItem} from '@/api/inventory'

const {t} = useI18n()

const props = defineProps<{
  member: StationMember
  memberId: number
  allRoles: PermissionGrant[]
  allGroups: MemberGroup[]
  allTags: UserTag[]
  initialUserType: string
  initialRoleIds: Set<number>
  initialGroupIds: Set<number>
  initialTagIds: Set<number>
  lockedPermissions: Map<string, string>
  memberInventory: MyInventoryItem[]
}>()

const emit = defineEmits<{
  userTypeChanged: [userType: string]
  groupsChanged: [groupIds: Set<number>]
}>()

const error = ref('')
const success = ref('')

// -- User Type --
const editUserType = ref(props.initialUserType)

async function onUserTypeChange(value: string) {
  error.value = ''
  success.value = ''
  try {
    await stationMembers.setUserType(props.memberId, value)
    editUserType.value = value
    emit('userTypeChanged', value)
  } catch {
    error.value = t('common.error')
  }
}

// -- Permissions --
const editRoleIds = ref(new Set(props.initialRoleIds))

async function onPermissionsChange(newIds: Set<number>) {
  editRoleIds.value = newIds
  error.value = ''
  try {
    await stationMembers.setPermissions(props.memberId, {permissionIds: [...newIds]})
  } catch {
    error.value = t('common.error')
  }
}

// -- Groups --
const editGroupIds = ref(new Set(props.initialGroupIds))

async function toggleGroup(groupId: number) {
  error.value = ''
  const wasIn = editGroupIds.value.has(groupId)
  if (wasIn) {
    editGroupIds.value.delete(groupId)
  } else {
    editGroupIds.value.add(groupId)
  }
  editGroupIds.value = new Set(editGroupIds.value)
  try {
    const currentMembers = await memberGroups.getGroupMembers(groupId)
    const memberIds = wasIn
        ? currentMembers.filter(m => m.id !== props.memberId).map(m => m.id)
        : [...currentMembers.map(m => m.id), props.memberId]
    await memberGroups.setGroupMembers(groupId, {memberIds})
    emit('groupsChanged', new Set(editGroupIds.value))
  } catch {
    // Revert on error
    if (wasIn) editGroupIds.value.add(groupId)
    else editGroupIds.value.delete(groupId)
    editGroupIds.value = new Set(editGroupIds.value)
    error.value = t('common.error')
  }
}

// -- Tags --
const editTagIds = ref(new Set(props.initialTagIds))

async function toggleTag(tagId: number) {
  error.value = ''
  const wasIn = editTagIds.value.has(tagId)
  if (wasIn) {
    editTagIds.value.delete(tagId)
  } else {
    editTagIds.value.add(tagId)
  }
  editTagIds.value = new Set(editTagIds.value)
  try {
    const currentMembers = await userTags.getTagMembers(tagId)
    const memberIds = wasIn
        ? currentMembers.filter(m => m.id !== props.memberId).map(m => m.id)
        : [...currentMembers.map(m => m.id), props.memberId]
    await userTags.setTagMembers(tagId, memberIds)
  } catch {
    if (wasIn) editTagIds.value.add(tagId)
    else editTagIds.value.delete(tagId)
    editTagIds.value = new Set(editTagIds.value)
    error.value = t('common.error')
  }
}

// -- Former member --
const showFormerModal = ref(false)
const markingFormer = ref(false)
const formerSuccess = ref(false)

const formerBlockReasons = computed(() => {
  const reasons: string[] = []
  if (props.memberInventory.length > 0) {
    reasons.push(t('memberDetail.formerBlockInventory', {count: props.memberInventory.length}))
  }
  const forbidden = [StationUserType.GUARDIAN, StationUserType.MANAGER]
  if (forbidden.includes(editUserType.value as any)) {
    reasons.push(t('memberDetail.formerBlockRole'))
  }
  return reasons
})
const canMarkFormer = computed(() => formerBlockReasons.value.length === 0)

async function confirmMarkFormer() {
  markingFormer.value = true
  error.value = ''
  try {
    await stationMembers.markFormer(props.memberId)
    formerSuccess.value = true
    showFormerModal.value = false
  } catch {
    error.value = t('common.error')
  } finally {
    markingFormer.value = false
  }
}
</script>

<template>
  <div class="space-y-6">
    <Alert v-if="error" variant="error">{{ error }}</Alert>
    <Alert v-if="success" variant="success">{{ success }}</Alert>

    <!-- User type -->
    <NeutralContainer class="space-y-3">
      <SubHeader class="text-sm">{{ t('memberEdit.userType') }}</SubHeader>
      <SelectInput :model-value="editUserType" @update:model-value="v => { if (v) onUserTypeChange(v) }" class="max-w-xs">
        <option :value="StationUserType.MANAGER">{{ t('memberEdit.userTypeManager') }}</option>
        <option :value="StationUserType.TEAM">{{ t('memberEdit.userTypeTeam') }}</option>
        <option :value="StationUserType.GUARDIAN">{{ t('memberEdit.userTypeGuardian') }}</option>
        <option :value="StationUserType.MEMBER">{{ t('memberEdit.userTypeMember') }}</option>
        <option :value="StationUserType.TRIAL">{{ t('memberEdit.userTypeTrial') }}</option>
      </SelectInput>
    </NeutralContainer>

    <!-- Permissions -->
    <NeutralContainer class="space-y-3">
      <SubHeader class="text-sm">{{ t('memberEdit.permissions') }}</SubHeader>
      <PermissionPicker :model-value="editRoleIds" :all-roles="allRoles" :locked-permissions="lockedPermissions"
                        @update:model-value="onPermissionsChange"/>
    </NeutralContainer>

    <!-- Groups -->
    <NeutralContainer class="space-y-3">
      <SubHeader class="text-sm">{{ t('memberEdit.groups') }}</SubHeader>
      <div class="flex flex-wrap gap-2">
        <button
            v-for="group in allGroups"
            :key="group.id"
            :class="editGroupIds.has(group.id) ? 'border-primary bg-primary/10 text-primary' : 'border-bg-light-accent dark:border-bg-dark-accent text-(--text-muted) hover:border-primary'"
            class="px-2.5 py-1 text-xs rounded-full border transition-colors"
            @click="toggleGroup(group.id)"
        >
          {{ group.name }}
        </button>
        <span v-if="allGroups.length === 0" class="text-xs text-(--text-muted)">{{ t('memberEdit.noGroups') }}</span>
      </div>
    </NeutralContainer>

    <!-- Tags -->
    <NeutralContainer class="space-y-3">
      <SubHeader class="text-sm">{{ t('memberEdit.tags') }}</SubHeader>
      <div class="flex flex-wrap gap-2">
        <button
            v-for="tag in allTags"
            :key="tag.id"
            :class="editTagIds.has(tag.id) ? 'border-primary bg-primary/10 text-primary' : 'border-bg-light-accent dark:border-bg-dark-accent text-(--text-muted) hover:border-primary'"
            class="px-2.5 py-1 text-xs rounded-full border transition-colors"
            @click="toggleTag(tag.id)"
        >
          {{ tag.name }}
        </button>
        <span v-if="allTags.length === 0" class="text-xs text-(--text-muted)">{{ t('memberEdit.noTags') }}</span>
      </div>
    </NeutralContainer>

    <!-- Former member -->
    <div class="flex items-center justify-end">
      <ErrorButton v-if="!formerSuccess" :icon="['fas', 'user-slash']" @click="showFormerModal = true">
        {{ t('memberDetail.markFormer') }}
      </ErrorButton>
    </div>
    <Alert v-if="formerSuccess" variant="success">{{ t('memberDetail.formerSuccess') }}</Alert>

    <Modal v-model="showFormerModal">
      <div class="space-y-4">
        <SectionHeader>{{ t('memberDetail.markFormerTitle') }}</SectionHeader>
        <template v-if="canMarkFormer">
          <p class="text-sm">{{ t('memberDetail.markFormerConfirm', {name: member?.name || member?.email || ''}) }}</p>
          <p class="text-xs text-(--text-muted)">{{ t('memberDetail.markFormerHint') }}</p>
          <div class="flex justify-end gap-2">
            <SecondaryButton @click="showFormerModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <ErrorButton :disabled="markingFormer" @click="confirmMarkFormer">
              {{ markingFormer ? t('common.loading') : t('memberDetail.markFormer') }}
            </ErrorButton>
          </div>
        </template>
        <template v-else>
          <p class="text-sm">{{ t('memberDetail.formerBlocked') }}</p>
          <ul class="list-disc list-inside text-sm text-error space-y-1">
            <li v-for="(reason, i) in formerBlockReasons" :key="i">{{ reason }}</li>
          </ul>
          <div class="flex justify-end">
            <SecondaryButton @click="showFormerModal = false">{{ t('common.close') }}</SecondaryButton>
          </div>
        </template>
      </div>
    </Modal>
  </div>
</template>

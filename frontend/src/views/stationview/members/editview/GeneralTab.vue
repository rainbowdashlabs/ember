/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import PermissionPicker from '@/components/input/PermissionPicker.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Alert from '@/components/feedback/Alert.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MemberUserTypeSelect from './MemberUserTypeSelect.vue'
import MemberGroupChips from './MemberGroupChips.vue'
import MemberTagChips from './MemberTagChips.vue'
import {
  StationUserType,
  type MemberGroup,
  type MemberGroupSet,
  type MemberWithName,
  type MyInventoryItem,
  type Permission,
  type UserTag,
} from '@/api/generated/schema'
import {stationMembers, memberGroups} from '@/api'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {describeFailure, type Failure} from '@/util/failure'

const {t} = useI18n()

/**
 * What a member is at the station: their type, their groups and tags, and their own permissions.
 *
 * <p>Groups and tags come first as compact chips, because they are what a manager changes most and
 * what used to go unnoticed below a long list of permissions. The groups are written for this one
 * member in one step, so two editors cannot overwrite each other's work on a whole group.
 */
const props = defineProps<{
  member: MemberWithName
  memberId: number
  allRoles: Permission[]
  allGroups: MemberGroup[]
  allSets: MemberGroupSet[]
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

const failure = ref<Failure | null>(null)
const success = ref('')

const editUserType = ref(props.initialUserType)
const editGroupIds = ref<Set<number>>(new Set(props.initialGroupIds))
const savingGroups = ref(false)

function onUserTypeChanged(userType: string, leftGroups: MemberGroup[]) {
  failure.value = null
  editUserType.value = userType
  emit('userTypeChanged', userType)
  if (leftGroups.length === 0) return
  const next = new Set(editGroupIds.value)
  for (const group of leftGroups) next.delete(group.id)
  editGroupIds.value = next
  emit('groupsChanged', next)
  success.value = t('memberEdit.userTypeLeftGroups', {groups: leftGroups.map(group => group.name).join(', ')})
}

async function onGroupsChange(groupIds: Set<number>) {
  failure.value = null
  success.value = ''
  savingGroups.value = true
  try {
    const saved = await memberGroups.setMemberGroups(props.memberId, [...groupIds])
    editGroupIds.value = new Set(saved.map(group => group.id))
    emit('groupsChanged', new Set(editGroupIds.value))
  } catch (e) {
    failure.value = describeFailure(e, t)
  } finally {
    savingGroups.value = false
  }
}

const editRoleIds = ref(new Set(props.initialRoleIds))

/**
 * Writes the ticked permissions back.
 *
 * <p>The picker keeps the new ticks either way, so a refusal has to be read to be noticed at all. It
 * is the refusal most worth saying plainly too: granting a right one does not hold oneself is the
 * ordinary way this is refused, and that is the station's business rather than a fault in Ember.
 */
async function onPermissionsChange(newIds: Set<number>) {
  editRoleIds.value = newIds
  failure.value = null
  try {
    await stationMembers.setPermissions(props.memberId, {permissionIds: [...newIds]})
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
}

const showFormerModal = ref(false)
const formerSuccess = ref(false)

const formerBlockReasons = computed(() => {
  const reasons: string[] = []
  if (props.memberInventory.length > 0) {
    reasons.push(t('memberDetail.formerBlockInventory', {count: props.memberInventory.length}))
  }
  const forbidden: string[] = [StationUserType.GUARDIAN, StationUserType.MANAGER]
  if (forbidden.includes(editUserType.value)) {
    reasons.push(t('memberDetail.formerBlockRole'))
  }
  return reasons
})
const canMarkFormer = computed(() => formerBlockReasons.value.length === 0)

const {running: markingFormer, failure: formerFailure, run: confirmMarkFormer} = useAsyncAction(async () => {
  failure.value = null
  await stationMembers.markFormer(props.memberId)
  formerSuccess.value = true
  showFormerModal.value = false
})
</script>

<template>
  <div class="space-y-6">
    <FailureAlert :failure="failure ?? formerFailure"/>
    <Alert v-if="success" variant="success">{{ success }}</Alert>

    <NeutralContainer class="space-y-3">
      <SubHeader class="text-sm">{{ t('memberEdit.userType') }}</SubHeader>
      <MemberUserTypeSelect :member-id="memberId" :user-type="editUserType"
                            @changed="onUserTypeChanged" @failed="f => failure = f"/>
    </NeutralContainer>

    <NeutralContainer class="space-y-3">
      <SubHeader class="text-sm">{{ t('memberEdit.groups') }}</SubHeader>
      <MemberGroupChips :groups="allGroups" :sets="allSets" :selected="editGroupIds" :user-type="editUserType"
                        :disabled="savingGroups" @change="onGroupsChange"/>
    </NeutralContainer>

    <NeutralContainer class="space-y-3">
      <SubHeader class="text-sm">{{ t('memberEdit.tags') }}</SubHeader>
      <MemberTagChips :member-id="memberId" :tags="allTags" :initial-tag-ids="initialTagIds" @failed="f => failure = f"/>
    </NeutralContainer>

    <NeutralContainer class="space-y-3">
      <SubHeader class="text-sm">{{ t('memberEdit.permissions') }}</SubHeader>
      <PermissionPicker :model-value="editRoleIds" :all-roles="allRoles" :locked-permissions="lockedPermissions"
                        @update:model-value="onPermissionsChange"/>
    </NeutralContainer>

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
          <ButtonRow pair align="end">
            <SecondaryButton @click="showFormerModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <ErrorButton :disabled="markingFormer" @click="confirmMarkFormer">
              {{ markingFormer ? t('common.loading') : t('memberDetail.markFormer') }}
            </ErrorButton>
          </ButtonRow>
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

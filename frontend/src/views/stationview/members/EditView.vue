/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ProfileFieldInput from '@/components/input/ProfileFieldInput.vue'
import PermissionPicker from '@/components/input/PermissionPicker.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'

import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import Modal from '@/components/feedback/Modal.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type { ProfileField, StationMember, PermissionGrant, MemberGroup, UserTag } from '@/api/types'
import { StationUserType } from '@/api/types'
import { profileFields, stationMembers, members, inventory, memberGroups, userTags } from '@/api'
import type { MyInventoryItem } from '@/api/inventory'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'


const { t } = useI18n()
const route = useRoute()
const router = useRouter()


const memberId = computed(() => Number(route.params.id))

const member = ref<StationMember | null>(null)
const fields = ref<ProfileField[]>([])
const allRoles = ref<PermissionGrant[]>([])
const editValues = ref<Map<number, string>>(new Map())
const editRoleIds = ref<Set<number>>(new Set())
const typeGrantedPermissions = ref<Set<string>>(new Set())
const editFirstName = ref('')
const editLastName = ref('')
const editEmail = ref('')
const allGroups = ref<MemberGroup[]>([])
const allTags = ref<UserTag[]>([])
const editGroupIds = ref<Set<number>>(new Set())
const editTagIds = ref<Set<number>>(new Set())
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const success = ref('')

function parseConfig(configStr: string | undefined): { options?: string[]; [key: string]: unknown } {
  if (!configStr) return {}
  try { return JSON.parse(configStr) } catch { return {} }
}

// --- Applicable fields based on user type ---

const editUserType = ref<string>('')

function getScopesForUserType(ut: string): string[] {
  const scopes: string[] = []
  if (ut === StationUserType.MEMBER || ut === StationUserType.TRIAL) scopes.push(StationUserType.MEMBER)
  if (ut === StationUserType.TEAM || ut === StationUserType.MANAGER) scopes.push(StationUserType.TEAM)
  if (ut === StationUserType.GUARDIAN) scopes.push(StationUserType.GUARDIAN)
  return scopes
}

const applicableFields = computed(() => {
  const scopes = getScopesForUserType(editUserType.value)
  return fields.value.filter(f => {
    if (f.scope === 'GROUP') return false
    return scopes.includes(f.scope ?? StationUserType.MEMBER)
  })
})

// --- Field values ---

function getEditValue(fieldId: number): string {
  return editValues.value.get(fieldId) ?? ''
}

function setEditValue(fieldId: number, val: string) {
  editValues.value = new Map([...editValues.value, [fieldId, val]])
}

async function loadTypePermissions(userType: string) {
  try {
    typeGrantedPermissions.value = new Set(await stationMembers.getEffectiveUserTypePermissions(userType))
  } catch { typeGrantedPermissions.value = new Set() }
}

async function onUserTypeChange(value: string) {
  editUserType.value = value
  await loadTypePermissions(value)
}

// --- Load / Save ---

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [allFields, allMembers, roles, memberData, memberPermissions, profileValues, groups, tags, mGroups, mTags] = await Promise.all([
      profileFields.listFields(),
      stationMembers.listMembers(),
      stationMembers.listAllRoles(),
      stationMembers.getMember(memberId.value),
      stationMembers.getPermissions(memberId.value),
      profileFields.getValues(memberId.value),
      memberGroups.listGroups(),
      userTags.listTags(),
      memberGroups.getMemberGroups(memberId.value),
      userTags.getMemberTags(memberId.value),
    ])
    fields.value = allFields
    allRoles.value = roles
    allGroups.value = groups
    allTags.value = tags
    editGroupIds.value = new Set(mGroups.map(g => g.id))
    editTagIds.value = new Set(mTags.map(t => t.id))
    member.value = allMembers.find(m => m.id === memberId.value) ?? null
    editFirstName.value = member.value?.name?.split(' ')[0] ?? ''
    editLastName.value = member.value?.name?.split(' ').slice(1).join(' ') ?? ''
    editEmail.value = member.value?.email ?? ''
    editUserType.value = memberData.userType ?? StationUserType.MEMBER
    editRoleIds.value = new Set(memberPermissions.map(r => r.id))
    await loadTypePermissions(editUserType.value)

    const map = new Map<number, string>()
    for (const v of profileValues) {
      let val = v.value ?? ''
      try { val = JSON.parse(val) } catch { /* use as-is */ }
      map.set(v.fieldId, typeof val === 'string' ? val : String(val))
    }
    editValues.value = map
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    // Update account base fields
    if (member.value) {
      await members.updateAccount(member.value.accountId, {
        email: editEmail.value,
        firstName: editFirstName.value,
        lastName: editLastName.value,
      })
    }
    // Update profile field values
    const entries = applicableFields.value.map(f => ({ fieldId: f.id, value: JSON.stringify(getEditValue(f.id)) }))
    await profileFields.setValues(memberId.value, { values: entries })
    // Update roles
    await stationMembers.setPermissions(memberId.value, { roleIds: [...editRoleIds.value] })
    // Update group memberships
    for (const group of allGroups.value) {
      const currentMembers = await memberGroups.getGroupMembers(group.id)
      const isMember = currentMembers.some(m => m.id === memberId.value)
      const shouldBeMember = editGroupIds.value.has(group.id)
      if (isMember && !shouldBeMember) {
        const newIds = currentMembers.filter(m => m.id !== memberId.value).map(m => m.id)
        await memberGroups.setGroupMembers(group.id, { memberIds: newIds })
      } else if (!isMember && shouldBeMember) {
        const newIds = [...currentMembers.map(m => m.id), memberId.value]
        await memberGroups.setGroupMembers(group.id, { memberIds: newIds })
      }
    }
    // Update tag memberships
    for (const tag of allTags.value) {
      const currentMembers = await userTags.getTagMembers(tag.id)
      const isMember = currentMembers.some(m => m.id === memberId.value)
      const shouldBeMember = editTagIds.value.has(tag.id)
      if (isMember && !shouldBeMember) {
        const newIds = currentMembers.filter(m => m.id !== memberId.value).map(m => m.id)
        await userTags.setTagMembers(tag.id, newIds)
      } else if (!isMember && shouldBeMember) {
        const newIds = [...currentMembers.map(m => m.id), memberId.value]
        await userTags.setTagMembers(tag.id, newIds)
      }
    }
    success.value = t('memberEdit.saved')
  } catch {
    error.value = t('common.error')
  } finally {
    saving.value = false
  }
}

function goBack() {
  router.push({ name: 'members-list' })
}

// -- Former member --
const memberInventory = ref<MyInventoryItem[]>([])
const showFormerModal = ref(false)
const markingFormer = ref(false)
const formerSuccess = ref(false)

const formerBlockReasons = computed(() => {
  const reasons: string[] = []
  if (memberInventory.value.length > 0) {
    reasons.push(t('memberDetail.formerBlockInventory', { count: memberInventory.value.length }))
  }
  const forbidden = [StationUserType.GUARDIAN, StationUserType.MANAGER]
  if (forbidden.includes(editUserType.value as any)) {
    reasons.push(t('memberDetail.formerBlockRole'))
  }
  return reasons
})
const canMarkFormer = computed(() => formerBlockReasons.value.length === 0 && !!member.value)

async function confirmMarkFormer() {
  markingFormer.value = true
  error.value = ''
  try {
    await stationMembers.markFormer(memberId.value)
    formerSuccess.value = true
    showFormerModal.value = false
  } catch {
    error.value = t('common.error')
  } finally {
    markingFormer.value = false
  }
}

onMounted(async () => {
  await loadData()
  try {
    memberInventory.value = await inventory.memberItems(memberId.value)
  } catch { memberInventory.value = [] }
})
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <SecondaryButton :icon="['fas', 'chevron-left']" @click="goBack">
        {{ t('memberEdit.back') }}
      </SecondaryButton>

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="success" variant="success">{{ success }}</Alert>

      <template v-if="!loading && member">
        <SectionHeader>{{ member.name || member.email }}</SectionHeader>

        <!-- Base fields -->
        <NeutralContainer class="space-y-4">
          <SubHeader class="text-sm">{{ t('memberEdit.baseFields') }}</SubHeader>
          <div class="grid gap-4 sm:grid-cols-3">
            <div class="space-y-1">
              <FieldLabel hint>{{ t('memberEdit.firstName') }}</FieldLabel>
              <TextInput v-model="editFirstName" />
            </div>
            <div class="space-y-1">
              <FieldLabel hint>{{ t('memberEdit.lastName') }}</FieldLabel>
              <TextInput v-model="editLastName" />
            </div>
            <div class="space-y-1">
              <FieldLabel hint>{{ t('memberEdit.email') }}</FieldLabel>
              <TextInput v-model="editEmail" />
            </div>
          </div>
        </NeutralContainer>

        <!-- User type -->
        <NeutralContainer class="space-y-3">
          <SubHeader class="text-sm">{{ t('memberEdit.userType') }}</SubHeader>
          <SelectInput :model-value="editUserType" @update:model-value="v => { if (v) onUserTypeChange(v) }" class="max-w-xs">
            <option :value="StationUserType.MEMBER">{{ t('memberEdit.userTypeMember') }}</option>
            <option :value="StationUserType.GUARDIAN">{{ t('memberEdit.userTypeGuardian') }}</option>
            <option :value="StationUserType.TEAM">{{ t('memberEdit.userTypeTeam') }}</option>
            <option :value="StationUserType.MANAGER">{{ t('memberEdit.userTypeManager') }}</option>
            <option :value="StationUserType.TRIAL">{{ t('memberEdit.userTypeTrial') }}</option>
          </SelectInput>
        </NeutralContainer>

        <!-- Permissions -->
        <NeutralContainer class="space-y-3">
          <SubHeader class="text-sm">{{ t('memberEdit.permissions') }}</SubHeader>
          <PermissionPicker v-model="editRoleIds" :all-roles="allRoles" :hidden-permissions="typeGrantedPermissions" />
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
              @click="editGroupIds.has(group.id) ? editGroupIds.delete(group.id) : editGroupIds.add(group.id)"
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
              @click="editTagIds.has(tag.id) ? editTagIds.delete(tag.id) : editTagIds.add(tag.id)"
            >
              {{ tag.name }}
            </button>
            <span v-if="allTags.length === 0" class="text-xs text-(--text-muted)">{{ t('memberEdit.noTags') }}</span>
          </div>
        </NeutralContainer>

        <!-- Profile fields -->
        <NeutralContainer class="space-y-4">
          <SubHeader class="text-sm">{{ t('memberEdit.fields') }}</SubHeader>
          <div class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <THead>
                  <Th class="text-(--text-muted)">{{ t('memberEdit.fieldName') }}</Th>
                  <Th class="text-(--text-muted)">{{ t('memberEdit.fieldValue') }}</Th>
                </THead>
              </thead>
              <tbody>
                <TRow v-for="field in applicableFields" :key="field.id">
                  <Td class="font-medium whitespace-nowrap">{{ field.name }}</Td>
                  <Td>
                    <ProfileFieldInput
                      :field-type="field.fieldType ?? 'text'"
                      :model-value="getEditValue(field.id)"
                      :options="parseConfig(field.config).options as string[]"
                      @update:model-value="setEditValue(field.id, $event)"
                    />
                  </Td>
                </TRow>
              </tbody>
            </table>
          </div>
        </NeutralContainer>

        <!-- Save -->
        <div class="flex items-center justify-between">
          <PrimaryButton :disabled="saving" @click="save">
            {{ saving ? t('common.loading') : t('memberEdit.save') }}
          </PrimaryButton>
          <ErrorButton :icon="['fas', 'user-slash']" v-if="!formerSuccess" @click="showFormerModal = true">
            {{ t('memberDetail.markFormer') }}
          </ErrorButton>
        </div>

        <Alert v-if="formerSuccess" variant="success">{{ t('memberDetail.formerSuccess') }}</Alert>
      </template>

      <!-- Former confirmation modal -->
      <Modal v-model="showFormerModal">
        <div class="space-y-4">
          <SectionHeader>{{ t('memberDetail.markFormerTitle') }}</SectionHeader>
          <template v-if="canMarkFormer">
            <p class="text-sm">{{ t('memberDetail.markFormerConfirm', { name: member?.name || member?.email || '' }) }}</p>
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
  </ViewContent>
</template>

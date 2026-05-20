/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ProfileFieldInput from '@/components/input/ProfileFieldInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type { ProfileField, Role } from '@/api/types'
import { Roles } from '@/api/types'
import { managedMembers, stationMembers } from '@/api'
import type { ManagedMember } from '@/api/managedMembers'
import RoleSelector from '@/components/input/RoleSelector.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

const { t } = useI18n()

const members = ref<ManagedMember[]>([])
const fields = ref<ProfileField[]>([])
const selectedMemberId = ref<string>('')
const values = ref<Map<number, string>>(new Map())
const allRoles = ref<Role[]>([])
const memberRoleIds = ref<Set<number>>(new Set())
const savingRoles = ref(false)
const rolesSuccess = ref('')
const loading = ref(true)
const loadingProfile = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')

const GUARDIAN_ALLOWED_ROLES = [Roles.MEMBER, Roles.GUARDIAN, Roles.LOGIN]

function memberDisplayName(member: ManagedMember): string {
  if (member.name && member.name.trim()) return member.name
  return member.email ?? `#${member.id}`
}

function parseFieldConfig(configStr: string | undefined): { required?: boolean; readonly?: boolean; computed?: boolean; options?: string[]; defaultValue?: unknown } {
  if (!configStr) return {}
  try {
    return JSON.parse(configStr)
  } catch {
    return {}
  }
}

const editableFields = computed(() => {
  return fields.value.filter(f => {
    const config = parseFieldConfig(f.config)
    return !config.computed
  })
})

function isReadonly(field: ProfileField): boolean {
  const config = parseFieldConfig(field.config)
  return !!config.readonly || !!config.computed
}

function getValue(fieldId: number): string {
  return values.value.get(fieldId) ?? ''
}

function setValue(fieldId: number, val: string) {
  const newMap = new Map(values.value)
  newMap.set(fieldId, val)
  values.value = newMap
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [mems, roles] = await Promise.all([
      managedMembers.listManaged(),
      stationMembers.listAllRoles(),
    ])
    members.value = mems
    allRoles.value = roles
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function loadMemberProfile() {
  if (!selectedMemberId.value) return
  loadingProfile.value = true
  error.value = ''
  success.value = ''
  rolesSuccess.value = ''
  try {
    const memberId = Number(selectedMemberId.value)
    const [profile, roles] = await Promise.all([
      managedMembers.getProfile(memberId),
      managedMembers.getRoles(memberId),
    ])
    fields.value = profile.fields
    const map = new Map<number, string>()
    for (const v of profile.values) {
      let val = v.value ?? ''
      try { val = JSON.parse(val) } catch { /* use as-is */ }
      map.set(v.fieldId, typeof val === 'string' ? val : String(val))
    }
    values.value = map
    memberRoleIds.value = new Set(roles.map(r => r.id))
  } catch {
    error.value = t('common.error')
  } finally {
    loadingProfile.value = false
  }
}

async function saveProfile() {
  if (!selectedMemberId.value) return
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    const entries = editableFields.value
      .filter(f => !isReadonly(f))
      .map(f => ({ fieldId: f.id, value: JSON.stringify(getValue(f.id)) }))
    await managedMembers.setProfile(Number(selectedMemberId.value), entries)
    success.value = t('profile.saved')
  } catch {
    error.value = t('common.error')
  } finally {
    saving.value = false
  }
}

async function saveRoles() {
  if (!selectedMemberId.value) return
  savingRoles.value = true
  error.value = ''
  rolesSuccess.value = ''
  try {
    const result = await managedMembers.setRoles(Number(selectedMemberId.value), [...memberRoleIds.value])
    memberRoleIds.value = new Set(result.map(r => r.id))
    rolesSuccess.value = t('profileManaged.rolesSaved')
  } catch {
    error.value = t('common.error')
  } finally {
    savingRoles.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="success" variant="success">{{ success }}</Alert>

      <template v-if="!loading">
        <NeutralContainer class="space-y-4">
          <SectionHeader>{{ t('profileManaged.title') }}</SectionHeader>

          <div v-if="members.length === 0" class="text-center text-(--text-muted) py-4">
            {{ t('profileManaged.noManaged') }}
          </div>

          <div v-else class="space-y-1">
            <label class="block text-sm font-medium">{{ t('profileManaged.selectMember') }}</label>
            <SelectInput v-model="selectedMemberId" @update:model-value="loadMemberProfile">
              <option value="" disabled>{{ t('profileManaged.selectMemberPlaceholder') }}</option>
              <option v-for="member in members" :key="member.id" :value="String(member.id)">
                {{ memberDisplayName(member) }}
              </option>
            </SelectInput>
          </div>
        </NeutralContainer>

        <Spinner v-if="loadingProfile" size="md" />

        <!-- Role management -->
        <NeutralContainer v-if="selectedMemberId && !loadingProfile" class="space-y-4">
          <SubHeader>{{ t('profileManaged.roles') }}</SubHeader>
          <Alert v-if="rolesSuccess" variant="success">{{ rolesSuccess }}</Alert>
          <RoleSelector v-model="memberRoleIds" :all-roles="allRoles" :allowed-roles="GUARDIAN_ALLOWED_ROLES" />
          <PrimaryButton :disabled="savingRoles" @click="saveRoles">
            {{ savingRoles ? t('common.loading') : t('profileManaged.saveRoles') }}
          </PrimaryButton>
        </NeutralContainer>

        <NeutralContainer v-if="selectedMemberId && !loadingProfile && editableFields.length > 0" class="space-y-4">
          <SectionHeader>{{ t('profileManaged.fields') }}</SectionHeader>

          <div v-for="field in editableFields" :key="field.id" class="space-y-1">
            <label class="block text-sm font-medium">
              {{ field.name }}
              <span v-if="parseFieldConfig(field.config).required" class="text-error">*</span>
              <span v-if="isReadonly(field)" class="text-xs text-(--text-muted) ml-1">({{ t('profile.readonlyHint') }})</span>
            </label>

            <ProfileFieldInput
              :field-type="field.fieldType ?? 'text'"
              :model-value="getValue(field.id)"
              :options="(parseFieldConfig(field.config).options as string[]) ?? []"
              :disabled="isReadonly(field)"
              @update:model-value="setValue(field.id, $event)"
            />
          </div>

          <PrimaryButton :disabled="saving" @click="saveProfile">
            {{ saving ? t('common.loading') : t('profile.save') }}
          </PrimaryButton>
        </NeutralContainer>
      </template>
    </div>
  </ViewContent>
</template>

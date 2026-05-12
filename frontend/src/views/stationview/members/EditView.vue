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

import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type { ProfileField, StationMember, Role } from '@/api/types'
import { profileFields, stationMembers, members } from '@/api'
import { useStations } from '@/composables/useStations'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { currentStationId } = useStations()

const memberId = computed(() => Number(route.params.id))

const member = ref<StationMember | null>(null)
const fields = ref<ProfileField[]>([])
const allRoles = ref<Role[]>([])
const editValues = ref<Map<number, string>>(new Map())
const editRoleIds = ref<Set<number>>(new Set())
const editFirstName = ref('')
const editLastName = ref('')
const editEmail = ref('')
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const success = ref('')

function parseConfig(configStr: string | undefined): { options?: string[]; [key: string]: unknown } {
  if (!configStr) return {}
  try { return JSON.parse(configStr) } catch { return {} }
}

// --- Role logic ---

const assignableRoles = computed(() => {
  return allRoles.value.filter(r => ['MEMBER', 'TEAM', 'MEMBER_MANAGER', 'LOGIN',
    'ATTENDENCE_MANAGEMENT', 'INVENTORY_MANAGEMENT', 'EVENT_MANAGEMENT', 'MEMBER_MANAGEMENT', 'MANAGER', 'NEWS_MANAGEMENT'].includes(r.role))
})

const roleHierarchy: Record<string, string[]> = {
  MANAGER: ['TEAM', 'ATTENDENCE_MANAGEMENT', 'INVENTORY_MANAGEMENT', 'EVENT_MANAGEMENT', 'MEMBER_MANAGEMENT'],
  TEAM: ['LOGIN'],
  MEMBER_MANAGER: ['MEMBER', 'LOGIN'],
  ATTENDENCE_MANAGEMENT: ['TEAM', 'LOGIN'],
  INVENTORY_MANAGEMENT: ['TEAM', 'LOGIN'],
  EVENT_MANAGEMENT: ['TEAM', 'LOGIN'],
  MEMBER_MANAGEMENT: ['TEAM', 'LOGIN'],
  NEWS_MANAGEMENT: ['TEAM', 'LOGIN'],
}

const roleConflicts: Record<string, string[]> = {
  MEMBER: ['TEAM', 'MEMBER_MANAGER', 'MANAGER', 'ATTENDENCE_MANAGEMENT', 'INVENTORY_MANAGEMENT', 'EVENT_MANAGEMENT', 'MEMBER_MANAGEMENT', 'NEWS_MANAGEMENT'],
  TEAM: ['MEMBER'],
  MEMBER_MANAGER: ['MEMBER'],
  MANAGER: ['MEMBER'],
  ATTENDENCE_MANAGEMENT: ['MEMBER'],
  INVENTORY_MANAGEMENT: ['MEMBER'],
  EVENT_MANAGEMENT: ['MEMBER'],
  MEMBER_MANAGEMENT: ['MEMBER'],
  NEWS_MANAGEMENT: ['MEMBER'],
}

function getAllChildren(roleName: string): string[] {
  const direct = roleHierarchy[roleName] ?? []
  const all: string[] = [...direct]
  for (const child of direct) { all.push(...getAllChildren(child)) }
  return [...new Set(all)]
}

function isRoleIncluded(roleName: string): boolean {
  for (const selectedId of editRoleIds.value) {
    const selected = allRoles.value.find(r => r.id === selectedId)
    if (!selected) continue
    if (getAllChildren(selected.role).includes(roleName)) return true
  }
  return false
}

function isRoleConflicting(roleName: string): boolean {
  for (const selectedId of editRoleIds.value) {
    const selected = allRoles.value.find(r => r.id === selectedId)
    if (!selected) continue
    if ((roleConflicts[selected.role] ?? []).includes(roleName)) return true
  }
  return false
}

function isRoleDisabled(role: Role): boolean {
  if (isRoleIncluded(role.role)) return true
  if (isRoleConflicting(role.role)) return true
  return false
}

function roleStatusHint(role: Role): string {
  if (isRoleIncluded(role.role)) return t('memberEdit.roleIncluded')
  if (isRoleConflicting(role.role)) return t('memberEdit.roleConflicting')
  return ''
}

function toggleRole(role: Role) {
  if (isRoleDisabled(role)) return
  const newSet = new Set(editRoleIds.value)
  if (newSet.has(role.id)) { newSet.delete(role.id) } else { newSet.add(role.id) }
  editRoleIds.value = newSet
}

const roleFriendlyNames: Record<string, string> = {
  LOGIN: 'Login',
  MEMBER: 'Mitglied',
  TEAM: 'Team',
  MEMBER_MANAGER: 'Mitgliedsmanager',
  ATTENDENCE_MANAGEMENT: 'Anwesenheitsverwaltung',
  INVENTORY_MANAGEMENT: 'Inventarverwaltung',
  EVENT_MANAGEMENT: 'Terminverwaltung',
  MEMBER_MANAGEMENT: 'Mitgliederverwaltung',
  MANAGER: 'Manager',
  NEWS_MANAGEMENT: 'Neuigkeiten',
}

// --- Applicable fields based on roles ---

const applicableFields = computed(() => {
  const roleNames = new Set<string>()
  for (const id of editRoleIds.value) {
    const role = allRoles.value.find(r => r.id === id)
    if (role) roleNames.add(role.role)
  }
  // Also include children of selected roles (for role UI logic)
  const expandedNames = new Set(roleNames)
  for (const name of roleNames) {
    for (const child of getAllChildren(name)) { expandedNames.add(child) }
  }

  // Scope resolution uses actual assigned roles only (not hierarchy children),
  // matching the backend logic in ManagedMemberRoutes.applicableScopes
  const scopes: string[] = []
  if (roleNames.has('MEMBER')) scopes.push('MEMBER')
  if (roleNames.has('TEAM') || roleNames.has('MANAGER') ||
      roleNames.has('ATTENDENCE_MANAGEMENT') || roleNames.has('INVENTORY_MANAGEMENT') ||
      roleNames.has('EVENT_MANAGEMENT') || roleNames.has('MEMBER_MANAGEMENT')) scopes.push('TEAM')
  if (roleNames.has('MEMBER_MANAGER')) scopes.push('MEMBER_MANAGER')

  return fields.value.filter(f => {
    if (f.scope === 'GROUP') return false
    return scopes.includes(f.scope ?? 'MEMBER')
  })
})

// --- Field values ---

function getEditValue(fieldId: number): string {
  return editValues.value.get(fieldId) ?? ''
}

function setEditValue(fieldId: number, val: string) {
  editValues.value = new Map([...editValues.value, [fieldId, val]])
}

// --- Load / Save ---

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [allFields, allMembers, roles, memberRoles, profileValues] = await Promise.all([
      profileFields.listFields(),
      stationMembers.listMembers(currentStationId.value!),
      stationMembers.listAllRoles(),
      stationMembers.getRoles(memberId.value),
      profileFields.getValues(memberId.value),
    ])
    fields.value = allFields
    allRoles.value = roles
    member.value = allMembers.find(m => m.id === memberId.value) ?? null
    editFirstName.value = member.value?.name?.split(' ')[0] ?? ''
    editLastName.value = member.value?.name?.split(' ').slice(1).join(' ') ?? ''
    editEmail.value = member.value?.email ?? ''
    editRoleIds.value = new Set(memberRoles.map(r => r.id))

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
    await stationMembers.setRoles(memberId.value, { roleIds: [...editRoleIds.value] })
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

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <SecondaryButton @click="goBack">
        <font-awesome-icon :icon="['fas', 'chevron-left']" class="mr-2" />
        {{ t('memberEdit.back') }}
      </SecondaryButton>

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="success" variant="success">{{ success }}</Alert>

      <template v-if="!loading && member">
        <SectionHeader>{{ member.name || member.email }}</SectionHeader>

        <!-- Base fields -->
        <NeutralContainer class="space-y-4">
          <h3 class="text-sm font-semibold">{{ t('memberEdit.baseFields') }}</h3>
          <div class="grid gap-4 sm:grid-cols-3">
            <div class="space-y-1">
              <label class="block text-xs font-medium text-(--text-muted)">{{ t('memberEdit.firstName') }}</label>
              <TextInput v-model="editFirstName" />
            </div>
            <div class="space-y-1">
              <label class="block text-xs font-medium text-(--text-muted)">{{ t('memberEdit.lastName') }}</label>
              <TextInput v-model="editLastName" />
            </div>
            <div class="space-y-1">
              <label class="block text-xs font-medium text-(--text-muted)">{{ t('memberEdit.email') }}</label>
              <TextInput v-model="editEmail" />
            </div>
          </div>
        </NeutralContainer>

        <!-- Roles -->
        <NeutralContainer class="space-y-3">
          <h3 class="text-sm font-semibold">{{ t('memberEdit.roles') }}</h3>
          <div class="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
            <div
              v-for="role in assignableRoles"
              :key="role.id"
              class="flex items-center justify-between rounded-lg px-3 py-2.5 border cursor-pointer transition-all"
              :class="{
                'border-primary bg-primary/10 ring-2 ring-primary/30': editRoleIds.has(role.id),
                'opacity-40 cursor-not-allowed': isRoleDisabled(role) && !editRoleIds.has(role.id),
                'border-bg-light-accent dark:border-bg-dark-accent hover:border-primary': !editRoleIds.has(role.id) && !isRoleDisabled(role),
              }"
              @click="toggleRole(role)"
            >
              <div>
                <span class="text-sm font-medium">{{ roleFriendlyNames[role.role] ?? role.role }}</span>
                <p v-if="roleStatusHint(role)" class="text-xs text-(--text-muted)">{{ roleStatusHint(role) }}</p>
              </div>
              <font-awesome-icon v-if="editRoleIds.has(role.id)" :icon="['fas', 'check']" class="text-primary" />
            </div>
          </div>
        </NeutralContainer>

        <!-- Profile fields -->
        <NeutralContainer class="space-y-4">
          <h3 class="text-sm font-semibold">{{ t('memberEdit.fields') }}</h3>
          <div class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent text-left">
                  <th class="px-3 py-2 font-medium text-(--text-muted)">{{ t('memberEdit.fieldName') }}</th>
                  <th class="px-3 py-2 font-medium text-(--text-muted)">{{ t('memberEdit.fieldValue') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="field in applicableFields" :key="field.id" class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
                  <td class="px-3 py-2.5 font-medium whitespace-nowrap">{{ field.name }}</td>
                  <td class="px-3 py-2.5">
                    <ProfileFieldInput
                      :field-type="field.fieldType ?? 'text'"
                      :model-value="getEditValue(field.id)"
                      :options="parseConfig(field.config).options as string[]"
                      @update:model-value="setEditValue(field.id, $event)"
                    />
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </NeutralContainer>

        <!-- Save -->
        <PrimaryButton :disabled="saving" @click="save">
          {{ saving ? t('common.loading') : t('memberEdit.save') }}
        </PrimaryButton>
      </template>
    </div>
  </ViewContent>
</template>

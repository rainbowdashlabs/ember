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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import ChangeHistory from './detailview/ChangeHistory.vue'
import type { ProfileField, ProfileFieldChange, StationMember } from '@/api/types'
import { profileFields, profileFieldChanges, stationMembers, members } from '@/api'
import { useStations } from '@/composables/useStations'
import { useSession } from '@/composables/useSession'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { currentStationId } = useStations()
const { sessionInfo, canManageMembers, isMemberManager } = useSession()

const memberId = computed(() => Number(route.params.id))
const currentMemberId = computed(() => sessionInfo.value?.member?.id ?? 0)
const showChangeHistory = computed(() => canManageMembers() || isMemberManager())

const member = ref<StationMember | null>(null)
const fields = ref<ProfileField[]>([])
const values = ref<Map<number, string>>(new Map())
const memberRoles = ref<string[]>([])
const managers = ref<StationMember[]>([])
const managerValues = ref<Map<number, Map<number, string>>>(new Map())
const managerRoles = ref<Map<number, string[]>>(new Map())
const allMembers = ref<StationMember[]>([])
const changes = ref<ProfileFieldChange[]>([])
const loading = ref(true)
const error = ref('')

// Link manager
const showLinkManager = ref(false)
const selectedManagerId = ref('')

// Create manager
const showCreateManager = ref(false)
const newMgrFirstName = ref('')
const newMgrLastName = ref('')
const newMgrEmail = ref('')
const creatingManager = ref(false)

const TEAM_ROLES = ['TEAM', 'MANAGER', 'ADMIN', 'ATTENDENCE_MANAGEMENT', 'INVENTORY_MANAGEMENT',
  'EVENT_MANAGEMENT', 'MEMBER_MANAGEMENT']

const applicableFields = computed(() => {
  const scopes: string[] = []
  if (memberRoles.value.includes('MEMBER')) scopes.push('MEMBER')
  if (memberRoles.value.some(r => TEAM_ROLES.includes(r))) scopes.push('TEAM')
  if (memberRoles.value.includes('MEMBER_MANAGER')) scopes.push('MEMBER_MANAGER')
  return fields.value.filter(f => {
    if (f.scope === 'GROUP') return false
    return scopes.includes(f.scope ?? 'MEMBER')
  })
})

const availableManagers = computed(() => {
  const managerIds = new Set(managers.value.map(m => m.id))
  managerIds.add(memberId.value)
  return allMembers.value.filter(m => !managerIds.has(m.id))
})

function memberDisplayName(m: StationMember): string {
  return m.name && m.name.trim() ? m.name : m.email ?? `#${m.id}`
}

function getFieldValue(fieldId: number): string {
  const raw = values.value.get(fieldId) ?? ''
  try { return JSON.parse(raw) } catch { return raw }
}

function getManagerFieldValue(mgrId: number, fieldId: number): string {
  const vals = managerValues.value.get(mgrId)
  if (!vals) return ''
  const raw = vals.get(fieldId) ?? ''
  try { return JSON.parse(raw) } catch { return raw }
}

function getManagerFields(mgrId: number): typeof fields.value {
  const roles = managerRoles.value.get(mgrId) ?? []
  const scopes: string[] = []
  if (roles.includes('MEMBER')) scopes.push('MEMBER')
  if (roles.some(r => TEAM_ROLES.includes(r))) scopes.push('TEAM')
  if (roles.includes('MEMBER_MANAGER')) scopes.push('MEMBER_MANAGER')
  return fields.value.filter(f => {
    if (f.scope === 'GROUP') return false
    return scopes.includes(f.scope ?? 'MEMBER')
  })
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [allFields, allMems, roles, profileValues, mgrs] = await Promise.all([
      profileFields.listFields(),
      stationMembers.listMembers(currentStationId.value!),
      stationMembers.getRoles(memberId.value),
      profileFields.getValues(memberId.value),
      stationMembers.getManagers(memberId.value),
    ])
    fields.value = allFields
    allMembers.value = allMems
    member.value = allMems.find(m => m.id === memberId.value) ?? null
    memberRoles.value = roles.map(r => r.role)
    managers.value = mgrs

    const map = new Map<number, string>()
    for (const v of profileValues) {
      map.set(v.fieldId, v.value ?? '')
    }
    values.value = map

    // Load manager details and change history
    await loadManagerDetails(mgrs)
    if (showChangeHistory.value) {
      try {
        changes.value = await profileFieldChanges.getChanges(memberId.value)
      } catch { /* ignore if unauthorized */ }
    }
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function loadManagerDetails(mgrs: StationMember[]) {
  const mgrVals = new Map<number, Map<number, string>>()
  const mgrRoles = new Map<number, string[]>()
  for (const mgr of mgrs) {
    try {
      const [vals, roles] = await Promise.all([
        profileFields.getValues(mgr.id),
        stationMembers.getRoles(mgr.id),
      ])
      const fieldMap = new Map<number, string>()
      for (const v of vals) { fieldMap.set(v.fieldId, v.value ?? '') }
      mgrVals.set(mgr.id, fieldMap)
      mgrRoles.set(mgr.id, roles.map(r => r.role))
    } catch { /* skip */ }
  }
  managerValues.value = mgrVals
  managerRoles.value = mgrRoles
}

async function linkManager() {
  if (!selectedManagerId.value) return
  error.value = ''
  try {
    const currentIds = managers.value.map(m => m.id)
    await stationMembers.setManagers(memberId.value, { managerIds: [...currentIds, Number(selectedManagerId.value)] })
    managers.value = await stationMembers.getManagers(memberId.value)
    await loadManagerDetails(managers.value)
    showLinkManager.value = false
    selectedManagerId.value = ''
  } catch {
    error.value = t('common.error')
  }
}

async function removeManager(mgrId: number) {
  error.value = ''
  try {
    const newIds = managers.value.filter(m => m.id !== mgrId).map(m => m.id)
    await stationMembers.setManagers(memberId.value, { managerIds: newIds })
    managers.value = await stationMembers.getManagers(memberId.value)
  } catch {
    error.value = t('common.error')
  }
}

async function createNewManager() {
  creatingManager.value = true
  error.value = ''
  try {
    const invited = await members.invite({
      email: newMgrEmail.value,
      firstName: newMgrFirstName.value,
      lastName: newMgrLastName.value,
    })
    // Find new member and assign as manager
    const updatedMembers = await stationMembers.listMembers(currentStationId.value!)
    const newMember = updatedMembers.find(m => m.accountId === invited.id)
    if (newMember) {
      const currentIds = managers.value.map(m => m.id)
      await stationMembers.setManagers(memberId.value, { managerIds: [...currentIds, newMember.id] })
      // Assign member_manager role to new account
      const allRoles = await stationMembers.listAllRoles()
      const mgrRoleIds = allRoles.filter(r => ['LOGIN', 'MEMBER_MANAGER'].includes(r.role)).map(r => r.id)
      await stationMembers.setRoles(newMember.id, { roleIds: mgrRoleIds })

      managers.value = await stationMembers.getManagers(memberId.value)
      await loadManagerDetails(managers.value)
      allMembers.value = updatedMembers
    }
    showCreateManager.value = false
    newMgrFirstName.value = ''
    newMgrLastName.value = ''
    newMgrEmail.value = ''
  } catch {
    error.value = t('common.error')
  } finally {
    creatingManager.value = false
  }
}

async function loadChanges() {
  try {
    changes.value = await profileFieldChanges.getChanges(memberId.value)
  } catch { /* ignore */ }
}

function goBack() {
  router.push({ name: 'members-list' })
}

function goToEdit() {
  router.push({ name: 'members-edit', params: { id: memberId.value } })
}

function goToEditManager(mgrId: number) {
  router.push({ name: 'members-edit', params: { id: mgrId } })
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <SecondaryButton @click="goBack">
          <font-awesome-icon :icon="['fas', 'chevron-left']" class="mr-2" />
          {{ t('memberDetail.back') }}
        </SecondaryButton>
        <PrimaryButton @click="goToEdit">
          <font-awesome-icon :icon="['fas', 'pen']" class="mr-2" />
          {{ t('memberDetail.edit') }}
        </PrimaryButton>
      </div>

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && member">
        <SectionHeader>{{ memberDisplayName(member) }}</SectionHeader>
        <p v-if="member.email" class="text-sm text-(--text-muted)">{{ member.email }}</p>

        <!-- Profile fields -->
        <NeutralContainer class="space-y-3">
          <h3 class="text-sm font-semibold">{{ t('memberDetail.fields') }}</h3>
          <div v-if="applicableFields.length === 0" class="text-(--text-muted) text-sm py-2">
            {{ t('memberDetail.noFields') }}
          </div>
          <div class="grid gap-2 sm:grid-cols-2">
            <div v-for="field in applicableFields" :key="field.id" class="text-sm">
              <span class="text-(--text-muted)">{{ field.name }}:</span>
              <span class="ml-1 font-medium">{{ getFieldValue(field.id) || '–' }}</span>
            </div>
          </div>
        </NeutralContainer>

        <!-- Managers -->
        <NeutralContainer class="space-y-4">
          <div class="flex items-center justify-between">
            <h3 class="text-sm font-semibold">{{ t('memberDetail.managers') }}</h3>
            <div class="flex items-center gap-2">
              <SecondaryButton class="text-sm" @click="showLinkManager = !showLinkManager">
                <font-awesome-icon :icon="['fas', 'link']" class="mr-1" />
                {{ t('memberDetail.linkManager') }}
              </SecondaryButton>
              <SecondaryButton class="text-sm" @click="showCreateManager = !showCreateManager">
                <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
                {{ t('memberDetail.createManager') }}
              </SecondaryButton>
            </div>
          </div>

          <div v-if="managers.length === 0" class="text-(--text-muted) text-sm py-2">
            {{ t('memberDetail.noManagers') }}
          </div>

          <div class="space-y-3">
            <div v-for="mgr in managers" :key="mgr.id" class="rounded-lg px-4 py-3 bg-bg-light-accent/30 dark:bg-bg-dark-accent/30 space-y-2">
              <div class="flex items-center justify-between">
                <div>
                  <span class="font-semibold">{{ memberDisplayName(mgr) }}</span>
                  <span v-if="mgr.email" class="ml-2 text-xs text-(--text-muted)">{{ mgr.email }}</span>
                </div>
                <div class="flex items-center gap-2">
                  <EditButton @click="goToEditManager(mgr.id)" />
                  <DeleteButton @click="removeManager(mgr.id)" />
                </div>
              </div>
              <div v-if="getManagerFields(mgr.id).length > 0" class="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
                <div v-for="field in getManagerFields(mgr.id)" :key="field.id" class="text-sm">
                  <span class="text-(--text-muted)">{{ field.name }}:</span>
                  <span class="ml-1 font-medium">{{ getManagerFieldValue(mgr.id, field.id) || '–' }}</span>
                </div>
              </div>
            </div>
          </div>

          <!-- Link existing manager -->
          <div v-if="showLinkManager" class="space-y-2 pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent">
            <label class="block text-sm font-medium">{{ t('memberDetail.selectManager') }}</label>
            <div class="flex gap-2">
              <SelectInput v-model="selectedManagerId" class="flex-1">
                <option value="" disabled>{{ t('memberDetail.selectManagerPlaceholder') }}</option>
                <option v-for="m in availableManagers" :key="m.id" :value="String(m.id)">{{ memberDisplayName(m) }}</option>
              </SelectInput>
              <PrimaryButton :disabled="!selectedManagerId" @click="linkManager">
                {{ t('memberDetail.assign') }}
              </PrimaryButton>
            </div>
          </div>

          <!-- Create new manager -->
          <div v-if="showCreateManager" class="space-y-3 pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent">
            <label class="block text-sm font-medium">{{ t('memberDetail.createManagerTitle') }}</label>
            <div class="grid gap-3 sm:grid-cols-3">
              <TextInput v-model="newMgrFirstName" :placeholder="t('memberDetail.firstName')" />
              <TextInput v-model="newMgrLastName" :placeholder="t('memberDetail.lastName')" />
              <TextInput v-model="newMgrEmail" :placeholder="t('memberDetail.email')" />
            </div>
            <SecondaryButton :disabled="!newMgrFirstName || !newMgrLastName || !newMgrEmail || creatingManager" @click="createNewManager">
              <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
              {{ creatingManager ? t('common.loading') : t('memberDetail.createManagerSubmit') }}
            </SecondaryButton>
          </div>
        </NeutralContainer>

        <!-- Change History -->
        <ChangeHistory
          v-if="showChangeHistory"
          :member-id="memberId"
          :changes="changes"
          :current-member-id="currentMemberId"
          @reload="loadChanges"
        />
      </template>
    </div>
  </ViewContent>
</template>

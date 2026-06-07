/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type { StationMember } from '@/api/types'
import { StationUserType } from '@/api/types'
import { stationMembers } from '@/api'

const props = defineProps<{
  memberId: number
  userType: string
  allMembers: StationMember[]
}>()

const { t } = useI18n()

const managers = ref<StationMember[]>([])
const managed = ref<StationMember[]>([])
const loading = ref(true)
const error = ref('')
const success = ref('')
const saving = ref(false)
const selectedAddId = ref('')

const isGuardian = computed(() => props.userType === StationUserType.GUARDIAN)

const availableToAdd = computed(() => {
  const linkedIds = new Set(isGuardian.value ? managed.value.map(m => m.id) : managers.value.map(m => m.id))
  linkedIds.add(props.memberId)
  return props.allMembers.filter(m => {
    if (linkedIds.has(m.id)) return false
    if (m.formerAt) return false
    if (isGuardian.value) {
      return m.userType === StationUserType.MEMBER || m.userType === StationUserType.TRIAL
    }
    return m.userType === StationUserType.GUARDIAN
  })
})

function displayName(m: StationMember): string {
  return m.name && m.name.trim() ? m.name : m.email ?? `#${m.id}`
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [mgrs, mgd] = await Promise.all([
      stationMembers.getManagers(props.memberId),
      stationMembers.getManaged(props.memberId),
    ])
    managers.value = mgrs
    managed.value = mgd
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function addRelation() {
  const id = Number(selectedAddId.value)
  if (!id) return
  saving.value = true
  error.value = ''
  try {
    if (isGuardian.value) {
      const ids = [...managed.value.map(m => m.id), id]
      managed.value = await stationMembers.setManaged(props.memberId, ids)
    } else {
      const ids = [...managers.value.map(m => m.id), id]
      managers.value = await stationMembers.setManagers(props.memberId, { managerIds: ids })
    }
    selectedAddId.value = ''
    success.value = t('memberEdit.relations.saved')
    setTimeout(() => { success.value = '' }, 3000)
  } catch {
    error.value = t('common.error')
  } finally {
    saving.value = false
  }
}

async function removeRelation(targetId: number) {
  saving.value = true
  error.value = ''
  try {
    if (isGuardian.value) {
      const ids = managed.value.filter(m => m.id !== targetId).map(m => m.id)
      managed.value = await stationMembers.setManaged(props.memberId, ids)
    } else {
      const ids = managers.value.filter(m => m.id !== targetId).map(m => m.id)
      managers.value = await stationMembers.setManagers(props.memberId, { managerIds: ids })
    }
    success.value = t('memberEdit.relations.saved')
    setTimeout(() => { success.value = '' }, 3000)
  } catch {
    error.value = t('common.error')
  } finally {
    saving.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <div class="space-y-6">
    <Spinner v-if="loading" size="md" />
    <Alert v-if="error" variant="error">{{ error }}</Alert>
    <Alert v-if="success" variant="success">{{ success }}</Alert>

    <template v-if="!loading">
      <!-- Guardians of this member (for non-guardian types) -->
      <NeutralContainer v-if="!isGuardian" class="space-y-4">
        <SubHeader>{{ t('memberEdit.relations.guardians') }}</SubHeader>
        <EmptyState v-if="managers.length === 0" compact>{{ t('memberEdit.relations.noGuardians') }}</EmptyState>
        <div v-for="mgr in managers" :key="mgr.id" class="flex items-center justify-between rounded-lg px-4 py-3 bg-bg-light-accent/40 dark:bg-bg-dark-accent/40">
          <div>
            <span class="font-medium">{{ displayName(mgr) }}</span>
            <span v-if="mgr.email" class="ml-2 text-sm text-(--text-muted)">{{ mgr.email }}</span>
          </div>
          <DeleteButton @click="removeRelation(mgr.id)" />
        </div>
        <div class="flex items-end gap-2">
          <div class="flex-1 space-y-1">
            <FieldLabel>{{ t('memberEdit.relations.addGuardian') }}</FieldLabel>
            <SelectInput v-model="selectedAddId">
              <option value="">{{ t('memberEdit.relations.selectMember') }}</option>
              <option v-for="m in availableToAdd" :key="m.id" :value="String(m.id)">{{ displayName(m) }}</option>
            </SelectInput>
          </div>
          <PrimaryButton :icon="['fas', 'plus']" :disabled="!selectedAddId || saving" @click="addRelation">
            {{ t('common.add') }}
          </PrimaryButton>
        </div>
      </NeutralContainer>

      <!-- Members managed by this guardian -->
      <NeutralContainer v-if="isGuardian" class="space-y-4">
        <SubHeader>{{ t('memberEdit.relations.managedMembers') }}</SubHeader>
        <EmptyState v-if="managed.length === 0" compact>{{ t('memberEdit.relations.noManagedMembers') }}</EmptyState>
        <div v-for="m in managed" :key="m.id" class="flex items-center justify-between rounded-lg px-4 py-3 bg-bg-light-accent/40 dark:bg-bg-dark-accent/40">
          <div>
            <span class="font-medium">{{ displayName(m) }}</span>
            <span v-if="m.email" class="ml-2 text-sm text-(--text-muted)">{{ m.email }}</span>
          </div>
          <DeleteButton @click="removeRelation(m.id)" />
        </div>
        <div class="flex items-end gap-2">
          <div class="flex-1 space-y-1">
            <FieldLabel>{{ t('memberEdit.relations.addManagedMember') }}</FieldLabel>
            <SelectInput v-model="selectedAddId">
              <option value="">{{ t('memberEdit.relations.selectMember') }}</option>
              <option v-for="m in availableToAdd" :key="m.id" :value="String(m.id)">{{ displayName(m) }}</option>
            </SelectInput>
          </div>
          <PrimaryButton :icon="['fas', 'plus']" :disabled="!selectedAddId || saving" @click="addRelation">
            {{ t('common.add') }}
          </PrimaryButton>
        </div>
      </NeutralContainer>

      <!-- Cross-reference: show the other side too -->
      <NeutralContainer v-if="isGuardian && managers.length > 0" class="space-y-4">
        <SubHeader>{{ t('memberEdit.relations.guardians') }}</SubHeader>
        <div v-for="mgr in managers" :key="mgr.id" class="flex items-center rounded-lg px-4 py-3 bg-bg-light-accent/40 dark:bg-bg-dark-accent/40">
          <span class="font-medium">{{ displayName(mgr) }}</span>
          <span v-if="mgr.email" class="ml-2 text-sm text-(--text-muted)">{{ mgr.email }}</span>
        </div>
      </NeutralContainer>

      <NeutralContainer v-if="!isGuardian && managed.length > 0" class="space-y-4">
        <SubHeader>{{ t('memberEdit.relations.managedMembers') }}</SubHeader>
        <div v-for="m in managed" :key="m.id" class="flex items-center rounded-lg px-4 py-3 bg-bg-light-accent/40 dark:bg-bg-dark-accent/40">
          <span class="font-medium">{{ displayName(m) }}</span>
          <span v-if="m.email" class="ml-2 text-sm text-(--text-muted)">{{ m.email }}</span>
        </div>
      </NeutralContainer>
    </template>
  </div>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import {stationManage, stationMembers} from '@/api'

const props = defineProps<{
  stationId: string
  ownerMemberId: number | null
}>()

const emit = defineEmits<{
  error: [msg: string]
  success: [msg: string]
  ownerChanged: []
}>()

const {t} = useI18n()

// -- Owner handover --
const managerMembers = ref<{id: number, name: string}[]>([])
const newOwnerId = ref('')
const transferringOwnership = ref(false)

async function loadManagers() {
  try {
    const members = await stationMembers.listMembers()
    const allRolesList = await stationMembers.listAllRoles()
    const managerRoleId = allRolesList.find(r => r.role === 'MANAGER')?.id
    if (!managerRoleId) return
    const result: {id: number, name: string}[] = []
    for (const m of members) {
      if (props.ownerMemberId && m.id === props.ownerMemberId) continue
      const roles = await stationMembers.getRoles(m.id)
      if (roles.some(r => r.id === managerRoleId)) {
        result.push({id: m.id, name: m.name || m.email || `#${m.id}`})
      }
    }
    managerMembers.value = result
  } catch { /* ignore */ }
}

async function transferOwnershipAction() {
  const id = Number(newOwnerId.value)
  if (!id) return
  transferringOwnership.value = true
  try {
    await stationManage.transferOwnership(id)
    emit('success', t('stationManage.ownerHandoverSuccess'))
    emit('ownerChanged')
  } catch {
    emit('error', t('common.error'))
  } finally {
    transferringOwnership.value = false
  }
}

// -- Station deletion --
const requestingDelete = ref(false)

async function requestDelete() {
  requestingDelete.value = true
  try {
    await stationManage.requestStationDeletion()
    emit('success', t('stationManage.deleteRequested'))
  } catch {
    emit('error', t('common.error'))
  } finally {
    requestingDelete.value = false
  }
}

onMounted(loadManagers)
</script>

<template>
  <!-- Owner handover -->
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('stationManage.ownerHandoverTitle') }}</SectionHeader>
    <p class="text-sm text-(--text-muted)">{{ t('stationManage.ownerHandoverHint') }}</p>
    <div v-if="managerMembers.length > 0" class="space-y-3">
      <SelectInput v-model="newOwnerId">
        <option value="">{{ t('stationManage.ownerHandoverSelect') }}</option>
        <option v-for="m in managerMembers" :key="m.id" :value="m.id">{{ m.name }}</option>
      </SelectInput>
      <PrimaryButton :disabled="transferringOwnership || !newOwnerId" @click="transferOwnershipAction">
        {{ transferringOwnership ? t('common.loading') : t('stationManage.ownerHandoverSubmit') }}
      </PrimaryButton>
    </div>
    <p v-else class="text-sm text-(--text-muted)">{{ t('stationManage.ownerHandoverNone') }}</p>
  </NeutralContainer>

  <!-- Station deletion -->
  <ErrorContainer class="space-y-4">
    <SectionHeader>{{ t('stationManage.deleteTitle') }}</SectionHeader>
    <p class="text-sm text-(--text-muted)">{{ t('stationManage.deleteHint') }}</p>
    <ErrorButton :disabled="requestingDelete" @click="requestDelete">
      {{ requestingDelete ? t('stationManage.deleteRequesting') : t('stationManage.deleteRequest') }}
    </ErrorButton>
  </ErrorContainer>
</template>

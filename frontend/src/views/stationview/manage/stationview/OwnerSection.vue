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
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import {stationManage, stationMembers, transfer} from '@/api'

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

// -- Transfer --
const creatingToken = ref(false)
const transferToken = ref('')

async function createToken() {
  creatingToken.value = true
  transferToken.value = ''
  try {
    const result = await transfer.createTransferToken()
    transferToken.value = result.token
  } catch {
    emit('error', t('common.error'))
  } finally {
    creatingToken.value = false
  }
}

async function copyToken() {
  await navigator.clipboard.writeText(transferToken.value)
  emit('success', t('stationManage.transferTokenCopied'))
}

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
  <!-- Transfer -->
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('stationManage.transferTitle') }}</SectionHeader>
    <p class="text-sm text-(--text-muted)">{{ t('stationManage.transferHint') }}</p>
    <PrimaryButton :disabled="creatingToken" @click="createToken">
      {{ creatingToken ? t('stationManage.transferCreating') : t('stationManage.transferCreate') }}
    </PrimaryButton>
    <div v-if="transferToken" class="space-y-2">
      <FieldLabel>{{ t('stationManage.transferTokenLabel') }}</FieldLabel>
      <div class="flex items-center gap-2">
        <code class="flex-1 rounded bg-bg-light-accent dark:bg-bg-dark-accent px-3 py-2 text-sm break-all select-all">{{ transferToken }}</code>
        <SecondaryButton @click="copyToken">
          <font-awesome-icon :icon="['fas', 'copy']" />
        </SecondaryButton>
      </div>
    </div>
  </NeutralContainer>

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

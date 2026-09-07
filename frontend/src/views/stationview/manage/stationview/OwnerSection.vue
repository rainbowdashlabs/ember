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
import {StationPermission} from '@/api/types'
import {useAsyncAction} from '@/composables/useAsyncAction'

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

interface ManagerOption {
  id: number
  name: string
}

const managerMembers = ref<ManagerOption[]>([])
const newOwnerId = ref('')
const loadFailed = ref(false)

/**
 * Collects the members a station can be handed to, which are those holding the station
 * administrator permission.
 *
 * <p>All the roles arrive in one call rather than one per member, because a station of a few
 * hundred people would otherwise ask a few hundred times to fill a single list.
 */
async function loadManagers() {
  loadFailed.value = false
  try {
    const [members, allRolesList, rolesByMember] = await Promise.all([
      stationMembers.listMembers(),
      stationMembers.listAllPermissions(),
      stationMembers.getAllMemberRoles(),
    ])
    const managerRoleId = allRolesList.find(r => r.permission === StationPermission.STATION_ADMINISTRATOR)?.id
    if (!managerRoleId) {
      loadFailed.value = true
      return
    }
    managerMembers.value = members
        .filter(m => m.id !== props.ownerMemberId)
        .filter(m => (rolesByMember[m.id] ?? []).some(r => r.id === managerRoleId))
        .map(m => ({id: m.id, name: m.name || m.email || `#${m.id}`}))
  } catch {
    loadFailed.value = true
  }
}

const {running: transferringOwnership, error: transferError, run: runTransferOwnership} = useAsyncAction(
    async (id: number) => {
      await stationManage.transferOwnership(id)
      emit('success', t('stationManage.ownerHandoverSuccess'))
      emit('ownerChanged')
    },
)

async function transferOwnershipAction() {
  const id = Number(newOwnerId.value)
  if (!id) return
  await runTransferOwnership(id)
  if (transferError.value) emit('error', transferError.value)
}

const {running: requestingDelete, error: deleteError, run: runRequestDelete} = useAsyncAction(async () => {
  const result = await stationManage.requestStationDeletion()
  emit('success', result.deleted ? t('stationManage.deleteDone') : t('stationManage.deleteRequested'))
})

async function requestDelete() {
  await runRequestDelete()
  if (deleteError.value) emit('error', deleteError.value)
}

onMounted(loadManagers)
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('stationManage.ownerHandoverTitle') }}</SectionHeader>
    <p class="text-sm text-(--text-muted)">{{ t('stationManage.ownerHandoverHint') }}</p>
    <div v-if="managerMembers.length > 0" class="space-y-3">
      <SelectInput v-model="newOwnerId" class="w-full">
        <option value="">{{ t('stationManage.ownerHandoverSelect') }}</option>
        <option v-for="m in managerMembers" :key="m.id" :value="m.id">{{ m.name }}</option>
      </SelectInput>
      <PrimaryButton :disabled="transferringOwnership || !newOwnerId" @click="transferOwnershipAction">
        {{ transferringOwnership ? t('common.loading') : t('stationManage.ownerHandoverSubmit') }}
      </PrimaryButton>
    </div>
    <p v-else-if="loadFailed" class="text-sm text-(--text-muted)">{{ t('stationManage.ownerHandoverLoadFailed') }}</p>
    <p v-else class="text-sm text-(--text-muted)">{{ t('stationManage.ownerHandoverNone') }}</p>
  </NeutralContainer>

  <ErrorContainer class="space-y-4">
    <SectionHeader>{{ t('stationManage.deleteTitle') }}</SectionHeader>
    <p class="text-sm text-(--text-muted)">{{ t('stationManage.deleteHint') }}</p>
    <ErrorButton :disabled="requestingDelete" @click="requestDelete">
      {{ requestingDelete ? t('stationManage.deleteRequesting') : t('stationManage.deleteRequest') }}
    </ErrorButton>
  </ErrorContainer>
</template>

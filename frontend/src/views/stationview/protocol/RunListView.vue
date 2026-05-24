/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import Modal from '@/components/feedback/Modal.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import { useSession } from '@/composables/useSession'
import { protocol, stationMembers } from '@/api'
import type { TestProtocol, TestProtocolRun } from '@/api/protocol'
import type { StationMember } from '@/api/types'

const { t } = useI18n()
const router = useRouter()
const { canManageProtocol, loaded } = useSession()


const runs = ref<TestProtocolRun[]>([])
const protocols = ref<TestProtocol[]>([])
const members = ref<StationMember[]>([])
const loading = ref(true)
const error = ref('')

// Create modal
const showCreateModal = ref(false)
const newProtocolId = ref<string>('')
const newName = ref('')
const newDate = ref(new Date().toISOString().split('T')[0])
const selectedMemberIds = ref<number[]>([])

async function loadData() {
  loading.value = true
  try {
    const [r, p, m] = await Promise.all([
      protocol.listRuns(),
      protocol.listProtocols(),
      stationMembers.listMembers(),
    ])
    runs.value = r
    protocols.value = Array.isArray(p) ? p : (p.protocols ?? [])
    members.value = m
  } catch { error.value = t('common.error') }
  finally { loading.value = false }
}

function protocolName(id: number) { return protocols.value.find(p => p.id === id)?.name ?? '?' }

async function handleCreate() {
  if (!newProtocolId.value || !newName.value.trim()) return
  try {
    const run = await protocol.createRun(Number(newProtocolId.value), {
      name: newName.value.trim(),
      testDate: newDate.value,
      memberIds: selectedMemberIds.value,
    })
    showCreateModal.value = false
    router.push({ name: 'protocol-run-detail', params: { id: run.id } })
  } catch { error.value = t('common.error') }
}

function toggleMember(id: number) {
  const idx = selectedMemberIds.value.indexOf(id)
  if (idx >= 0) selectedMemberIds.value.splice(idx, 1)
  else selectedMemberIds.value.push(id)
}

watch(loaded, (v) => { if (v) loadData() }, { immediate: true })
onMounted(() => { if (loaded.value) loadData() })
</script>

<template>
  <ViewContent>
    <div class="flex items-center justify-between mb-4">
      <SectionHeader>{{ t('protocol.runs') }}</SectionHeader>
      <PrimaryButton v-if="canManageProtocol()" @click="showCreateModal = true">
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" /> {{ t('protocol.createRun') }}
      </PrimaryButton>
    </div>

    <Spinner v-if="loading" />
    <Alert v-if="error" variant="error">{{ error }}</Alert>

    <div v-if="!loading && runs.length === 0" class="text-center text-[var(--text-muted)] py-8">
      {{ t('protocol.noRuns') }}
    </div>

    <div class="space-y-2">
      <NeutralContainer
        v-for="run in runs"
        :key="run.id"
        class="flex items-center gap-3 cursor-pointer hover:border-[var(--primary)] transition-colors"
        @click="router.push({ name: 'protocol-run-detail', params: { id: run.id } })"
      >
        <div class="flex-1 min-w-0">
          <div class="font-medium">{{ run.name }}</div>
          <div class="text-sm text-[var(--text-muted)]">{{ protocolName(run.protocolId) }} &mdash; {{ new Date(run.testDate).toLocaleDateString('de-DE') }}</div>
        </div>
        <SuccessBadge v-if="run.status === 'CLOSED'">{{ t('protocol.closed') }}</SuccessBadge>
        <PrimaryBadge v-else>{{ t('protocol.open') }}</PrimaryBadge>
      </NeutralContainer>
    </div>

    <!-- Create Run Modal -->
    <Modal v-model="showCreateModal">
      <SubHeader class="font-semibold mb-3">{{ t('protocol.createRun') }}</SubHeader>
      <form @submit.prevent="handleCreate" class="space-y-3">
        <div>
          <FieldLabel class="mb-1">{{ t('protocol.selectProtocol') }}</FieldLabel>
          <SelectInput v-model="newProtocolId">
            <option value="" disabled>{{ t('protocol.selectProtocol') }}</option>
            <option v-for="p in protocols" :key="p.id" :value="p.id">{{ p.name }}</option>
          </SelectInput>
        </div>
        <TextInput v-model="newName" :placeholder="t('protocol.runName')" required />
        <DateInput v-model="newDate" />

        <div>
          <FieldLabel class="mb-1">{{ t('protocol.selectMembers') }}</FieldLabel>
          <div class="max-h-40 overflow-y-auto border border-[var(--border)] rounded p-2 space-y-1">
            <label v-for="m in members" :key="m.id" class="flex items-center gap-2 text-sm cursor-pointer">
              <ToggleInput :model-value="selectedMemberIds.includes(m.id)" @update:model-value="toggleMember(m.id)" />
              {{ m.name || m.email || `#${m.id}` }}
            </label>
          </div>
        </div>

        <div class="flex gap-2 justify-end">
          <SecondaryButton type="button" @click="showCreateModal = false">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton type="submit" :disabled="!newProtocolId || !newName.trim()">{{ t('protocol.createRun') }}</PrimaryButton>
        </div>
      </form>
    </Modal>
  </ViewContent>
</template>

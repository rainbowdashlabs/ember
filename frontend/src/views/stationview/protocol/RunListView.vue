/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
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
import RestrictionPicker from '@/components/input/RestrictionPicker.vue'
import MultiSelectDropdown from '@/components/input/select/MultiSelectDropdown.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import { useSession } from '@/composables/useSession'
import { protocol, stationMembers, memberGroups, userTags } from '@/api'
import type { TestProtocol, TestProtocolRun } from '@/api/protocol'
import type { StationMember, MemberGroup, UserTag } from '@/api/types'
import { StationPermission } from '@/api/types'

const { t } = useI18n()
const router = useRouter()
const { hasPermission, loaded } = useSession()
const canCreateRun = computed(() => hasPermission(StationPermission.PROTOCOL_CREATE))

const runs = ref<TestProtocolRun[]>([])
const protocols = ref<TestProtocol[]>([])
const members = ref<StationMember[]>([])
const allGroups = ref<MemberGroup[]>([])
const allTags = ref<UserTag[]>([])
const loading = ref(true)
const error = ref('')

// Create modal
const showCreateModal = ref(false)
const newProtocolId = ref<string>('')
const newName = ref('')
const newDate = ref(new Date().toISOString().split('T')[0])
const selectedUserTypes = ref<string[]>([])
const selectedGroupIds = ref<number[]>([])
const selectedTagIds = ref<number[]>([])
const selectedMemberIds = ref<number[]>([])

const memberOptions = computed(() =>
  members.value.map(m => ({ value: String(m.id), label: m.name || m.email || `#${m.id}` }))
)
const selectedMemberValues = computed(() => selectedMemberIds.value.map(String))

function onMembersChange(values: string[]) {
  selectedMemberIds.value = values.map(Number)
}

async function loadData() {
  loading.value = true
  try {
    const [r, p, m, groups, tags] = await Promise.all([
      protocol.listRuns(),
      protocol.listProtocols(),
      stationMembers.listMembers(),
      memberGroups.listGroups(),
      userTags.listTags(),
    ])
    runs.value = r
    protocols.value = Array.isArray(p) ? p : (p.protocols ?? [])
    members.value = m
    allGroups.value = groups
    allTags.value = tags
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
      memberIds: selectedMemberIds.value.length > 0 ? selectedMemberIds.value : undefined,
      userTypes: selectedUserTypes.value.length > 0 ? selectedUserTypes.value : undefined,
      groupIds: selectedGroupIds.value.length > 0 ? selectedGroupIds.value : undefined,
      tagIds: selectedTagIds.value.length > 0 ? selectedTagIds.value : undefined,
    })
    showCreateModal.value = false
    router.push({ name: 'protocol-run-detail', params: { id: run.id } })
  } catch { error.value = t('common.error') }
}

function resetCreateModal() {
  newProtocolId.value = ''
  newName.value = ''
  newDate.value = new Date().toISOString().split('T')[0]
  selectedUserTypes.value = []
  selectedGroupIds.value = []
  selectedTagIds.value = []
  selectedMemberIds.value = []
}

watch(loaded, (v) => { if (v) loadData() }, { immediate: true })
onMounted(() => { if (loaded.value) loadData() })
</script>

<template>
  <ViewContent>
    <div class="flex items-center justify-between mb-4">
      <SectionHeader>{{ t('protocol.runs') }}</SectionHeader>
      <PrimaryButton v-if="canCreateRun" @click="showCreateModal = true">
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
        class="flex items-center gap-2 cursor-pointer hover:border-[var(--primary)] transition-colors"
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
    <Modal v-model="showCreateModal" @update:model-value="v => { if (!v) resetCreateModal() }">
      <SubHeader class="mb-3">{{ t('protocol.createRun') }}</SubHeader>
      <form @submit.prevent="handleCreate" class="space-y-3">
        <div>
          <FieldLabel class="mb-1">{{ t('protocol.selectProtocol') }}</FieldLabel>
          <SelectInput v-model="newProtocolId" class="w-full">
            <option value="" disabled>{{ t('protocol.selectProtocol') }}</option>
            <option v-for="p in protocols" :key="p.id" :value="p.id">{{ p.name }}</option>
          </SelectInput>
        </div>
        <TextInput v-model="newName" :placeholder="t('protocol.runName')" required />
        <DateInput v-model="newDate" />

        <div>
          <FieldLabel class="mb-1">{{ t('protocol.selectByRestriction') }}</FieldLabel>
          <RestrictionPicker
            :groups="allGroups"
            :tags="allTags"
            :selected-user-types="selectedUserTypes"
            :selected-group-ids="selectedGroupIds"
            :selected-tag-ids="selectedTagIds"
            :show-mode="false"
            @update:selected-user-types="selectedUserTypes = $event"
            @update:selected-group-ids="selectedGroupIds = $event"
            @update:selected-tag-ids="selectedTagIds = $event"
          />
        </div>

        <div>
          <FieldLabel class="mb-1">{{ t('protocol.selectMembers') }}</FieldLabel>
          <MultiSelectDropdown
            :options="memberOptions"
            :model-value="selectedMemberValues"
            :placeholder="t('protocol.selectMembers')"
            @update:model-value="onMembersChange"
          />
        </div>

        <div class="flex gap-2 justify-end">
          <SecondaryButton type="button" @click="showCreateModal = false">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton type="submit" :disabled="!newProtocolId || !newName.trim()">{{ t('protocol.createRun') }}</PrimaryButton>
        </div>
      </form>
    </Modal>
  </ViewContent>
</template>

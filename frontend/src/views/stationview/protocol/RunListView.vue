/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import Modal from '@/components/feedback/Modal.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import RestrictionPicker from '@/components/input/RestrictionPicker.vue'
import {type RestrictionSelection, emptyRestriction} from '@/components/input/restriction'
import MultiSelectDropdown from '@/components/input/select/MultiSelectDropdown.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import { useSession } from '@/composables/useSession'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { protocol, stationMembers, memberGroups, userTags } from '@/api'
import type { TestProtocol, TestProtocolRun } from '@/api/protocol'
import {StationPermission, type MemberGroup, type StationMember, type UserTag} from '@/api/types'
import { formatDate, todayIsoDate } from '@/util/format'

const { t } = useI18n()
const router = useRouter()
const { hasPermission, loaded } = useSession()
const canCreateRun = computed(() => hasPermission(StationPermission.PROTOCOL_CREATE))

const runs = ref<TestProtocolRun[]>([])
const protocols = ref<TestProtocol[]>([])
const members = ref<StationMember[]>([])
const allGroups = ref<MemberGroup[]>([])
const allTags = ref<UserTag[]>([])

const showCreateModal = ref(false)
const newProtocolId = ref<string>('')
const newName = ref('')
const newDate = ref(todayIsoDate())
const restriction = ref<RestrictionSelection>(emptyRestriction())

const memberOptions = computed(() =>
  members.value.map(m => ({ value: String(m.id), label: m.name || m.email || `#${m.id}` }))
)
const selectedMemberValues = computed(() => restriction.value.memberIds.map(String))

function onMembersChange(values: string[]) {
  restriction.value.memberIds = values.map(Number)
}

const {loading, error, reload: loadData} = useAsyncLoader(async () => {
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
}, {autoLoad: false})

function protocolName(id: number) { return protocols.value.find(p => p.id === id)?.name ?? '?' }

async function handleCreate() {
  if (!newProtocolId.value || !newName.value.trim()) return
  try {
    const run = await protocol.createRun(Number(newProtocolId.value), {
      name: newName.value.trim(),
      testDate: newDate.value,
      memberIds: restriction.value.memberIds.length > 0 ? restriction.value.memberIds : undefined,
      userTypes: restriction.value.userTypes.length > 0 ? restriction.value.userTypes : undefined,
      groupIds: restriction.value.groupIds.length > 0 ? restriction.value.groupIds : undefined,
      tagIds: restriction.value.tagIds.length > 0 ? restriction.value.tagIds : undefined,
    })
    showCreateModal.value = false
    router.push({ name: 'protocol-run-detail', params: { id: run.id } })
  } catch { error.value = t('common.error') }
}

function resetCreateModal() {
  newProtocolId.value = ''
  newName.value = ''
  newDate.value = todayIsoDate()
  restriction.value = emptyRestriction()
}

watch(loaded, (v) => { if (v) loadData() }, { immediate: true })
</script>

<template>
  <ViewContent
      :title="t('pages.protocol-run-list.title')"
      :subtitle="t('pages.protocol-run-list.subtitle')"
  >
    <div class="flex items-center justify-between mb-4">
      <div />
      <PrimaryButton v-if="canCreateRun" @click="showCreateModal = true">
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" /> {{ t('protocol.createRun') }}
      </PrimaryButton>
    </div>

    <AsyncSection
      :empty="runs.length === 0"
      :empty-message="t('protocol.noRuns')"
      :error="error"
      :loading="loading"
    >
      <div class="space-y-2">
        <NeutralContainer
          v-for="run in runs"
          :key="run.id"
          class="flex items-center gap-2 cursor-pointer hover:border-[var(--primary)] transition-colors"
          @click="router.push({ name: 'protocol-run-detail', params: { id: run.id } })"
        >
          <div class="flex-1 min-w-0">
            <div class="font-medium">{{ run.name }}</div>
            <div class="text-sm text-[var(--text-muted)]">{{ protocolName(run.protocolId) }}, {{ formatDate(run.testDate) }}</div>
          </div>
          <SuccessBadge v-if="run.status === 'CLOSED'">{{ t('protocol.closed') }}</SuccessBadge>
          <PrimaryBadge v-else>{{ t('protocol.open') }}</PrimaryBadge>
        </NeutralContainer>
      </div>
    </AsyncSection>

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
            v-model="restriction"
            :show-mode="false"
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

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
import DeleteButton from '@/components/button/DeleteButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import Modal from '@/components/feedback/Modal.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import { useSession } from '@/composables/useSession'
import { protocol, federation } from '@/api'
import type { TestProtocol, SharedProtocolEntry } from '@/api/protocol'

const { t } = useI18n()
const router = useRouter()
const { canManageProtocol, loaded } = useSession()

const protocols = ref<TestProtocol[]>([])
const sharedProtocols = ref<SharedProtocolEntry[]>([])
const loading = ref(true)
const error = ref('')

// Search & filters
const searchQuery = ref('')
const showFederated = ref(true)
const filterStationId = ref<string | null>(null)

const showCreateModal = ref(false)
const newName = ref('')
const newDescription = ref('')
const newPassThreshold = ref<number | undefined>(undefined)

const showDeleteModal = ref(false)
const deleteTarget = ref<TestProtocol | null>(null)

// Unique partner stations from shared protocols
const partnerStations = computed(() => {
  const map = new Map<string, string>()
  for (const s of sharedProtocols.value) {
    map.set(s.sourceStationId, s.stationName)
  }
  return [...map.entries()].map(([id, name]) => ({ id, name }))
})

// Filtered local protocols
const filteredProtocols = computed(() => {
  if (!searchQuery.value.trim()) return protocols.value
  const q = searchQuery.value.trim().toLowerCase()
  return protocols.value.filter(
    p => p.name.toLowerCase().includes(q) || (p.description && p.description.toLowerCase().includes(q)),
  )
})

// Filtered shared protocols
const filteredShared = computed(() => {
  if (!showFederated.value) return []
  let result = sharedProtocols.value
  if (searchQuery.value.trim()) {
    const q = searchQuery.value.trim().toLowerCase()
    result = result.filter(
      s => s.protocol.name.toLowerCase().includes(q) || (s.protocol.description && s.protocol.description.toLowerCase().includes(q)),
    )
  }
  if (filterStationId.value != null) {
    result = result.filter(s => s.sourceStationId === filterStationId.value)
  }
  return result
})

async function loadData() {
  loading.value = true
  try {
    const data = await protocol.listProtocols()
    if (Array.isArray(data)) {
      protocols.value = data as unknown as typeof protocols.value
      sharedProtocols.value = []
    } else {
      protocols.value = data.protocols ?? []
      sharedProtocols.value = data.shared ?? []
    }
  } catch { error.value = t('common.error') }
  finally { loading.value = false }
}

async function handleCreate() {
  if (!newName.value.trim()) return
  try {
    const created = await protocol.createProtocol({
      name: newName.value.trim(),
      description: newDescription.value,
      passThreshold: newPassThreshold.value,
    })
    showCreateModal.value = false
    newName.value = ''
    newDescription.value = ''
    newPassThreshold.value = undefined
    router.push({ name: 'protocol-detail', params: { id: created.id } })
  } catch { error.value = t('common.error') }
}

async function handleDelete() {
  if (!deleteTarget.value) return
  try {
    await protocol.deleteProtocol(deleteTarget.value.id)
    showDeleteModal.value = false
    deleteTarget.value = null
    await loadData()
  } catch { error.value = t('common.error') }
}

async function copySharedProtocol(protocolId: number) {
  try {
    await federation.copyProtocol(protocolId)
    await loadData()
  } catch { error.value = t('common.error') }
}

watch(loaded, (v) => { if (v) loadData() }, { immediate: true })
onMounted(() => { if (loaded.value) loadData() })
</script>

<template>
  <ViewContent>
    <div class="flex items-center justify-between mb-4">
      <SectionHeader>{{ t('protocol.title') }}</SectionHeader>
      <PrimaryButton v-if="canManageProtocol()" @click="showCreateModal = true">
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" /> {{ t('protocol.create') }}
      </PrimaryButton>
    </div>

    <!-- Search -->
    <div class="mb-4">
      <TextInput v-model="searchQuery" :placeholder="t('protocol.search')" />
    </div>

    <!-- Filters -->
    <div class="flex flex-wrap items-center gap-2 mb-4">
      <SelectionToggleButton :selected="showFederated" @toggle="showFederated = !showFederated">
        <font-awesome-icon :icon="['fas', 'arrow-right-arrow-left']" class="w-3 h-3 mr-1" />
        {{ t('federation.shared') }}
      </SelectionToggleButton>
      <SelectInput
        v-if="showFederated && partnerStations.length > 0"
        :model-value="filterStationId != null ? String(filterStationId) : ''"
        class="!w-auto !text-xs !py-1"
        @update:model-value="(v: string | undefined) => { filterStationId = v || null }"
      >
        <option value="">{{ t('protocol.allStations') }}</option>
        <option v-for="station in partnerStations" :key="station.id" :value="String(station.id)">
          {{ station.name }}
        </option>
      </SelectInput>
    </div>

    <Spinner v-if="loading" />
    <Alert v-if="error" variant="error">{{ error }}</Alert>

    <div v-if="!loading && filteredProtocols.length === 0 && filteredShared.length === 0" class="text-center text-[var(--text-muted)] py-8">
      {{ t('protocol.empty') }}
    </div>

    <div class="space-y-2">
      <!-- Local protocols -->
      <NeutralContainer
        v-for="p in filteredProtocols"
        :key="'local-' + p.id"
        class="flex items-center gap-3 cursor-pointer hover:border-[var(--primary)] transition-colors group"
        @click="router.push({ name: 'protocol-detail', params: { id: p.id } })"
      >
        <div class="flex-1 min-w-0">
          <div class="font-medium">{{ p.name }}</div>
          <div v-if="p.description" class="text-sm text-[var(--text-muted)] truncate">{{ p.description }}</div>
        </div>
        <span v-if="p.passThreshold" class="text-xs text-[var(--text-muted)]">{{ t('protocol.threshold') }}: {{ p.passThreshold }}P</span>
        <div v-if="canManageProtocol()" class="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
          <EditButton :label="t('common.edit')" @click.stop="router.push({ name: 'protocol-detail', params: { id: p.id } })" />
          <DeleteButton :label="t('common.delete')" @click.stop="deleteTarget = p; showDeleteModal = true" />
        </div>
      </NeutralContainer>

      <!-- Shared protocols from partner stations -->
      <NeutralContainer
        v-for="s in filteredShared"
        :key="'shared-' + s.protocol.id + '-' + s.sourceStationId"
        class="flex items-center gap-3 cursor-pointer hover:border-[var(--primary)] transition-colors group"
        @click="router.push({ name: 'protocol-detail', params: { id: s.protocol.id } })"
      >
        <div class="flex-1 min-w-0">
          <div class="flex items-center gap-2">
            <span class="font-medium">{{ s.protocol.name }}</span>
            <StationBadge :station-name="s.stationName" />
          </div>
          <div v-if="s.protocol.description" class="text-sm text-[var(--text-muted)] truncate">{{ s.protocol.description }}</div>
        </div>
        <span v-if="s.protocol.passThreshold" class="text-xs text-[var(--text-muted)]">{{ t('protocol.threshold') }}: {{ s.protocol.passThreshold }}P</span>
        <div class="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
          <IconButton
            :icon="['fas', 'copy']"
            :label="t('federation.copyToStation')"
            @click.stop="copySharedProtocol(s.protocol.id)"
          />
        </div>
      </NeutralContainer>
    </div>

    <!-- Create Modal -->
    <Modal v-model="showCreateModal">
      <SubHeader class="font-semibold mb-3">{{ t('protocol.create') }}</SubHeader>
      <form @submit.prevent="handleCreate" class="space-y-3">
        <TextInput v-model="newName" :placeholder="t('protocol.name')" required />
        <TextAreaInput v-model="newDescription" :placeholder="t('protocol.description')" />
        <div>
          <FieldLabel class="mb-1">{{ t('protocol.passThreshold') }}</FieldLabel>
          <NumberInput v-model="newPassThreshold" :placeholder="t('protocol.passThresholdHint')" />
        </div>
        <div class="flex gap-2 justify-end">
          <PrimaryButton type="submit">{{ t('protocol.create') }}</PrimaryButton>
        </div>
      </form>
    </Modal>

    <!-- Delete Modal -->
    <Modal v-model="showDeleteModal">
      <SubHeader class="font-semibold mb-3">{{ t('protocol.deleteConfirm') }}</SubHeader>
      <p class="mb-4">{{ deleteTarget?.name }}</p>
      <div class="flex gap-2 justify-end">
        <PrimaryButton @click="handleDelete">{{ t('common.delete') }}</PrimaryButton>
      </div>
    </Modal>
  </ViewContent>
</template>

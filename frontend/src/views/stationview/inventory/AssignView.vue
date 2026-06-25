/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import {inventory, stationMembers} from '@/api'
import type {InventoryItem, StationMember} from '@/api/types'
import UnknownScanModal from '@/views/stationview/inventory/UnknownScanModal.vue'
import ScanButton from '@/components/scanner/ScanButton.vue'
import {normaliseScannedPayload} from '@/components/scanner/useBarcodeScanner'

interface AssignmentEvent {
  id: number
  itemName: string
  internalId?: string | null
  memberName: string
  memberId: number
  itemId: number
  ts: string
  action: 'ASSIGN' | 'RETURN'
}

const {t} = useI18n()

const members = ref<StationMember[]>([])
const memberId = ref<number | null>(null)
const scanValue = ref('')
const bulkMode = ref(false)
const submitting = ref(false)
const error = ref('')
const success = ref('')
const recent = ref<AssignmentEvent[]>([])
const recentCounter = ref(1)
const loading = ref(true)
const unknownScanCode = ref<string | null>(null)

const sortedMembers = computed(() => {
  return [...members.value].sort((a, b) =>
      (a.firstName ?? '').localeCompare(b.firstName ?? '')
      || (a.lastName ?? '').localeCompare(b.lastName ?? ''))
})

const selectedMember = computed(() =>
    memberId.value != null ? members.value.find(m => m.id === memberId.value) ?? null : null)

const memberDisplay = (m: StationMember) => `${m.firstName ?? ''} ${m.lastName ?? ''}`.trim() || `#${m.id}`

async function load() {
  loading.value = true
  error.value = ''
  try {
    members.value = await stationMembers.listMembers()
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('inventory.assign.loadError')
  } finally {
    loading.value = false
  }
}

function flashError(msg: string) {
  error.value = msg
  setTimeout(() => (error.value = ''), 3500)
}

function flashSuccess(msg: string) {
  success.value = msg
  setTimeout(() => (success.value = ''), 2500)
}

async function onCameraScan(value: string) {
  if (submitting.value) return
  scanValue.value = normaliseScannedPayload(value)
  await handleScan()
}

async function handleScan() {
  const term = scanValue.value.trim()
  if (!term) return
  scanValue.value = ''
  if (!memberId.value) {
    flashError(t('inventory.assign.errors.pickMember'))
    return
  }
  submitting.value = true
  try {
    const item = await inventory.findByInternalId(term)
    if (!item) {
      unknownScanCode.value = term
      return
    }
    if (item.assignedTo === memberId.value) {
      const returned = await inventory.assignItem(item.id, {memberId: null})
      pushRecent('RETURN', returned, selectedMember.value)
      flashSuccess(t('inventory.assign.returned', {name: item.name ?? ''}))
      return
    }
    if (item.assignedTo && item.assignedTo !== memberId.value) {
      const previous = members.value.find(m => m.id === item.assignedTo) ?? null
      if (!confirm(t('inventory.assign.confirmReassign', {
            previous: previous ? memberDisplay(previous) : `#${item.assignedTo}`,
          }))) {
        return
      }
    }
    const assigned = await inventory.assignItem(item.id, {
      memberId: memberId.value,
      memberName: selectedMember.value ? memberDisplay(selectedMember.value) : '',
    })
    pushRecent('ASSIGN', assigned, selectedMember.value)
    flashSuccess(t('inventory.assign.assigned', {name: item.name ?? ''}))
  } catch (e: any) {
    flashError(e?.response?.data?.message ?? t('inventory.assign.errors.failed'))
  } finally {
    submitting.value = false
  }
}

function pushRecent(action: 'ASSIGN' | 'RETURN', item: InventoryItem, member: StationMember | null) {
  recent.value.unshift({
    id: recentCounter.value++,
    itemName: item.name ?? '',
    internalId: item.internalId,
    memberName: member ? memberDisplay(member) : '',
    memberId: member?.id ?? -1,
    itemId: item.id,
    ts: new Date().toISOString(),
    action,
  })
  if (recent.value.length > 50) recent.value.length = 50
}

async function undoLast() {
  const last = recent.value[0]
  if (!last) return
  submitting.value = true
  try {
    if (last.action === 'ASSIGN') {
      await inventory.assignItem(last.itemId, {memberId: null})
    } else {
      await inventory.assignItem(last.itemId, {
        memberId: last.memberId,
        memberName: last.memberName,
      })
    }
    recent.value.shift()
    flashSuccess(t('inventory.assign.undone'))
  } catch (e: any) {
    flashError(e?.response?.data?.message ?? t('inventory.assign.errors.failed'))
  } finally {
    submitting.value = false
  }
}

async function onUnknownScanCreated(item: InventoryItem) {
  unknownScanCode.value = null
  if (!memberId.value) {
    flashError(t('inventory.assign.errors.pickMember'))
    return
  }
  try {
    const assigned = await inventory.assignItem(item.id, {
      memberId: memberId.value,
      memberName: selectedMember.value ? memberDisplay(selectedMember.value) : '',
    })
    pushRecent('ASSIGN', assigned, selectedMember.value)
    flashSuccess(t('inventory.assign.assigned', {name: item.name ?? ''}))
  } catch (e: any) {
    flashError(e?.response?.data?.message ?? t('inventory.assign.errors.failed'))
  }
}

onMounted(load)
</script>

<template>
  <ViewContent>
    <Alert v-if="error" variant="error" class="mb-3">{{ error }}</Alert>
    <Alert v-if="success" variant="success" class="mb-3">{{ success }}</Alert>

    <div v-if="loading" class="flex justify-center py-12">
      <Spinner size="lg" />
    </div>
    <template v-else>
      <NeutralContainer class="mb-4">
        <SubHeader class="mb-2">{{ t('inventory.assign.selectMember') }}</SubHeader>
        <SelectInput v-model="memberId">
          <option :value="null">{{ t('inventory.assign.pickMember') }}</option>
          <option v-for="m in sortedMembers" :key="m.id" :value="m.id">{{ memberDisplay(m) }}</option>
        </SelectInput>
      </NeutralContainer>

      <NeutralContainer class="mb-4">
        <SubHeader class="mb-2">{{ t('inventory.assign.scanItem') }}</SubHeader>
        <div class="flex gap-2 items-center">
          <TextInput
              v-model="scanValue"
              :placeholder="t('inventory.assign.scanPlaceholder')"
              @keydown.enter="handleScan"
              class="flex-1"
              :disabled="!memberId"
          />
          <ScanButton mode="continuous" :disabled="!memberId || submitting" @decoded="onCameraScan" />
        </div>
        <label class="flex items-center gap-2 text-sm mt-3">
          <ToggleInput v-model="bulkMode" />
          <span>{{ t('inventory.assign.bulkMode') }}</span>
        </label>
        <p v-if="bulkMode" class="text-xs text-(--text-muted) mt-1">{{ t('inventory.assign.bulkModeHint') }}</p>
      </NeutralContainer>

      <UnknownScanModal
          v-if="unknownScanCode"
          :scanned-code="unknownScanCode"
          context="member"
          @created="onUnknownScanCreated"
          @close="unknownScanCode = null"
      />

      <SectionHeader>{{ t('inventory.assign.recent') }}</SectionHeader>
      <NeutralContainer>
        <div class="flex items-center justify-between mb-2">
          <p class="text-xs text-(--text-muted)">{{ t('inventory.assign.recentHint') }}</p>
          <SecondaryButton v-if="recent.length > 0" size="sm" :disabled="submitting" @click="undoLast">
            <font-awesome-icon :icon="['fas', 'rotate-left']" class="mr-1" />
            {{ t('inventory.assign.undo') }}
          </SecondaryButton>
        </div>
        <EmptyState v-if="recent.length === 0" :message="t('inventory.assign.recentEmpty')" />
        <ul v-else class="divide-y divide-(--bg-accent)">
          <li v-for="r in recent" :key="r.id" class="py-2 flex items-center gap-3 text-sm">
            <font-awesome-icon
                :icon="['fas', r.action === 'ASSIGN' ? 'user-plus' : 'user-minus']"
                :class="r.action === 'ASSIGN' ? 'text-success' : 'text-error'"
                class="w-4"
            />
            <span class="font-medium">{{ r.itemName }}</span>
            <span v-if="r.internalId" class="text-xs text-(--text-muted)">{{ r.internalId }}</span>
            <span class="text-(--text-muted)">→</span>
            <span>{{ r.memberName }}</span>
            <span class="ml-auto text-xs text-(--text-muted)">{{ new Date(r.ts).toLocaleTimeString() }}</span>
          </li>
        </ul>
      </NeutralContainer>
    </template>
  </ViewContent>
</template>

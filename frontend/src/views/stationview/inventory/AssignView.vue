/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useConfigPanel} from '@/composables/useConfigPanel'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useFlashMessage} from '@/composables/useFlashMessage'
import {useConfirmAction} from '@/composables/useConfirmAction'
import ViewContent from '@/components/layout/ViewContent.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import {resolveMemberOption, searchMemberOptions} from '@/components/input/select/memberSearchSource'
import ItemSearchPicker from '@/components/input/search/ItemSearchPicker.vue'
import HandOutChoice from '@/components/inventory/HandOutChoice.vue'
import type {HandOutMode} from '@/components/inventory/HandOutChoice.vue'
import {inventory, movements, stationMembers} from '@/api'
import type {InventoryItem} from '@/api/inventory'
import type {StationMember} from '@/api/types'
import UnknownScanModal from '@/views/stationview/inventory/UnknownScanModal.vue'
import {formatTime} from '@/util/format'
import {apiErrorMessage} from '@/util/apiError'

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

const {config: members, loading, error} = useConfigPanel<StationMember[]>({
  initial: [],
  fetch: () => stationMembers.listMembers(),
  formatError: (e) => apiErrorMessage(e) ?? t('inventory.assign.loadError'),
})
const memberId = ref<number | null>(null)
const memberUid = ref('')
const pickedItemId = ref<number | null>(null)
const handOutMode = ref<HandOutMode>('NOW')
const bulkMode = ref(false)
const {message: flashMessage, kind: flashKind, flash} = useFlashMessage()
const {running: submitting, run: runMutation} = useAsyncAction((fn: () => Promise<void>) => fn())
const recent = ref<AssignmentEvent[]>([])
const recentCounter = ref(1)
const unknownScanCode = ref<string | null>(null)

const selectedMember = computed(() =>
    memberId.value != null ? members.value.find(m => m.id === memberId.value) ?? null : null)

function memberDisplay(m: StationMember): string {
  return (m.name ?? '').trim() || `#${m.id}`
}

/**
 * The menu names somebody by their UUID and everything below works from the row id, which is the one
 * translation between them. A member the loaded list does not hold yet is reason to load it again.
 */
async function onMemberPicked(uid: string | null) {
  if (!uid) {
    memberId.value = null
    return
  }
  let match = members.value.find(m => m.identity?.memberUid === uid)
  if (!match) {
    try {
      members.value = await stationMembers.listMembers()
      match = members.value.find(m => m.identity?.memberUid === uid)
    } catch {
      match = undefined
    }
  }
  if (!match) {
    flashError(t('inventory.assign.errors.memberLookupFailed'))
    memberId.value = null
    return
  }
  memberId.value = match.id
}

watch(memberUid, onMemberPicked)

function flashError(msg: string) {
  flash(msg, 'error', 3500)
}

function flashSuccess(msg: string) {
  flash(msg)
}

async function onItemPicked(item: InventoryItem) {
  if (!memberId.value) {
    pickedItemId.value = null
    flashError(t('inventory.assign.errors.pickMember'))
    return
  }
  if (submitting.value) return

  if (item.assignedTo && item.assignedTo !== memberId.value) {
    reassign.request(item)
    return
  }

  await runMutation(async () => {
    try {
      if (item.assignedTo === memberId.value) {
        const returned = await inventory.assignItem(item.id, {memberId: null})
        pushRecent('RETURN', returned, selectedMember.value)
        flashSuccess(t('inventory.assign.returned', {name: item.name ?? ''}))
        return
      }
      await assignToSelectedMember(item)
    } catch (e) {
      flashError(apiErrorMessage(e) ?? t('inventory.assign.errors.failed'))
    }
  })
  pickedItemId.value = null
}

/**
 * Hands the piece over, or writes down that it is to be handed over.
 *
 * <p>A planned hand-out leaves the session's list alone: there is nothing to undo locally once a
 * Vorgang carries it, and the queue is where it is followed from there.
 */
async function assignToSelectedMember(item: InventoryItem) {
  if (memberId.value == null) return
  if (handOutMode.value === 'PLANNED') {
    await movements.planHandOut(memberId.value, item.id, item.inventoryId)
    flashSuccess(t('inventory.handOut.plannedFlash', {name: item.name ?? ''}))
    return
  }
  const assigned = await inventory.assignItem(item.id, {
    memberId: memberId.value,
    memberName: selectedMember.value ? memberDisplay(selectedMember.value) : '',
  })
  pushRecent('ASSIGN', assigned, selectedMember.value)
  flashSuccess(t('inventory.assign.assigned', {name: item.name ?? ''}))
}

/**
 * Reassigning an item that another member still holds needs a confirmation, and a modal cannot
 * block the scan flow the way the old native prompt did. The pick is therefore parked here and the
 * assignment resumes only once the user confirms.
 */
const reassign = useConfirmAction<InventoryItem>({
  onConfirm: async item => {
    await runMutation(async () => {
      try {
        await assignToSelectedMember(item)
      } catch (e) {
        flashError(apiErrorMessage(e) ?? t('inventory.assign.errors.failed'))
      }
    })
    pickedItemId.value = null
  },
})

const reassignPrevious = computed(() => {
  const holder = reassign.target.value?.assignedTo
  if (!holder) return ''
  const previous = members.value.find(m => m.id === holder) ?? null
  return previous ? memberDisplay(previous) : `#${holder}`
})

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
  await runMutation(async () => {
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
    } catch (e) {
      flashError(apiErrorMessage(e) ?? t('inventory.assign.errors.failed'))
    }
  })
}

async function onUnknownScanCreated(item: InventoryItem) {
  unknownScanCode.value = null
  if (!memberId.value) {
    flashError(t('inventory.assign.errors.pickMember'))
    return
  }
  try {
    await assignToSelectedMember(item)
  } catch (e) {
    flashError(apiErrorMessage(e) ?? t('inventory.assign.errors.failed'))
  }
}
</script>

<template>
  <ViewContent
      :title="t('pages.inventory-assign.title')"
      :subtitle="t('pages.inventory-assign.subtitle')"
  >
    <Alert v-if="error" variant="error" class="mb-3">{{ error }}</Alert>
    <Alert v-if="flashMessage" :variant="flashKind" class="mb-3">{{ flashMessage }}</Alert>

    <div v-if="loading" class="flex justify-center py-12">
      <Spinner size="lg" />
    </div>
    <template v-else>
      <NeutralContainer class="mb-4">
        <SubHeader class="mb-2">{{ t('inventory.assign.selectMember') }}</SubHeader>
        <MemberSelectInput
            v-model="memberUid"
            :search-fn="searchMemberOptions"
            :resolve-fn="resolveMemberOption"
            :placeholder="t('inventory.assign.pickMember')"
        />
        <HandOutChoice v-model="handOutMode" class="mt-3"/>
      </NeutralContainer>

      <NeutralContainer class="mb-4">
        <SubHeader class="mb-2">{{ t('inventory.assign.scanItem') }}</SubHeader>
        <ItemSearchPicker
            v-model="pickedItemId"
            :disabled="!memberId || submitting"
            @pick="onItemPicked"
            @scan-not-found="(code) => unknownScanCode = code"
        />
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
            <span class="ml-auto text-xs text-(--text-muted)">{{ formatTime(r.ts) }}</span>
          </li>
        </ul>
      </NeutralContainer>
    </template>
    <ConfirmDeleteModal
        v-model="reassign.show.value"
        :message="t('inventory.assign.confirmReassign', {previous: reassignPrevious})"
        @confirm="reassign.confirm"
    />
  </ViewContent>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import Modal from '@/components/feedback/Modal.vue'
import MemberPicker, {type PickableMember} from '@/views/stationview/members/MemberPicker.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import ScanButton from '@/components/scanner/ScanButton.vue'
import {normaliseScannedPayload} from '@/components/scanner/useBarcodeScanner'
import {ItemOwner, type InventoryDetail, type InventoryItem, type InventoryItemHistory} from '@/api/inventory'
import type {StationMember} from '@/api/types'
import {inventory, inventoryArts, inventoryFields} from '@/api'
import type {InventoryArt} from '@/api/inventoryArts'
import {useModalTarget} from '@/composables/useModalTarget'
import AddItemFields from './itemmodals/AddItemFields.vue'
import EditItemModal from '../detailview/EditItemModal.vue'
import EditItemCustomFields from '../detailview/edititemmodal/EditItemCustomFields.vue'
import {buildItemMetadata} from '../detailview/itemMetadata'
import type {InventoryFieldDefinition} from '@/api/inventoryFields'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {formatDate} from '@/util/format'

const {t} = useI18n()

const props = defineProps<{
  detail: InventoryDetail
  members: StationMember[]
}>()

const emit = defineEmits<{
  itemsChanged: []
  error: [message: string]
}>()

/** The list the picker offers, and the kinds it can be narrowed by. */
const pickable = computed<PickableMember[]>(() => props.members.map(member => ({
  id: member.id,
  name: member.name || member.email || `#${member.id}`,
  email: member.email,
  identity: member.identity,
  userType: member.userType,
})))

const userTypes = computed(() => [...new Set(props.members.map(member => member.userType).filter(Boolean))] as string[])

function getMemberName(memberId: number | null | undefined): string {
  if (!memberId) return ''
  const m = props.members.find(mem => mem.id === memberId)
  return m ? (m.name && m.name.trim() ? m.name : m.email ?? `#${m.id}`) : `#${memberId}`
}

const showItemModal = ref(false)
const itemName = ref('')
const itemInternalId = ref('')
const itemSizeId = ref('')
const itemQuantity = ref(1)
const itemArtId = ref<number | null>(null)
const itemArtDraft = ref('')
const arts = ref<InventoryArt[]>([])
const allFieldDefs = ref<InventoryFieldDefinition[]>([])
const fieldValues = ref<Record<string, unknown>>({})

/** Kinds live only in an inventory that holds a drawer of different things. */
const heterogeneous = computed(() => props.detail.homogeneous === false)

/**
 * The fields the new piece will carry, worked out the way the backend works them out for a piece
 * that already exists: the inventory's fields, plus the chosen kind's, narrowest winning.
 */
const fieldDefs = computed(() => inventoryFields.resolveFields(allFieldDefs.value, itemArtId.value))

const fieldsInvalid = computed(() => inventoryFields.hasInvalidFieldValues(fieldDefs.value, fieldValues.value))

async function loadFieldDefs() {
  try {
    const [defs, allArts] = await Promise.all([
      inventoryFields.listFields(props.detail.id),
      heterogeneous.value ? inventoryArts.listArts(props.detail.id) : Promise.resolve([]),
    ])
    allFieldDefs.value = defs
    arts.value = allArts
  } catch {
    allFieldDefs.value = []
    arts.value = []
  }
}

function openAdd() {
  itemName.value = props.detail.name ?? ''
  itemInternalId.value = ''
  itemSizeId.value = ''
  itemQuantity.value = 1
  itemArtId.value = null
  itemArtDraft.value = ''
  fieldValues.value = {}
  loadFieldDefs()
  showItemModal.value = true
}

const {isOpen: showEditModal, target: editTarget, open: openEdit} = useModalTarget<InventoryItem>()

const {running: itemSaving, run: saveItem} = useAsyncAction(async () => {
  try {
    const normalisedInternalId = itemInternalId.value
        ? normaliseScannedPayload(itemInternalId.value)
        : ''
    // A kind typed into the picker is written down here and nowhere earlier, so a form somebody
    // opened and closed again leaves no half-invented kind behind.
    const resolvedArt = heterogeneous.value
        ? itemArtDraft.value
            ? await inventoryArts.ensureArt(props.detail.id, arts.value, itemArtDraft.value)
            : itemArtId.value
        : null
    const data = {
      name: itemName.value,
      internalId: normalisedInternalId || undefined,
      sizeId: itemSizeId.value ? Number(itemSizeId.value) : undefined,
      artId: resolvedArt,
      metadata: buildItemMetadata(fieldDefs.value, fieldValues.value),
    }
    const count = Math.max(1, Math.min(itemQuantity.value, 100))
    for (let i = 0; i < count; i++) {
      await inventory.createItem(props.detail.id, data)
    }
    showItemModal.value = false
    emit('itemsChanged')
  } catch {
    emit('error', t('common.error'))
  }
})

const assignMemberId = ref('')
const {isOpen: showAssignModal, target: assignTarget, open: openAssign} = useModalTarget<InventoryItem>(() => {
  assignMemberId.value = ''
})

async function submitAssign() {
  if (!assignTarget.value) return
  try {
    const memberId = assignMemberId.value ? Number(assignMemberId.value) : null
    const memberName = memberId ? getMemberName(memberId) : ''
    await inventory.assignItem(assignTarget.value.id, {memberId, memberName})
    showAssignModal.value = false
    emit('itemsChanged')
  } catch {
    emit('error', t('common.error'))
  }
}

const quickAssignMemberId = ref('')
const quickAssignSizeId = ref('')
const {isOpen: showQuickAssignModal, open: openQuickAssign} = useModalTarget<null>(() => {
  quickAssignMemberId.value = ''
  quickAssignSizeId.value = ''
})

async function submitQuickAssign() {
  if (!quickAssignMemberId.value) return
  try {
    const memberId = Number(quickAssignMemberId.value)
    const memberName = getMemberName(memberId)
    const sizeId = quickAssignSizeId.value ? Number(quickAssignSizeId.value) : undefined
    const item = await inventory.createItem(props.detail.id, {
      name: props.detail.name ?? '',
      sizeId,
      metadata: {fields: {}},
      ownerKind: ItemOwner.CLUSTER,
    })
    await inventory.assignItem(item.id, {memberId, memberName})
    showQuickAssignModal.value = false
    emit('itemsChanged')
  } catch {
    emit('error', t('common.error'))
  }
}

const showHistoryModal = ref(false)
const historyTarget = ref<InventoryItem | null>(null)
const historyEntries = ref<InventoryItemHistory[]>([])
const historyLoading = ref(false)

async function openHistory(item: InventoryItem) {
  historyTarget.value = item
  historyEntries.value = []
  historyLoading.value = true
  showHistoryModal.value = true
  try {
    historyEntries.value = await inventory.getItemHistory(item.id)
  } catch {
    emit('error', t('common.error'))
  } finally {
    historyLoading.value = false
  }
}

const {isOpen: showDeleteModal, target: deleteTarget, open: requestDelete} = useModalTarget<InventoryItem>()

async function confirmDelete() {
  if (!deleteTarget.value) return
  try {
    await inventory.deleteItem(deleteTarget.value.id)
    showDeleteModal.value = false
    deleteTarget.value = null
    emit('itemsChanged')
  } catch {
    emit('error', t('common.error'))
  }
}

defineExpose({openAdd, openEdit, openAssign, openQuickAssign, openHistory, requestDelete})
</script>

<template>
  <Modal v-model="showItemModal">
    <form class="space-y-4" @submit.prevent="saveItem">
      <SectionHeader>{{ t('inventory.edit.addItem') }}</SectionHeader>
      <AddItemFields
          v-model:internalId="itemInternalId"
          v-model:name="itemName"
          v-model:sizeId="itemSizeId"
          v-model:quantity="itemQuantity"
          v-model:artId="itemArtId"
          v-model:artDraft="itemArtDraft"
          :detail="detail"
          :arts="arts"
          :heterogeneous="heterogeneous"
      />
      <EditItemCustomFields :defs="fieldDefs" v-model="fieldValues"/>
      <div class="flex justify-end gap-3">
        <SecondaryButton type="button" @click="showItemModal = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="itemSaving || !itemName.trim() || fieldsInvalid" type="submit">
          {{ itemSaving ? t('common.loading') : t('common.save') }}
        </PrimaryButton>
      </div>
    </form>
  </Modal>

  <EditItemModal
      v-model="showEditModal"
      :item="editTarget"
      :has-sizes="detail.hasSizes"
      :sizes="detail.sizes ?? []"
      :heterogeneous="heterogeneous"
      @saved="emit('itemsChanged')"
  />

  <Modal v-model="showAssignModal">
    <div class="space-y-4">
      <SectionHeader>{{ t('inventory.edit.assignTitle') }}</SectionHeader>
      <p class="text-sm text-(--text-muted)">{{ t('inventory.edit.assignHint', {name: assignTarget?.name}) }}</p>
      <MemberPicker
          :members="pickable"
          :user-types="userTypes"
          :placeholder="t('inventory.edit.selectMember')"
          @select="assignMemberId = String($event)"
      />
      <MutedText v-if="assignMemberId" size="sm">
        {{ t('inventory.detail.assignTo') }} {{ getMemberName(Number(assignMemberId)) }}
      </MutedText>
      <div class="flex justify-end gap-3">
        <SecondaryButton @click="showAssignModal = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="!assignMemberId" @click="submitAssign">{{ t('inventory.edit.assignSubmit') }}</PrimaryButton>
      </div>
    </div>
  </Modal>

  <Modal v-model="showHistoryModal">
    <div class="space-y-4">
      <SectionHeader>{{ t('inventory.edit.historyTitle') }}</SectionHeader>
      <p class="text-sm text-(--text-muted)">{{ historyTarget?.name }}</p>
      <Spinner v-if="historyLoading" size="md"/>
      <EmptyState v-if="!historyLoading && historyEntries.length === 0" compact>{{ t('inventory.edit.noHistory') }}</EmptyState>
      <div v-if="!historyLoading && historyEntries.length > 0" class="space-y-2 max-h-80 overflow-y-auto">
        <div v-for="entry in historyEntries" :key="entry.id"
             class="flex items-center justify-between rounded-lg px-3 py-2 border border-bg-light-accent dark:border-bg-dark-accent">
          <span class="text-sm font-medium">{{ entry.memberName || getMemberName(entry.memberId) }}</span>
          <div class="text-xs text-(--text-muted) text-right">
            <div>{{ t('inventory.edit.givenOut') }}: {{ formatDate(entry.givenOut) || '–' }}</div>
            <div v-if="entry.returned">{{ t('inventory.edit.returned') }}: {{ formatDate(entry.returned) }}</div>
            <div v-else class="text-primary font-medium">{{ t('inventory.edit.currentlyAssigned') }}</div>
          </div>
        </div>
      </div>
      <div class="flex justify-end">
        <SecondaryButton @click="showHistoryModal = false">{{ t('common.cancel') }}</SecondaryButton>
      </div>
    </div>
  </Modal>

  <Modal v-model="showQuickAssignModal">
    <div class="space-y-4">
      <SectionHeader>{{ t('inventory.edit.quickAssign') }}</SectionHeader>
      <p class="text-sm text-(--text-muted)">{{ t('inventory.edit.quickAssignHint') }}</p>
      <MemberPicker
          :members="pickable"
          :user-types="userTypes"
          :placeholder="t('inventory.edit.selectMember')"
          @select="quickAssignMemberId = String($event)"
      />
      <MutedText v-if="quickAssignMemberId" size="sm">
        {{ t('inventory.detail.assignTo') }} {{ getMemberName(Number(quickAssignMemberId)) }}
      </MutedText>
      <div v-if="detail.hasSizes" class="space-y-1">
        <FieldLabel>{{ t('inventory.edit.itemSize') }}</FieldLabel>
        <SelectInput v-model="quickAssignSizeId">
          <option value="">&#x2013;</option>
          <option v-for="size in detail.sizes ?? []" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
        </SelectInput>
      </div>
      <div class="flex justify-end gap-3">
        <SecondaryButton @click="showQuickAssignModal = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="!quickAssignMemberId" @click="submitQuickAssign">{{ t('inventory.edit.assignSubmit') }}</PrimaryButton>
      </div>
    </div>
  </Modal>

  <ConfirmDeleteModal
      v-model="showDeleteModal"
      :message="t('inventory.edit.deleteConfirm', { name: deleteTarget?.name })"
      @confirm="confirmDelete"
  />
</template>

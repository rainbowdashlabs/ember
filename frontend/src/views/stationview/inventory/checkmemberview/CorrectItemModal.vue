/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import CorrectNewPiece from './correctitemmodal/CorrectNewPiece.vue'
import {inventoryFields} from '@/api'
import {InventoryTypes, ItemOwner, type InventoryItem, type ItemOwnerName} from '@/api/inventory'
import type {CorrectItemRequest} from '@/api/inventoryCheck'
import type {RequiredInventoryItem} from '@/api/inventory'
import type {InventoryFieldDefinition} from '@/api/inventoryFields'
import {buildItemMetadata} from '@/views/stationview/inventory/detailview/itemMetadata'

/**
 * Putting the record right about which piece somebody holds.
 *
 * <p>Not a movement and not an exchange: the piece named here is already in the member's hands, and
 * this only writes down that it is. What becomes of the piece coming off the record follows from who
 * owns it, so it is said rather than asked.
 *
 * <p>The size of the piece already on the record is not offered, here or anywhere on the check. Gear
 * is assumed to be described correctly, so a wrong size means the wrong piece, and the wrong piece is
 * replaced rather than edited.
 */
const props = defineProps<{
  item: InventoryItem | null
  req: RequiredInventoryItem | null
  availableItems: InventoryItem[]
  itemLabel: (item: InventoryItem, req: RequiredInventoryItem) => string
  busy?: boolean
  error?: string
}>()

const show = defineModel<boolean>({required: true})

const emit = defineEmits<{
  confirm: [payload: CorrectItemRequest]
}>()

const {t} = useI18n()

const source = ref<'STOCK' | 'NEW'>('NEW')
const pickedItemId = ref('')
const owner = ref<ItemOwnerName>(ItemOwner.STATION)
const sizeId = ref('')
const internalId = ref('')
const fieldValues = ref<Record<string, unknown>>({})
const fields = ref<InventoryFieldDefinition[]>([])

/**
 * The free stock a correction may reach for, which is what the station owns itself.
 *
 * <p>A piece belonging to the association is issued by the association, so a correction naming one
 * would claim a handover that never happened. Where the inventory holds nothing of the station's own,
 * nothing is offered and the piece is written down anew.
 */
const pickable = computed(() => props.availableItems.filter(item => item.ownerKind === ItemOwner.STATION))

const asksOwner = computed(() => props.req?.inventoryType === InventoryTypes.MIXED)

const needsSize = computed(() => Boolean(props.req?.hasSizes) && (props.req?.sizes.length ?? 0) > 0)

const ready = computed(() => (source.value === 'STOCK' ? pickedItemId.value !== '' : !needsSize.value || sizeId.value !== ''))

/** Where the piece coming off the record goes, which nobody is asked and everybody should be told. */
const oldPieceFate = computed(() => {
  const old = props.item
  if (!old) return ''
  if (old.ownerKind !== ItemOwner.CLUSTER) return t('inventory.check.correct.oldToStation')
  return old.ownerClusterId
      ? t('inventory.check.correct.oldToCluster')
      : t('inventory.check.correct.oldVanishes')
})

watch(show, async visible => {
  if (!visible || !props.req) return
  source.value = pickable.value.length > 0 ? 'STOCK' : 'NEW'
  pickedItemId.value = ''
  owner.value = props.req.inventoryType === InventoryTypes.EXTERNAL ? ItemOwner.CLUSTER : ItemOwner.STATION
  sizeId.value = ''
  internalId.value = ''
  fieldValues.value = {}
  fields.value = await inventoryFields.listFields(props.req.inventoryId).catch(() => [])
})

function confirm() {
  const req = props.req
  if (!req || !ready.value) return
  const makesANewPiece = source.value === 'NEW'
  emit('confirm', {
    inventoryId: req.inventoryId,
    oldItemId: props.item?.id ?? null,
    pickedItemId: makesANewPiece ? null : Number(pickedItemId.value),
    sizeId: makesANewPiece && sizeId.value ? Number(sizeId.value) : null,
    ownerKind: makesANewPiece ? owner.value : null,
    internalId: makesANewPiece ? internalId.value.trim() || null : null,
    metadata: makesANewPiece ? buildItemMetadata(fields.value, fieldValues.value) : null,
  })
}
</script>

<template>
  <Modal v-model="show">
    <SubHeader class="mb-1">{{ t('inventory.check.correct.title') }}</SubHeader>
    <p class="mb-3 text-sm text-(--text-muted)">{{ item?.name }}</p>

    <Alert v-if="error" variant="error" class="mb-3">{{ error }}</Alert>

    <p class="mb-2 text-sm">{{ t('inventory.check.correct.intro') }}</p>
    <p class="mb-4 text-sm text-(--text-muted)" data-testid="correct-old-piece">{{ oldPieceFate }}</p>

    <div v-if="pickable.length > 0" class="mb-3 space-y-1">
      <FieldLabel>{{ t('inventory.check.correct.source') }}</FieldLabel>
      <SelectInput v-model="source" class="w-full" data-testid="correct-source">
        <option value="STOCK">{{ t('inventory.check.correct.fromStock') }}</option>
        <option value="NEW">{{ t('inventory.check.correct.newPiece') }}</option>
      </SelectInput>
    </div>

    <div v-if="source === 'STOCK' && req" class="space-y-1">
      <FieldLabel>{{ t('inventory.check.correct.pick') }}</FieldLabel>
      <SelectInput v-model="pickedItemId" class="w-full" data-testid="correct-pick">
        <option value="" disabled>{{ t('inventory.check.selectItem') }}</option>
        <option v-for="avail in pickable" :key="avail.id" :value="String(avail.id)">
          {{ itemLabel(avail, req) }}
        </option>
      </SelectInput>
    </div>
    <CorrectNewPiece
        v-else-if="req"
        v-model:owner="owner"
        v-model:size-id="sizeId"
        v-model:internal-id="internalId"
        v-model:field-values="fieldValues"
        :req="req"
        :fields="fields"
        :asks-owner="asksOwner"
    />

    <div class="mt-4 flex justify-end gap-2">
      <SecondaryButton @click="show = false">{{ t('common.cancel') }}</SecondaryButton>
      <PrimaryButton :disabled="busy || !ready" data-testid="correct-confirm" @click="confirm">
        {{ t('inventory.check.correct.confirm') }}
      </PrimaryButton>
    </div>
  </Modal>
</template>

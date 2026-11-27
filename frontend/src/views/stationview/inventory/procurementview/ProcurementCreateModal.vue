/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ProcurementFields from './ProcurementFields.vue'
import {InventoryType, ItemOwner, type Inventory, type InventorySize} from '@/api/generated/schema'
import type { MemberLike } from '@/components/input/select/memberOption'
import { inventory, procurement } from '@/api'
import { useAsyncAction } from '@/composables/useAsyncAction'
import { useInventoryRoutes } from '@/composables/useInventoryRoutes'

const { t } = useI18n()

/**
 * Whether an order is placed for somebody.
 *
 * <p>A station orders for the person who will wear it. An association orders for its own store and hands
 * it out afterwards, so it is never asked who, and what arrives rests in its store rather than on a
 * person who does not exist there.
 */
const routes = useInventoryRoutes()
const forSomebody = computed(() => !!routes.member)

const modelValue = defineModel<boolean>({required: true})

const props = defineProps<{
  inventories: Inventory[]
  members: MemberLike[]
  /** Whose piece a new order starts with, where the dialog was opened for one owner in particular. */
  presetOwner?: ItemOwner
  /**
   * Whether a station's order may leave out whom it is for. A request noted down for the association
   * may be for the store, as a request sent to it may be.
   */
  memberOptional?: boolean
}>()

const emit = defineEmits<{
  (e: 'created'): void
}>()

const createInventoryId = ref<string>('')
const createMemberId = ref<string>('')
const createSizeId = ref<string>('')
const createNotes = ref('')
const createOwner = ref<ItemOwner | ''>('')
const createSuccess = ref(false)
const availableSizes = ref<InventorySize[]>([])

/** Whether the chosen inventory holds both owners' gear, so the order has to be given its owner. */
const mixed = computed(() => props.inventories
    .find(candidate => candidate.id === Number(createInventoryId.value))?.inventoryType === InventoryType.MIXED)

/**
 * An association orders for its own store, so a mixed inventory there brings its own piece without
 * asking. A station is asked, since a wrong answer records the wrong owner.
 */
const asksOwner = computed(() => forSomebody.value)

const canCreate = computed(() => !!createInventoryId.value
    && (!forSomebody.value || props.memberOptional || !!createMemberId.value)
    && (!mixed.value || !asksOwner.value || !!createOwner.value))

/** The owner sent with the order: only for a mixed inventory, where the type does not settle it. */
const sentOwner = computed<ItemOwner | undefined>(() => {
  if (!mixed.value) return undefined
  if (!asksOwner.value) return ItemOwner.CLUSTER
  return createOwner.value || undefined
})

function close() {
  modelValue.value = false
}

function reset() {
  createInventoryId.value = ''
  createMemberId.value = ''
  createSizeId.value = ''
  createNotes.value = ''
  createOwner.value = props.presetOwner ?? ''
  createSuccess.value = false
  availableSizes.value = []
  clearError()
}

async function onInventorySelected() {
  availableSizes.value = []
  createSizeId.value = ''
  const id = Number(createInventoryId.value)
  if (!id) return
  try {
    availableSizes.value = await inventory.listSizes(id)
  } catch {
    availableSizes.value = []
  }
}

/**
 * Places the order.
 *
 * <p>What goes wrong stays in this window. The window stays open on a failure, so sending the reason to
 * the page put it behind the very thing the reader was looking at, and the button simply looked dead.
 */
const {running: createSaving, failure: createFailure, run: submitCreate, clearError} = useAsyncAction(async () => {
  await procurement.createProcurement({
    inventoryId: Number(createInventoryId.value),
    memberId: createMemberId.value ? Number(createMemberId.value) : undefined,
    sizeId: createSizeId.value ? Number(createSizeId.value) : undefined,
    notes: createNotes.value || undefined,
    owner: sentOwner.value,
  })
  createSuccess.value = true
  emit('created')
})

watch(
  modelValue,
  (open) => {
    if (open) reset()
  },
)
</script>

<template>
  <Modal v-model="modelValue">
    <div class="space-y-4">
      <SubHeader>{{ t('procurement.createTitle') }}</SubHeader>

      <template v-if="createSuccess">
        <Alert variant="success">{{ t('inventory.check.procurementCreated') }}</Alert>
        <div class="flex justify-end">
          <SecondaryButton data-testid="procurement-close" @click="close">{{ t('common.close') }}</SecondaryButton>
        </div>
      </template>
      <template v-else>
        <FailureAlert :failure="createFailure"/>

        <ProcurementFields
            v-model:member-id="createMemberId"
            v-model:inventory-id="createInventoryId"
            v-model:size-id="createSizeId"
            v-model:notes="createNotes"
            v-model:owner="createOwner"
            :asks-owner="asksOwner"
            :available-sizes="availableSizes"
            :for-somebody="forSomebody"
            :inventories="inventories"
            :members="members"
            @inventory-selected="onInventorySelected"
        />

        <ButtonRow pair align="end">
          <SecondaryButton @click="close">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton
              :disabled="createSaving || !canCreate"
              data-testid="procurement-submit"
              @click="submitCreate"
          >
            {{ createSaving ? t('common.loading') : t('procurement.submit') }}
          </PrimaryButton>
        </ButtonRow>
      </template>
    </div>
  </Modal>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type {ExchangeRequestEntry, CreateExchangeRequest} from '@/api/exchanges'
import type {InventorySize} from '@/api/inventory'
import type {StationMember} from '@/api/types'
import type { ManagedMember } from '@/api/managedMembers'
import { ExchangeStatus } from '@/api/exchanges'
import { exchanges, inventory, managedMembers } from '@/api'
import { useSession } from '@/composables/useSession'
import { useAsyncAction } from '@/composables/useAsyncAction'
import ExchangeCreateStepMember from './exchangecreatemodal/ExchangeCreateStepMember.vue'
import ExchangeCreateStepItem, { type MemberItemOption } from './exchangecreatemodal/ExchangeCreateStepItem.vue'
import ExchangeCreateStepSize from './exchangecreatemodal/ExchangeCreateStepSize.vue'
import ExchangeCreateStepReason from './exchangecreatemodal/ExchangeCreateStepReason.vue'

const { t } = useI18n()
const { canManageInventory, isGuardian, sessionInfo } = useSession()

const model = defineModel<boolean>({ required: true })

const props = defineProps<{
  requests: ExchangeRequestEntry[]
  membersWithItems: Set<number>
  membersWithItemsList: StationMember[]
  managedWithItemsList: ManagedMember[]
  managed: ManagedMember[]
}>()

const emit = defineEmits<{
  created: []
  error: [msg: string]
}>()

const createStep = ref(1)
const createMemberId = ref<string>('')
const createItemId = ref<string>('')
const createNewSizeId = ref<string>('')
const createReason = ref('')

const createMemberItems = ref<MemberItemOption[]>([])
const createItemSizes = ref<InventorySize[]>([])
const createLoadingItems = ref(false)

const selectedCreateItem = computed(() => {
  const id = Number(createItemId.value)
  return createMemberItems.value.find(i => i.id === id) ?? null
})

const needsSizeStep = computed(() => createItemSizes.value.length > 0)
const totalCreateSteps = computed(() => needsSizeStep.value ? 4 : 3)
const isReasonStep = computed(() => createStep.value === totalCreateSteps.value)

const canCreate = computed(() => {
  if (!createItemId.value || !createReason.value.trim()) return false
  if (needsSizeStep.value && !createNewSizeId.value) return false
  return true
})

watch(model, (open) => {
  if (open) {
    createStep.value = 1
    createMemberId.value = ''
    createItemId.value = ''
    createNewSizeId.value = ''
    createReason.value = ''
    createMemberItems.value = []
    createItemSizes.value = []
    if (!canManageInventory() && !(isGuardian() && props.managed.length > 0)) {
      createMemberId.value = String(sessionInfo.value?.member?.id ?? '')
      createStep.value = 2
      loadCreateMemberItems()
    }
  }
})

async function loadCreateMemberItems() {
  createLoadingItems.value = true
  createMemberItems.value = []
  createItemId.value = ''
  createNewSizeId.value = ''
  createItemSizes.value = []
  const mid = Number(createMemberId.value)
  if (!mid) { createLoadingItems.value = false; return }
  try {
    const isOwnMember = mid === sessionInfo.value?.member?.id
    let items
    if (isOwnMember) {
      items = await inventory.myItems()
    } else if (canManageInventory()) {
      items = await inventory.memberItems(mid)
    } else {
      items = await managedMembers.getMemberInventory(mid)
    }
    const activeExchangeItemIds = new Set(
      props.requests
        .filter(r => r.status !== ExchangeStatus.DONE && r.itemId)
        .map(r => r.itemId!)
    )
    createMemberItems.value = items
      .filter(i => !i.lostAt && !activeExchangeItemIds.has(i.id))
      .map(i => ({ id: i.id, inventoryId: i.inventoryId, name: i.name ?? '', internalId: i.internalId ?? '', sizeId: i.sizeId ?? null, sizeName: i.sizeName ?? null, inventoryName: i.inventoryName }))
  } catch {}
  createLoadingItems.value = false
}

async function onCreateItemSelected() {
  createItemSizes.value = []
  createNewSizeId.value = ''
  const item = selectedCreateItem.value
  if (!item) return
  try {
    createItemSizes.value = await inventory.listSizes(item.inventoryId)
  } catch {}
}

function createStepNext() {
  if (createStep.value === 1) {
    createStep.value = 2
    loadCreateMemberItems()
  } else if (createStep.value === 2) {
    onCreateItemSelected()
    createStep.value = needsSizeStep.value ? 3 : totalCreateSteps.value
  } else if (createStep.value === 3 && needsSizeStep.value) {
    createStep.value = 4
  }
}

async function createStepNextFromItem() {
  await onCreateItemSelected()
  createStep.value = needsSizeStep.value ? 3 : totalCreateSteps.value
}

function createStepBack() {
  if (createStep.value > 1) {
    createStep.value = createStep.value - 1
  }
}

const {running: createSaving, run: runCreate} = useAsyncAction(async () => {
  const item = selectedCreateItem.value
  const data: CreateExchangeRequest = {
    memberId: Number(createMemberId.value) || undefined,
    inventoryId: item?.inventoryId ?? 0,
    itemId: item?.id ?? undefined,
    oldSizeId: item?.sizeId ?? undefined,
    newSizeId: createNewSizeId.value ? Number(createNewSizeId.value) : undefined,
    reason: createReason.value,
  }
  await exchanges.createExchange(data)
  model.value = false
  emit('created')
  return true
})

async function submitCreate() {
  const ok = await runCreate()
  if (!ok) emit('error', t('common.error'))
}
</script>

<template>
  <Modal v-model="model">
    <div class="space-y-4">
      <SubHeader>{{ t('exchanges.createTitle') }}</SubHeader>
      <p class="text-xs text-(--text-muted)">{{ t('exchanges.step') }} {{ createStep }} / {{ totalCreateSteps }}</p>
      <ExchangeCreateStepMember
        v-if="createStep === 1"
        v-model="createMemberId"
        :members-with-items="membersWithItems"
        :members-with-items-list="membersWithItemsList"
        :managed-with-items-list="managedWithItemsList"
        :managed="managed"
        @next="createStepNext"
        @cancel="model = false"
      />
      <ExchangeCreateStepItem
        v-if="createStep === 2"
        v-model="createItemId"
        :loading="createLoadingItems"
        :items="createMemberItems"
        @next="createStepNextFromItem"
        @back="createStepBack"
      />
      <ExchangeCreateStepSize
        v-if="createStep === 3 && needsSizeStep"
        v-model="createNewSizeId"
        :selected-item="selectedCreateItem"
        :sizes="createItemSizes"
        @next="createStepNext"
        @back="createStepBack"
      />
      <ExchangeCreateStepReason
        v-if="isReasonStep"
        v-model="createReason"
        :selected-item="selectedCreateItem"
        :new-size-id="createNewSizeId"
        :sizes="createItemSizes"
        :saving="createSaving"
        :can-submit="canCreate"
        @submit="submitCreate"
        @back="createStepBack"
      />
    </div>
  </Modal>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import type {
  ExchangeRequestEntry,
  InventorySize,
  CreateExchangeRequest,
  StationMember,
} from '@/api/types'
import type { ManagedMember } from '@/api/managedMembers'
import { ExchangeStatus } from '@/api/types'
import { exchanges, inventory, managedMembers } from '@/api'
import { useSession } from '@/composables/useSession'
import MutedText from '@/components/typography/MutedText.vue'

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
const createSaving = ref(false)
const createMemberItems = ref<{ id: number; inventoryId: number; name: string; internalId: string; sizeId: number | null; sizeName: string | null; inventoryName: string }[]>([])
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
    // For non-managers, skip member selection and load own items
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
        .filter(r => r.status !== ExchangeStatus.EXCHANGED && r.itemId)
        .map(r => r.itemId!)
    )
    createMemberItems.value = items
      .filter(i => !i.lostAt && !activeExchangeItemIds.has(i.id))
      .map(i => ({ id: i.id, inventoryId: i.inventoryId, name: i.name ?? '', internalId: i.internalId ?? '', sizeId: i.sizeId ?? null, sizeName: i.sizeName ?? null, inventoryName: i.inventoryName }))
  } catch { /* ignore */ }
  createLoadingItems.value = false
}

async function onCreateItemSelected() {
  createItemSizes.value = []
  createNewSizeId.value = ''
  const item = selectedCreateItem.value
  if (!item) return
  try {
    createItemSizes.value = await inventory.listSizes(item.inventoryId)
  } catch { /* ignore */ }
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

async function submitCreate() {
  createSaving.value = true
  try {
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
  } catch {
    emit('error', t('common.error'))
  } finally {
    createSaving.value = false
  }
}
</script>

<template>
  <Modal v-model="model">
    <div class="space-y-4">
      <SectionHeader>{{ t('exchanges.createTitle') }}</SectionHeader>
      <p class="text-xs text-(--text-muted)">{{ t('exchanges.step') }} {{ createStep }} / {{ totalCreateSteps }}</p>

      <!-- Step 1: Select member -->
      <template v-if="createStep === 1">
        <div class="space-y-1">
          <FieldLabel>{{ t('exchanges.member') }}</FieldLabel>
          <SelectInput v-if="canManageInventory()" v-model="createMemberId">
            <option value="" disabled>{{ t('exchanges.selectMember') }}</option>
            <option v-for="m in membersWithItemsList" :key="m.id" :value="String(m.id)">
              {{ m.name || m.email || `#${m.id}` }}
            </option>
          </SelectInput>
          <SelectInput v-else-if="isGuardian() && managed.length > 0" v-model="createMemberId">
            <option v-if="membersWithItems.has(sessionInfo?.member?.id ?? 0)" :value="String(sessionInfo?.member?.id ?? '')">{{ t('profile.myInventorySelf') }}</option>
            <option v-for="m in managedWithItemsList" :key="m.id" :value="String(m.id)">
              {{ m.name || m.email }}
            </option>
          </SelectInput>
        </div>
        <div class="flex justify-end gap-3">
          <SecondaryButton @click="model = false">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton :disabled="!createMemberId" @click="createStepNext">
            {{ t('exchanges.stepNext') }}
          </PrimaryButton>
        </div>
      </template>

      <!-- Step 2: Select item -->
      <template v-if="createStep === 2">
        <Spinner v-if="createLoadingItems" size="md" />
        <template v-else>
          <MutedText tag="div" size="sm" class="py-2" v-if="createMemberItems.length === 0">
            {{ t('exchanges.noItemsForMember') }}
          </MutedText>
          <div v-else class="space-y-1">
            <FieldLabel>{{ t('exchanges.selectItem') }}</FieldLabel>
            <SelectInput v-model="createItemId">
              <option value="" disabled>{{ t('exchanges.selectItem') }}</option>
              <option v-for="item in createMemberItems" :key="item.id" :value="String(item.id)">
                {{ item.inventoryName }} — {{ item.name }}
                {{ item.sizeName ?? '' }}
                {{ item.internalId ? `(${item.internalId})` : '' }}
              </option>
            </SelectInput>
          </div>
        </template>
        <div class="flex justify-between">
          <SecondaryButton @click="createStepBack">{{ t('common.back') }}</SecondaryButton>
          <PrimaryButton :disabled="!createItemId" @click="createStepNextFromItem">
            {{ t('exchanges.stepNext') }}
          </PrimaryButton>
        </div>
      </template>

      <!-- Step 3: Select new size (conditional) -->
      <template v-if="createStep === 3 && needsSizeStep">
        <p v-if="selectedCreateItem" class="text-sm">
          {{ selectedCreateItem.inventoryName }} — {{ selectedCreateItem.name }}
          <span class="text-(--text-muted)">{{ selectedCreateItem.sizeName ?? t('common.unisize') }}</span>
        </p>
        <div class="space-y-1">
          <FieldLabel>{{ t('exchanges.newSize') }}</FieldLabel>
          <SelectInput v-model="createNewSizeId">
            <option value="" disabled>{{ t('exchanges.noSize') }}</option>
            <option v-for="size in createItemSizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
          </SelectInput>
        </div>
        <div class="flex justify-between">
          <SecondaryButton @click="createStepBack">{{ t('common.back') }}</SecondaryButton>
          <PrimaryButton :disabled="!createNewSizeId" @click="createStepNext">
            {{ t('exchanges.stepNext') }}
          </PrimaryButton>
        </div>
      </template>

      <!-- Final step: Reason -->
      <template v-if="isReasonStep">
        <p v-if="selectedCreateItem" class="text-sm">
          {{ selectedCreateItem.inventoryName }} — {{ selectedCreateItem.name }}
          <span class="text-(--text-muted)">{{ selectedCreateItem.sizeName ?? t('common.unisize') }}</span>
          <template v-if="createNewSizeId">
            &rarr; <span class="font-medium">{{ createItemSizes.find(s => s.id === Number(createNewSizeId))?.label }}</span>
          </template>
        </p>
        <div class="space-y-1">
          <FieldLabel>{{ t('exchanges.reason') }}</FieldLabel>
          <TextAreaInput v-model="createReason" :placeholder="t('exchanges.reasonPlaceholder')" />
        </div>
        <div class="flex justify-between">
          <SecondaryButton @click="createStepBack">{{ t('common.back') }}</SecondaryButton>
          <PrimaryButton :disabled="createSaving || !canCreate" @click="submitCreate">
            {{ createSaving ? t('common.loading') : t('exchanges.submit') }}
          </PrimaryButton>
        </div>
      </template>
    </div>
  </Modal>
</template>

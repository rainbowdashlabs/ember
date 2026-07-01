/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import type { Inventory, InventorySize, StationMember } from '@/api/types'
import { inventory, procurement } from '@/api'

const { t } = useI18n()

const modelValue = defineModel<boolean>({required: true})

defineProps<{
  inventories: Inventory[]
  members: StationMember[]
}>()

const emit = defineEmits<{
  (e: 'created'): void
  (e: 'error'): void
}>()

const createInventoryId = ref<string>('')
const createMemberId = ref<string>('')
const createSizeId = ref<string>('')
const createNotes = ref('')
const createSaving = ref(false)
const createSuccess = ref(false)
const availableSizes = ref<InventorySize[]>([])

const canCreate = computed(() => !!createInventoryId.value && !!createMemberId.value)

function memberDisplayName(member: StationMember): string {
  if (member.name && member.name.trim()) return member.name
  return member.email ?? `#${member.id}`
}

function close() {
  modelValue.value = false
}

function reset() {
  createInventoryId.value = ''
  createMemberId.value = ''
  createSizeId.value = ''
  createNotes.value = ''
  createSuccess.value = false
  availableSizes.value = []
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

async function submitCreate() {
  createSaving.value = true
  try {
    await procurement.createProcurement({
      inventoryId: Number(createInventoryId.value),
      memberId: Number(createMemberId.value),
      sizeId: createSizeId.value ? Number(createSizeId.value) : undefined,
      notes: createNotes.value || undefined,
    })
    createSuccess.value = true
    emit('created')
  } catch {
    emit('error')
  } finally {
    createSaving.value = false
  }
}

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
          <SecondaryButton @click="close">{{ t('common.close') }}</SecondaryButton>
        </div>
      </template>
      <template v-else>
        <div class="space-y-1">
          <FieldLabel>{{ t('procurement.member') }}</FieldLabel>
          <SelectInput v-model="createMemberId">
            <option value="" disabled>{{ t('procurement.selectMember') }}</option>
            <option v-for="m in members" :key="m.id" :value="String(m.id)">{{ memberDisplayName(m) }}</option>
          </SelectInput>
        </div>

        <div class="space-y-1">
          <FieldLabel>{{ t('procurement.inventory') }}</FieldLabel>
          <SelectInput v-model="createInventoryId" @change="onInventorySelected">
            <option value="" disabled>{{ t('procurement.selectInventory') }}</option>
            <option v-for="inv in inventories" :key="inv.id" :value="String(inv.id)">{{ inv.name }}</option>
          </SelectInput>
        </div>

        <div v-if="availableSizes.length > 0" class="space-y-1">
          <FieldLabel>{{ t('procurement.size') }}</FieldLabel>
          <SelectInput v-model="createSizeId">
            <option value="">{{ t('procurement.noSize') }}</option>
            <option v-for="size in availableSizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
          </SelectInput>
        </div>

        <div class="space-y-1">
          <FieldLabel>{{ t('procurement.notes') }}</FieldLabel>
          <TextAreaInput v-model="createNotes" :placeholder="t('procurement.notesPlaceholder')" />
        </div>

        <div class="flex justify-end gap-3">
          <SecondaryButton @click="close">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton :disabled="createSaving || !canCreate" @click="submitCreate">
            {{ createSaving ? t('common.loading') : t('procurement.submit') }}
          </PrimaryButton>
        </div>
      </template>
    </div>
  </Modal>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Modal from '@/components/feedback/Modal.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Alert from '@/components/feedback/Alert.vue'
import {inventory, movements} from '@/api'
import type {Inventory, InventorySize} from '@/api/inventory'
import {MovementPurpose} from '@/api/movements'
import {useAsyncAction} from '@/composables/useAsyncAction'

/**
 * Asking the association above for a piece the station does not have.
 *
 * <p>The other movements a station raises are about a piece it is holding. This one is not: it names a
 * kind of thing and a size, and the association picks the actual piece when it sends one, or refuses
 * with a reason. That is why it asks for an inventory rather than an item.
 *
 * <p>Absent where there is nobody above keeping gear in Ember, because then there is nobody to ask.
 */
const props = defineProps<{
  /** What the association above is called, or null when there is none. */
  ownerName: string | null
  inventories: Inventory[]
}>()

const emit = defineEmits<{
  requested: []
}>()

const {t} = useI18n()

const asking = ref(false)
const inventoryId = ref('')
const sizeId = ref('')
const reason = ref('')
const sizes = ref<InventorySize[]>([])

watch(inventoryId, async (next) => {
  sizeId.value = ''
  sizes.value = next ? await inventory.listSizes(Number(next)) : []
})

const {running, error, run: send} = useAsyncAction(async () => {
  if (!inventoryId.value) return
  await movements.createMovement({
    purpose: MovementPurpose.REQUEST,
    inventoryId: Number(inventoryId.value),
    newSizeId: sizeId.value ? Number(sizeId.value) : undefined,
    reason: reason.value.trim() || undefined,
  })
  asking.value = false
  inventoryId.value = ''
  sizeId.value = ''
  reason.value = ''
  emit('requested')
})
</script>

<template>
  <NeutralContainer v-if="props.ownerName" data-testid="request-from-owner" class="space-y-3">
    <SectionHeader>{{ t('inventory.request.title') }}</SectionHeader>
    <MutedText tag="p" size="sm">{{ t('inventory.request.hint', {name: props.ownerName}) }}</MutedText>

    <SecondaryButton :icon="['fas', 'hand-holding']" @click="asking = true">
      {{ t('inventory.request.ask') }}
    </SecondaryButton>

    <Modal v-if="asking" model-value @update:model-value="(open) => { if (!open) asking = false }">
      <div class="space-y-4">
        <SectionHeader>{{ t('inventory.request.title') }}</SectionHeader>
        <Alert v-if="error" variant="error">{{ error }}</Alert>

        <div class="space-y-1">
          <FieldLabel>{{ t('inventory.request.what') }}</FieldLabel>
          <SelectInput v-model="inventoryId">
            <option value="" disabled>{{ t('inventory.request.selectWhat') }}</option>
            <option v-for="inv in props.inventories" :key="inv.id" :value="String(inv.id)">{{ inv.name }}</option>
          </SelectInput>
        </div>

        <div v-if="sizes.length > 0" class="space-y-1">
          <FieldLabel>{{ t('inventory.request.size') }}</FieldLabel>
          <SelectInput v-model="sizeId">
            <option value="">{{ t('inventory.request.anySize') }}</option>
            <option v-for="size in sizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
          </SelectInput>
        </div>

        <div class="space-y-1">
          <FieldLabel>{{ t('inventory.request.reason') }}</FieldLabel>
          <TextAreaInput v-model="reason" :placeholder="t('inventory.request.reasonPlaceholder')"/>
        </div>

        <div class="flex justify-end gap-3">
          <SecondaryButton @click="asking = false">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton :disabled="running || !inventoryId" @click="send">
            {{ running ? t('common.loading') : t('inventory.request.send') }}
          </PrimaryButton>
        </div>
      </div>
    </Modal>
  </NeutralContainer>
</template>

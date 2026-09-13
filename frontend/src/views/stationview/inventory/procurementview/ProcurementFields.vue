/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import {fromMember, userTypesOf} from '@/components/input/select/memberOption'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import type { Inventory, InventorySize } from '@/api/inventory'
import type { StationMember } from '@/api/types'

/**
 * What is being ordered, and for whom where there is a whom.
 *
 * <p>An association orders for its own store and hands out afterwards, so it is never asked who, and the
 * question is left out rather than shown with nobody in it.
 */
const memberId = defineModel<string>('memberId', {required: true})
const inventoryId = defineModel<string>('inventoryId', {required: true})
const sizeId = defineModel<string>('sizeId', {required: true})
const notes = defineModel<string>('notes', {required: true})

const props = defineProps<{
  inventories: Inventory[]
  members: StationMember[]
  availableSizes: InventorySize[]
  /** Whether the order is placed for a person, which a station's is and an association's is not. */
  forSomebody: boolean
}>()

const emit = defineEmits<{
  (e: 'inventorySelected'): void
}>()

const { t } = useI18n()

const options = computed(() => props.members.map(fromMember))
const userTypes = computed(() => userTypesOf(options.value))
</script>

<template>
  <div v-if="forSomebody" class="space-y-1">
    <FieldLabel>{{ t('procurement.member') }}</FieldLabel>
    <MemberSelectInput
        v-model="memberId"
        :members="options"
        :user-types="userTypes"
        :placeholder="t('procurement.selectMember')"
    />
  </div>

  <div class="space-y-1">
    <FieldLabel>{{ t('procurement.inventory') }}</FieldLabel>
    <SelectInput v-model="inventoryId" data-testid="procurement-inventory" @change="emit('inventorySelected')">
      <option value="" disabled>{{ t('procurement.selectInventory') }}</option>
      <option v-for="inv in inventories" :key="inv.id" :value="String(inv.id)">{{ inv.name }}</option>
    </SelectInput>
  </div>

  <div v-if="availableSizes.length > 0" class="space-y-1">
    <FieldLabel>{{ t('procurement.size') }}</FieldLabel>
    <SelectInput v-model="sizeId">
      <option value="">{{ t('procurement.noSize') }}</option>
      <option v-for="size in availableSizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
    </SelectInput>
  </div>

  <div class="space-y-1">
    <FieldLabel>{{ t('procurement.notes') }}</FieldLabel>
    <TextAreaInput v-model="notes" data-testid="procurement-notes"
                   :placeholder="t('procurement.notesPlaceholder')" />
  </div>
</template>

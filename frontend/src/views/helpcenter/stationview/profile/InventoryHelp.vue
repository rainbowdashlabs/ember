/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import HelpRoleToggle from '@/components/helpcenter/HelpRoleToggle.vue'
import type {HelpRole} from '@/components/helpcenter/HelpRoleToggle.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import InventoryItemCard from '@/views/stationview/inventory/InventoryItemCard.vue'
import Modal from '@/components/feedback/Modal.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import type {MyInventoryItem} from '@/api/inventory'
import type {ExchangeRequestEntry} from '@/api/types'

const {t} = useI18n()

const roles: HelpRole[] = [
  {key: 'member', label: t('helpCenter.roles.member')},
  {key: 'memberManager', label: t('helpCenter.roles.memberManager')},
]
const activeRole = ref('')
const showExchangeModal = ref(false)

const dummyHelm: MyInventoryItem = {
  id: 1, inventoryId: 1, inventoryName: 'Helme', name: 'Helm #12',
  sizeName: 'M', sizeId: 2, internalId: 'HLM-2024-012', lostAt: null,
}
const dummyJacke: MyInventoryItem = {
  id: 2, inventoryId: 2, inventoryName: 'Jacken', name: 'Einsatzjacke #7',
  sizeName: 'L', sizeId: 3, internalId: 'JCK-2023-007', lostAt: null,
}
const dummyJackeExchange = {
  id: 1, memberId: 1, memberName: 'Max Mustermann', itemId: 2,
  inventoryId: 2, inventoryName: 'Jacken', inventoryType: 'SIZED',
  oldSizeId: 3, oldSizeLabel: 'L', newSizeId: 4, newSizeLabel: 'XL',
  reason: 'Zu klein geworden', status: 'ANNOUNCED',
  createdAt: '2026-01-15T10:00:00Z', updatedAt: '2026-01-15T10:00:00Z',
} as ExchangeRequestEntry
const dummyStiefel: MyInventoryItem = {
  id: 3, inventoryId: 3, inventoryName: 'Stiefel', name: 'Stiefel #3',
  sizeName: '42', sizeId: 4, lostAt: '2026-03-01T00:00:00Z',
}
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryMy.title')" :subtitle="t('helpCenter.inventoryMy.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryMy.whatIs')">
      <p>{{ t('helpCenter.inventoryMy.whatIsText') }}</p>
    </HelpSection>

    <HelpRoleToggle v-model="activeRole" :roles="roles"/>

    <!-- Dummy: Member selector for managers -->
    <template v-if="activeRole === 'memberManager'">
      <div class="flex items-center justify-between flex-wrap gap-2">
        <SectionHeader>{{ t('profile.inventory') }}</SectionHeader>
        <SelectInput model-value="self" class="w-48 text-sm">
          <option value="self">{{ t('profile.myInventorySelf') }}</option>
          <option value="1">Lena Mustermann</option>
          <option value="2">Tim Mustermann</option>
        </SelectInput>
      </div>
    </template>

    <!-- Dummy: Inventory groups using real InventoryItemCard -->
    <div class="space-y-6">
      <!-- Group: Helme -->
      <div>
        <div class="flex items-center justify-between mb-2">
          <SubHeader>Helme</SubHeader>
          <span class="text-sm text-(--text-muted)">1 / 1</span>
        </div>
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
          <InventoryItemCard :item="dummyHelm" :show-exchange-button="true" @request-exchange="showExchangeModal = true"/>
        </div>
      </div>

      <!-- Group: Jacken (with exchange) -->
      <div>
        <div class="flex items-center justify-between mb-2">
          <SubHeader>Jacken</SubHeader>
          <span class="text-sm text-(--text-muted)">1 / 1</span>
        </div>
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
          <InventoryItemCard :item="dummyJacke" :exchange="dummyJackeExchange" :show-exchange-button="true"/>
        </div>
      </div>

      <!-- Group: Stiefel (with lost item) -->
      <div>
        <div class="flex items-center justify-between mb-2">
          <SubHeader>Stiefel</SubHeader>
          <span class="text-sm text-(--text-muted)">
            1 / 1
            <span class="text-error">(1 fehlt)</span>
          </span>
        </div>
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
          <InventoryItemCard :item="dummyStiefel" :show-exchange-button="false"/>
        </div>
      </div>
    </div>

    <HelpSection :title="t('helpCenter.inventoryMy.exchangeTitle')">
      <p>{{ t('helpCenter.inventoryMy.exchangeText') }}</p>
    </HelpSection>

    <!-- Exchange request modal using real Modal -->
    <Modal v-model="showExchangeModal">
      <div class="space-y-3">
        <SectionHeader>{{ t('profile.requestExchange') }}</SectionHeader>
        <p class="text-sm">
          Helme — Helm #12 <SizeBadge>M</SizeBadge>
        </p>
        <div class="space-y-1">
          <FieldLabel>{{ t('exchanges.newSize') }}</FieldLabel>
          <SelectInput model-value="">
            <option value="" disabled>{{ t('exchanges.selectNewSize') }}</option>
            <option value="1">S</option>
            <option value="2">M</option>
            <option value="3">L</option>
            <option value="4">XL</option>
          </SelectInput>
        </div>
        <TextAreaInput model-value="" :placeholder="t('profile.exchangeReasonPlaceholder')" :rows="3"/>
        <div class="flex justify-end gap-2">
          <SecondaryButton @click="showExchangeModal = false">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton disabled>{{ t('profile.submitExchange') }}</PrimaryButton>
        </div>
      </div>
    </Modal>

    <HelpTip>{{ t('helpCenter.inventoryMy.tip') }}</HelpTip>
  </HelpArticle>
</template>

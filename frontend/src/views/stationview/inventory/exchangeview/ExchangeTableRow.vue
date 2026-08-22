/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import ExchangeStatusBadge from './ExchangeStatusBadge.vue'
import Td from '@/components/table/Td.vue'
import TRow from '@/components/table/TRow.vue'
import {ExchangeStatus, type ExchangeRequestEntry} from '@/api/exchanges'
import { inventoryTypeBadge, inventoryTypeLabel as toInventoryTypeLabel } from '@/util/inventoryType'
import { formatDate } from '@/util/format'

const routes = useInventoryRoutes()

const { t } = useI18n()
const router = useRouter()

const props = defineProps<{
  request: ExchangeRequestEntry
  exportMode: boolean
  showMemberColumn: boolean
  canManageExchanges: boolean
  selected: boolean
}>()

const emit = defineEmits<{
  (e: 'toggle-export'): void
  (e: 'open-log'): void
  (e: 'start-update'): void
  (e: 'delete'): void
}>()

function inventoryTypeLabel(type?: string | null): string {
  return toInventoryTypeLabel(t, type)
}
</script>

<template>
  <TRow>
    <td v-if="exportMode" class="px-1 py-2.5 w-8">
      <CheckboxInput :model-value="selected" @update:model-value="emit('toggle-export')" />
    </td>
    <Td v-if="showMemberColumn">
      <SecondaryButton
        v-if="canManageExchanges && routes.member"
        class="!bg-transparent !p-0 text-primary font-normal hover:underline cursor-pointer"
        @click="routes.member && router.push({ name: routes.member, params: { memberId: request.memberId } })"
      ><MemberName :identity="request.memberIdentity ?? null"/></SecondaryButton>
      <MemberName v-else :identity="request.memberIdentity ?? null"/>
    </Td>
    <Td class="font-medium">{{ request.inventoryName }}</Td>
    <Td v-if="canManageExchanges">
      <component :is="inventoryTypeBadge(request.inventoryType)">{{ inventoryTypeLabel(request.inventoryType) }}</component>
    </Td>
    <Td>{{ request.oldSizeLabel ?? t('common.unisize') }}</Td>
    <Td>{{ request.newSizeLabel ?? t('common.unisize') }}</Td>
    <Td><ExchangeStatusBadge :status="request.status" /></Td>
    <Td class="text-(--text-muted) max-w-48 truncate">{{ request.reason }}</Td>
    <Td muted>
      {{ formatDate(request.createdAt) }}
      <span v-if="request.createdByName" class="block text-xs italic">{{ t('common.createdBy', { name: request.createdByName }) }}</span>
    </Td>
    <Td align="right">
      <div class="flex items-center justify-end gap-1">
        <SecondaryButton @click="emit('open-log')">
          <font-awesome-icon :icon="['fas', 'clock-rotate-left']" />
        </SecondaryButton>
        <SecondaryButton v-if="canManageExchanges && request.status !== ExchangeStatus.DONE" @click="emit('start-update')">
          <font-awesome-icon :icon="['fas', 'arrow-right']" />
        </SecondaryButton>
        <DeleteButton v-if="canManageExchanges" @click="emit('delete')" />
      </div>
    </Td>
  </TRow>
</template>

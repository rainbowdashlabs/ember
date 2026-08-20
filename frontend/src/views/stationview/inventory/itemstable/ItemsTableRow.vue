/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import Td from '@/components/table/Td.vue'
import TRow from '@/components/table/TRow.vue'
import {ItemOwner, type InventoryItem} from '@/api/inventory'
import type { MemberIdentity } from '@/api/types'
import type { InventoryItemActionEmits } from '../itemEmits'
import ItemActions from './ItemActions.vue'

withDefaults(defineProps<{
  item: InventoryItem
  hasSizes: boolean
  isMixed: boolean
  showActions: boolean
  showHistory: boolean
  lentOut: boolean
  lentToStationName: string | null
  sizeLabel: string
  memberIdentity?: MemberIdentity
  formattedLostAt: string
  locationLabel?: string
  showAssigned?: boolean
  fieldValues?: string[]
}>(), {
  showAssigned: true,
  fieldValues: () => [],
})

const emit = defineEmits<InventoryItemActionEmits>()

const { t } = useI18n()
</script>

<template>
  <TRow :class="item.lostAt ? 'opacity-60' : ''">
    <Td class="font-medium">
      <router-link :to="{ name: 'inventory-item-detail', params: { id: item.id } }" class="hover:text-primary hover:underline">{{ item.name }}</router-link>
      <span v-if="item.lostAt" class="ml-2 text-xs text-error font-normal">
        {{ t('inventory.edit.lost') }} ({{ formattedLostAt }})
      </span>
      <template v-if="lentOut">
        <span class="ml-2 inline-flex items-center gap-1">
          <InfoBadge>
            <font-awesome-icon :icon="['fas', 'arrow-right-arrow-left']" class="mr-0.5 h-2.5 w-2.5"/>
            {{ t('inventory.detail.lentTo') }} {{ lentToStationName }}
          </InfoBadge>
        </span>
      </template>
    </Td>
    <Td muted>{{ item.internalId || '–' }}</Td>
    <Td v-if="hasSizes"><SizeBadge v-if="sizeLabel" :lost="!!item.lostAt">{{ sizeLabel }}</SizeBadge></Td>
    <Td v-if="isMixed">
      <PrimaryBadge v-if="item.ownerKind === ItemOwner.STATION">{{ t('inventory.edit.ownerStation') }}</PrimaryBadge>
      <SecondaryBadge v-else-if="item.ownerKind === ItemOwner.CLUSTER">{{ t('inventory.edit.ownerCluster') }}</SecondaryBadge>
      <span v-else class="text-(--text-muted)">–</span>
    </Td>
    <Td v-if="showAssigned">
      <router-link v-if="item.assignedTo" :to="{ name: 'inventory-member', params: { memberId: item.assignedTo } }" class="inline-block font-medium hover:text-primary hover:underline" @click.stop><MemberName :identity="memberIdentity"/></router-link>
      <span v-else-if="locationLabel" class="inline-flex items-center gap-1 text-(--text-muted)">
        <font-awesome-icon :icon="['fas', 'box']" class="h-3 w-3"/>
        {{ locationLabel }}
      </span>
      <span v-else class="text-(--text-muted)">–</span>
    </Td>
    <Td v-for="(value, index) in fieldValues" :key="index" muted>{{ value || '–' }}</Td>
    <Td v-if="showActions || showHistory" align="right">
      <div class="flex items-center justify-end gap-0.5">
        <ItemActions :item="item" :show-actions="showActions" :lent-out="lentOut"
                     @assign="emit('assign', $event)"
                     @unassign="emit('unassign', $event)"
                     @edit="emit('edit', $event)"
                     @mark-lost="emit('markLost', $event)"
                     @mark-found="emit('markFound', $event)"
                     @history="emit('history', $event)"
                     @delete="emit('delete', $event)"/>
      </div>
    </Td>
  </TRow>
</template>

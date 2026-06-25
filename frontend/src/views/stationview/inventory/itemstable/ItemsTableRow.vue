/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import Td from '@/components/table/Td.vue'
import TRow from '@/components/table/TRow.vue'
import type { InventoryItem, MemberIdentity } from '@/api/types'
import { ItemSource } from '@/api/types'
import type { InventoryItemActionEmits } from '../itemEmits'
import ItemActions from './ItemActions.vue'

defineProps<{
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
}>()

const emit = defineEmits<InventoryItemActionEmits>()

const { t } = useI18n()
const router = useRouter()
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
    <Td v-if="hasSizes"><SizeBadge :lost="!!item.lostAt">{{ sizeLabel }}</SizeBadge></Td>
    <Td v-if="isMixed">
      <PrimaryBadge v-if="item.itemSource === ItemSource.INTERNAL">{{ t('inventory.edit.sourceInternal') }}</PrimaryBadge>
      <SecondaryBadge v-else-if="item.itemSource === ItemSource.EXTERNAL">{{ t('inventory.edit.sourceExternal') }}</SecondaryBadge>
      <span v-else class="text-(--text-muted)">–</span>
    </Td>
    <Td>
      <SecondaryButton v-if="item.assignedTo" class="!bg-transparent !p-0 text-primary font-medium hover:underline" @click.stop="router.push({ name: 'inventory-member', params: { memberId: item.assignedTo } })"><MemberName :identity="memberIdentity"/></SecondaryButton>
      <span v-else class="text-(--text-muted)">–</span>
    </Td>
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

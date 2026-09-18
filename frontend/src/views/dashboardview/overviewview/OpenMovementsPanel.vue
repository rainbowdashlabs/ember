/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import ItemChip from '@/components/inventory/ItemChip.vue'
import {glyphFor} from '@/util/glyph'
import {MovementState, type Movement} from '@/api/movements'
import {movements} from '@/api'
import {useSession} from '@/composables/useSession'

/**
 * What is on the move, on the dashboard: the piece, the step it stands on, and whose turn it is.
 *
 * <p>A guardian sees their charges' as well as their own, which is why a row that is not the reader's
 * says whose it is.
 */
const {t} = useI18n()
const router = useRouter()
const {isGuardian, sessionInfo} = useSession()

const rows = ref<Movement[]>([])

const open = computed(() => rows.value.filter(movement => movement.state === MovementState.OPEN))

function isOtherMember(memberId: number | null | undefined): boolean {
  return isGuardian() && memberId != null && memberId !== (sessionInfo.value?.member?.id ?? 0)
}

function chipOf(movement: Movement) {
  return {
    glyph: glyphFor({icon: movement.icon, color: movement.color}),
    name: movement.itemName ?? movement.incomingItemName ?? movement.inventoryName ?? '',
    sizeName: movement.itemSizeName,
    inventoryName: movement.itemName ? movement.inventoryName : null,
  }
}

async function loadData() {
  try {
    rows.value = await movements.listMovements()
  } catch { /* ignore */ }
}

onMounted(loadData)
</script>

<template>
  <NeutralContainer class="flex max-h-[66vh] flex-col">
    <SectionHeader class="mb-3 shrink-0">
      <font-awesome-icon :icon="['fas', 'rotate']" class="mr-2"/>
      {{ t('dashboard.exchanges') }}
    </SectionHeader>
    <div class="flex-1 space-y-2 overflow-y-auto">
      <EmptyState v-if="open.length === 0" compact>{{ t('dashboard.noExchanges') }}</EmptyState>
      <template v-else>
        <NeutralContainer
            v-for="movement in open"
            :key="movement.id"
            class="flex cursor-pointer items-center justify-between gap-2 px-3 py-2 hover:bg-(--bg-accent)"
            @click="router.push({name: 'inventory-movements'})"
        >
          <div class="min-w-0">
            <MemberName v-if="isOtherMember(movement.memberId)" :identity="movement.memberIdentity ?? null"
                        class="text-xs font-semibold text-primary"/>
            <ItemChip :source="chipOf(movement)"/>
            <p class="text-xs text-(--text-muted)">{{ movement.reason }}</p>
          </div>
          <div class="flex shrink-0 flex-col items-end gap-1">
            <InfoBadge v-if="movement.reachedStepLabel">{{ movement.reachedStepLabel }}</InfoBadge>
            <SecondaryBadge v-if="movement.currentStepActor">
              {{ t(`movements.actor.${movement.currentStepActor}`) }}
            </SecondaryBadge>
          </div>
        </NeutralContainer>
      </template>
    </div>
  </NeutralContainer>
</template>

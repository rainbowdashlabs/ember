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
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import type {ExchangeRequestEntry} from '@/api/types'
import {ExchangeStatus} from '@/api/types'
import {exchanges} from '@/api'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const router = useRouter()
const {isGuardian, sessionInfo} = useSession()

const exchangeList = ref<ExchangeRequestEntry[]>([])

const openExchanges = computed(() => exchangeList.value.filter(e => e.status !== ExchangeStatus.EXCHANGED))

function isOtherMember(memberId: number): boolean {
  return isGuardian() && memberId !== (sessionInfo.value?.member?.id ?? 0)
}

function statusBadgeComponent(status: string) {
  switch (status) {
    case ExchangeStatus.EXCHANGED: return SuccessBadge
    case ExchangeStatus.ANNOUNCED: case ExchangeStatus.SHIPPED: return InfoBadge
    default: return SecondaryBadge
  }
}

async function loadData() {
  try {
    exchangeList.value = await exchanges.listExchanges()
  } catch { /* ignore */ }
}

onMounted(loadData)
</script>

<template>
  <NeutralContainer class="flex flex-col max-h-[66vh]">
    <SectionHeader class="mb-3 shrink-0">
      <font-awesome-icon :icon="['fas', 'rotate']" class="mr-2"/>
      {{ t('dashboard.exchanges') }}
    </SectionHeader>
    <div class="overflow-y-auto flex-1 space-y-2">
      <EmptyState compact v-if="openExchanges.length === 0">{{ t('dashboard.noExchanges') }}</EmptyState>
      <template v-else>
        <NeutralContainer v-for="ex in openExchanges" :key="ex.id"
                          class="flex items-center justify-between gap-3 py-2 px-3 cursor-pointer hover:bg-(--bg-accent)"
                          @click="router.push({ name: 'inventory-exchanges' })">
          <div>
            <MemberName v-if="isOtherMember(ex.memberId)" :name="ex.memberName"
                        class="text-xs font-semibold text-primary"/>
            <p class="text-sm font-medium">{{ ex.inventoryName }}</p>
            <p class="text-xs text-(--text-muted)">
              {{ ex.oldSizeLabel ?? t('common.unisize') }} → {{ ex.newSizeLabel ?? t('common.unisize') }}
            </p>
            <p class="text-xs text-(--text-muted)">{{ ex.reason }}</p>
          </div>
          <component :is="statusBadgeComponent(ex.status)">
            {{ t(`dashboard.exchangeStatus.${ex.status}`) }}
          </component>
        </NeutralContainer>
      </template>
    </div>
  </NeutralContainer>
</template>

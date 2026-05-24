/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import type {MemberCheckSummary} from '@/api/types'
import {Roles, hasTeamRole} from '@/api/types'
import {inventoryCheck} from '@/api'
import {useSession} from '@/composables/useSession'
import MemberName from '@/components/avatar/MemberName.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'

const {t} = useI18n()
const router = useRouter()
const {sessionInfo} = useSession()

const members = ref<MemberCheckSummary[]>([])
const loading = ref(true)
const error = ref('')
const activeTab = ref<'team' | 'member'>('team')
const sortBy = ref<'name' | 'lastChecked'>('name')

const currentMemberId = () => sessionInfo.value?.member?.id

const filteredMembers = computed(() => {
  return members.value.filter(m => {
    const roles = m.roles ?? []
    if (roles.includes(Roles.GUARDIAN)) return false
    if (activeTab.value === 'team') {
      return hasTeamRole(roles)
    } else {
      return roles.includes(Roles.MEMBER) && !hasTeamRole(roles)
    }
  }).sort((a, b) => {
    if (sortBy.value === 'lastChecked') {
      if (!a.lastCheckedAt && b.lastCheckedAt) return -1
      if (a.lastCheckedAt && !b.lastCheckedAt) return 1
      if (a.lastCheckedAt && b.lastCheckedAt) {
        return new Date(a.lastCheckedAt).getTime() - new Date(b.lastCheckedAt).getTime()
      }
    }
    return memberName(a).localeCompare(memberName(b), 'de')
  })
})

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    members.value = await inventoryCheck.getCheckOverview()
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

function formatDate(dateStr?: string | null): string {
  if (!dateStr) return t('inventory.check.neverChecked')
  return new Date(dateStr).toLocaleString('de-DE')
}

function checkerName(member: MemberCheckSummary): string {
  if (!member.checkerFirstName) return '-'
  return `${member.checkerFirstName} ${member.checkerLastName ?? ''}`.trim()
}

function lockerName(member: MemberCheckSummary): string {
  if (!member.lockerFirstName) return ''
  return `${member.lockerFirstName} ${member.lockerLastName ?? ''}`.trim()
}

function memberName(member: MemberCheckSummary): string {
  return `${member.firstName ?? ''} ${member.lastName ?? ''}`.trim()
}

function isLockedByMe(member: MemberCheckSummary): boolean {
  return member.locked && member.lockedBy === currentMemberId()
}

function isLockedByOther(member: MemberCheckSummary): boolean {
  return member.locked && member.lockedBy !== currentMemberId()
}

function startCheck(memberId: number) {
  router.push({name: 'inventory-check-member', params: {memberId}, query: {teamOnly: activeTab.value === 'team' ? 'true' : 'false'}})
}

function viewLastCheck(member: MemberCheckSummary) {
  router.push({name: 'inventory-check-result', params: {memberId: member.memberId}, query: {name: memberName(member)}})
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <SectionHeader>{{ t('inventory.check.title') }}</SectionHeader>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && !error">
        <!-- Tabs and sort -->
        <div class="flex items-center justify-between gap-4">
          <div class="flex gap-2">
            <SelectionToggleButton
                :selected="activeTab === 'team'"
                size="md"
                @toggle="activeTab = 'team'"
            >
              {{ t('inventory.check.tabTeam') }}
            </SelectionToggleButton>
            <SelectionToggleButton
                :selected="activeTab === 'member'"
                size="md"
                @toggle="activeTab = 'member'"
            >
              {{ t('inventory.check.tabMember') }}
            </SelectionToggleButton>
          </div>
          <IconButton :icon="['fas', 'sort']" :label="sortBy === 'name' ? t('inventory.check.sortByLastChecked') : t('inventory.check.sortByName')" class="text-xs text-(--text-muted) hover:text-primary" @click="sortBy = sortBy === 'name' ? 'lastChecked' : 'name'"/>
        </div>

        <EmptyState v-if="filteredMembers.length === 0">{{ t('inventory.check.noMembers') }}</EmptyState>

        <!-- Desktop table -->
        <NeutralContainer v-else class="hidden sm:block overflow-x-auto">
          <table class="w-full text-sm">
            <thead>
            <THead>
              <Th>{{ t('inventory.check.member') }}</Th>
              <Th>{{ t('inventory.check.lastChecked') }}</Th>
              <Th>{{ t('inventory.check.checkedBy') }}</Th>
              <Th>{{ t('inventory.check.status') }}</Th>
              <th class="px-3 py-2"></th>
            </THead>
            </thead>
            <tbody>
            <TRow
                v-for="member in filteredMembers"
                :key="member.memberId"
            >
              <Td class="font-medium"><MemberName :name="memberName(member)" :member-id="member.memberId"/></Td>
              <Td muted>{{ formatDate(member.lastCheckedAt) }}</Td>
              <Td muted>{{ checkerName(member) }}</Td>
              <Td>
                <ErrorBadge v-if="isLockedByOther(member)">
                  {{ t('inventory.check.locked') }}: {{ lockerName(member) }}
                </ErrorBadge>
                <InfoBadge v-else-if="isLockedByMe(member)">{{ t('inventory.check.lockedByMe') }}</InfoBadge>
                <SecondaryBadge v-else-if="!member.lastCheckedAt">{{
                    t('inventory.check.neverChecked')
                  }}
                </SecondaryBadge>
              </Td>
              <Td align="right">
                <div class="flex items-center justify-end gap-1">
                  <IconButton
                      v-if="member.lastCheckedAt"
                      :icon="['fas', 'eye']"
                      :label="t('inventory.check.showLastCheck')"
                      class="text-(--text-muted) hover:text-primary"
                      @click="viewLastCheck(member)"
                  />
                  <SecondaryButton v-if="isLockedByMe(member)" @click="startCheck(member.memberId)">
                    {{ t('inventory.check.continue') }}
                  </SecondaryButton>
                  <PrimaryButton v-else :disabled="isLockedByOther(member)"
                                 @click="startCheck(member.memberId)">
                    {{ t('inventory.check.start') }}
                  </PrimaryButton>
                </div>
              </Td>
            </TRow>
            </tbody>
          </table>
        </NeutralContainer>

        <!-- Mobile card list -->
        <div class="sm:hidden space-y-2">
          <NeutralContainer
              v-for="member in filteredMembers"
              :key="member.memberId"
              class="space-y-2"
          >
            <div class="flex items-center justify-between gap-2">
              <div class="font-medium truncate"><MemberName :name="memberName(member)" :member-id="member.memberId"/></div>
              <div>
                <ErrorBadge v-if="isLockedByOther(member)">
                  {{ t('inventory.check.locked') }}: {{ lockerName(member) }}
                </ErrorBadge>
                <InfoBadge v-else-if="isLockedByMe(member)">{{ t('inventory.check.lockedByMe') }}</InfoBadge>
                <SecondaryBadge v-else-if="!member.lastCheckedAt">{{
                    t('inventory.check.neverChecked')
                  }}
                </SecondaryBadge>
              </div>
            </div>
            <div class="text-xs text-(--text-muted)">
              {{ t('inventory.check.lastChecked') }}: {{ formatDate(member.lastCheckedAt) }}
              <template v-if="member.checkerFirstName">
                &middot; {{ checkerName(member) }}
              </template>
            </div>
            <div class="flex gap-2">
              <SecondaryButton v-if="member.lastCheckedAt" class="text-sm flex-1" @click="viewLastCheck(member)">
                <font-awesome-icon :icon="['fas', 'eye']" class="mr-1"/>
                {{ t('inventory.check.showLastCheck') }}
              </SecondaryButton>
              <SecondaryButton v-if="isLockedByMe(member)" class="text-sm flex-1" @click="startCheck(member.memberId)">
                {{ t('inventory.check.continue') }}
              </SecondaryButton>
              <PrimaryButton v-else :disabled="isLockedByOther(member)" class="text-sm flex-1"
                             @click="startCheck(member.memberId)">
                {{ t('inventory.check.start') }}
              </PrimaryButton>
            </div>
          </NeutralContainer>
        </div>
      </template>
    </div>
  </ViewContent>
</template>

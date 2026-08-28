/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type {PickableMember} from '@/views/stationview/members/MemberPicker.vue'
import IntakePickStep from './intakeview/IntakePickStep.vue'
import {IntakeAudience, type IntakeAudienceName} from './intakeview/intakeAudience'
import IntakeTableCard from './intakeview/IntakeTableCard.vue'
import {lineFor, namesAPiece, rowsOf, type IntakeLine} from './intakeview/intakeLines'
import {inventory, inventoryFields, memberGroups as memberGroupsApi, stationMembers} from '@/api'
import {InventoryTypes, ItemOwner, type InventoryDetail, type ItemOwnerName} from '@/api/inventory'
import type {InventoryFieldDefinition} from '@/api/inventoryFields'
import type {MemberGroup, StationMember} from '@/api/types'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import {memberDisplayName} from '@/views/stationview/members/listview/useMemberData'

/**
 * Writing down an inventory the station already owns.
 *
 * <p>The way round the other screens do not offer: not from the piece to the member, but from the
 * member list to the pieces. Who is asked first, the table is filled in second, and one press writes
 * every line and hands each piece to the member on it.
 */
const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const routes = useInventoryRoutes()

const inventoryId = computed(() => Number(route.params.id))

const detail = ref<InventoryDetail | null>(null)
const fields = ref<InventoryFieldDefinition[]>([])
const members = ref<StationMember[]>([])
const groups = ref<MemberGroup[]>([])

const audience = ref<IntakeAudienceName>(IntakeAudience.ALL)
const userType = ref('MEMBER')
const groupId = ref('')
const lines = ref<IntakeLine[]>([])
const bulkSize = ref('')
const loaded = ref(false)

const {loading, error} = useAsyncLoader(async () => {
  const [inv, defs, people, memberGroups] = await Promise.all([
    inventory.getInventory(inventoryId.value),
    inventoryFields.listFields(inventoryId.value).catch(() => []),
    stationMembers.listMembers(),
    memberGroupsApi.listGroups().catch(() => []),
  ])
  detail.value = inv
  fields.value = defs
  members.value = people
  groups.value = memberGroups
})

const sizes = computed(() => detail.value?.sizes ?? [])

/** Everybody who is not on the list yet, for adding somebody the chosen audience left out. */
const pickable = computed<PickableMember[]>(() => {
  const taken = new Set(lines.value.map(line => line.memberId))
  return members.value
      .filter(member => !taken.has(member.id))
      .map(member => ({
        id: member.id,
        name: memberDisplayName(member),
        email: member.email,
        identity: member.identity,
        userType: member.userType,
      }))
})

const offeredUserTypes = computed(() =>
    [...new Set(members.value.map(member => member.userType).filter(Boolean))] as string[])

/** Loads the chosen audience into the table, replacing whatever was there. */
async function openTable() {
  const chosen = await membersOfAudience()
  lines.value = chosen.map(member => lineFor(member.id, memberDisplayName(member)))
  loaded.value = true
}

async function membersOfAudience(): Promise<StationMember[]> {
  if (audience.value === IntakeAudience.USER_TYPE) {
    return members.value.filter(member => member.userType === userType.value)
  }
  if (audience.value === IntakeAudience.GROUP) {
    if (!groupId.value) return []
    return memberGroupsApi.getGroupMembers(Number(groupId.value)).catch(() => [])
  }
  return members.value
}

function addByHand(memberId: number) {
  const member = members.value.find(candidate => candidate.id === memberId)
  if (!member) return
  lines.value = [...lines.value, lineFor(member.id, memberDisplayName(member))]
}

/** Writes the chosen size into every line that has none, which is what a uniform issue looks like. */
function applyToEmpty() {
  if (!bulkSize.value) return
  lines.value = lines.value.map(line => (line.sizeId ? line : {...line, sizeId: bulkSize.value}))
}

const filled = computed(() => lines.value.filter(namesAPiece).length)

/**
 * Who the pieces belong to.
 *
 * <p>An inventory that holds only one kind answers this by itself; one that holds both is asked,
 * because a station taking stock of its own gear and of the association's does so in one table.
 */
const holdsBoth = computed(() => detail.value?.inventoryType === InventoryTypes.MIXED)
const ownerKind = ref<ItemOwnerName>(ItemOwner.STATION)

const {running: saving, error: saveError, run: save} = useAsyncAction(async () => {
  const written = await inventory.takeStock(
      inventoryId.value,
      rowsOf(lines.value, fields.value, holdsBoth.value ? ownerKind.value : undefined))
  router.push({name: routes.detail, params: {id: inventoryId.value}})
  return written
}, {formatError: (e) => (e as {response?: {data?: {message?: string}}})?.response?.data?.message ?? t('common.error')})
</script>

<template>
  <ViewContent :title="t('inventory.intake.title')" :subtitle="t('inventory.intake.subtitle')">
    <div class="space-y-4">
      <div class="flex items-center gap-2">
        <SecondaryButton :icon="['fas', 'arrow-left']"
                         @click="router.push({name: routes.detail, params: {id: inventoryId}})"/>
        <SectionHeader>{{ detail?.name ?? '' }}</SectionHeader>
      </div>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && detail">
        <NeutralContainer class="space-y-3">
          <IntakePickStep
              v-model:audience="audience"
              v-model:user-type="userType"
              v-model:group-id="groupId"
              :groups="groups"
          />
          <PrimaryButton data-testid="intake-load" @click="openTable">
            {{ t('inventory.intake.load') }}
          </PrimaryButton>
        </NeutralContainer>

        <IntakeTableCard
            v-if="loaded"
            v-model:lines="lines"
            v-model:bulk-size="bulkSize"
            v-model:owner-kind="ownerKind"
            :sizes="sizes"
            :fields="fields"
            :has-sizes="detail.hasSizes"
            :holds-both="holdsBoth"
            :pickable="pickable"
            :user-types="offeredUserTypes"
            :filled="filled"
            :saving="saving"
            :save-error="saveError"
            @apply-to-empty="applyToEmpty"
            @add="addByHand"
            @save="save"
        />
      </template>
    </div>
  </ViewContent>
</template>

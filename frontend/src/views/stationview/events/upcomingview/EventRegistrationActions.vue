/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import {RegistrationStatus, type EventRegistrationEntry} from '@/api/events'

const props = defineProps<{
  eligibleMembers: { id: number; name: string }[]
  registrations: EventRegistrationEntry[]
  requiresRegistration: boolean
  hasManagedMembers: boolean
  registering: boolean
}>()

const emit = defineEmits<{
  register: [memberId: number]
  decline: [memberId: number]
  withdraw: [registrationId: number]
}>()

const {t} = useI18n()
const selectedMemberId = ref('')

function getRegistration(memberId: number): EventRegistrationEntry | undefined {
  return props.registrations.find(r => r.memberId === memberId)
}

const membersWithoutRegistration = computed(() =>
    props.eligibleMembers.filter(m => !getRegistration(m.id)))

const selectedId = computed((): number | null => {
  const single = membersWithoutRegistration.value.length === 1 ? membersWithoutRegistration.value[0] : undefined
  if (single) return single.id
  return selectedMemberId.value ? Number(selectedMemberId.value) : null
})

function handleRegister() { if (selectedId.value != null) emit('register', selectedId.value) }
function handleDecline() { if (selectedId.value != null) emit('decline', selectedId.value) }
</script>

<template>
  <div data-onboarding="events.item.pending" class="flex items-center gap-2 flex-wrap">
    <template v-for="m in eligibleMembers" :key="`reg-${m.id}`">
      <template v-if="getRegistration(m.id)">
        <div class="flex items-center gap-1">
          <span v-if="hasManagedMembers" class="text-xs text-(--text-muted)">{{ m.name }}:</span>
          <template v-if="getRegistration(m.id)!.status !== RegistrationStatus.DECLINED">
            <SecondaryButton :title="t('eventsUpcoming.decline')" class="!p-0 !bg-transparent !border-0" @click="emit('decline', m.id)">
              <SuccessBadge v-if="getRegistration(m.id)!.status === RegistrationStatus.ACCEPTED">{{ t('eventsUpcoming.statusAccepted') }} <font-awesome-icon :icon="['fas', 'xmark']" class="ml-1 h-3 w-3"/></SuccessBadge>
              <InfoBadge v-else-if="getRegistration(m.id)!.status === RegistrationStatus.PENDING">{{ t('eventsUpcoming.statusPending') }} <font-awesome-icon :icon="['fas', 'xmark']" class="ml-1 h-3 w-3"/></InfoBadge>
              <ErrorBadge v-else-if="getRegistration(m.id)!.status === RegistrationStatus.DENIED">{{ t('eventsUpcoming.statusDenied') }} <font-awesome-icon :icon="['fas', 'xmark']" class="ml-1 h-3 w-3"/></ErrorBadge>
            </SecondaryButton>
          </template>
          <template v-else>
            <ErrorBadge>{{ t('eventsUpcoming.statusDeclined') }}</ErrorBadge>
            <PrimaryButton v-if="requiresRegistration" :disabled="registering" class="text-sm" @click="emit('register', m.id)">
              <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>{{ t('eventsUpcoming.register') }}
            </PrimaryButton>
            <SecondaryButton v-else :disabled="registering" class="text-sm" @click="emit('withdraw', getRegistration(m.id)!.id)">
              <font-awesome-icon :icon="['fas', 'trash']" class="mr-1"/>{{ t('eventsUpcoming.remove') }}
            </SecondaryButton>
          </template>
          <span v-if="getRegistration(m.id)?.createdByName" class="text-xs text-(--text-muted) italic">{{ t('common.createdBy', {name: getRegistration(m.id)!.createdByName}) }}</span>
        </div>
      </template>
    </template>

    <template v-if="membersWithoutRegistration.length > 0">
      <SelectInput v-if="membersWithoutRegistration.length > 1" v-model="selectedMemberId"
                   data-onboarding="events.item.member-select" class="text-sm w-40">
        <option disabled value="">{{ t('eventsUpcoming.selectMember') }}</option>
        <option v-for="m in membersWithoutRegistration" :key="m.id" :value="String(m.id)">{{ m.name }}</option>
      </SelectInput>

      <PrimaryButton v-if="requiresRegistration" :disabled="registering || selectedId == null" class="text-sm" @click="handleRegister">
        <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
        {{ selectedId != null && membersWithoutRegistration.length > 1 ? t('eventsUpcoming.registerFor', {name: membersWithoutRegistration.find(m => m.id === selectedId)?.name ?? ''}) : t('eventsUpcoming.register') }}
      </PrimaryButton>

      <ErrorButton :disabled="selectedId == null" class="text-sm" @click="handleDecline">
        <font-awesome-icon :icon="['fas', 'ban']" class="mr-1"/>
        {{ selectedId != null && membersWithoutRegistration.length > 1 ? t('eventsUpcoming.declineFor', {name: membersWithoutRegistration.find(m => m.id === selectedId)?.name ?? ''}) : t('eventsUpcoming.decline') }}
      </ErrorButton>
    </template>
  </div>
</template>

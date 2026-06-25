/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import type {FederatedRegistration} from '@/api/events'

interface EligibleMember {
  uid: string
  name: string
}

const selectedMemberUid = defineModel<string>('selectedMemberUid', {required: true})

const props = defineProps<{
  eligibleMembers: EligibleMember[]
  registrations: FederatedRegistration[]
  eventId: number
  registering: boolean
}>()

const emit = defineEmits<{
  register: []
  withdraw: [uid: string]
}>()

const {t} = useI18n()

function getRegistration(uid: string): FederatedRegistration | undefined {
  return props.registrations.find(r => r.eventId === props.eventId && r.remoteMemberId === uid)
}

const membersWithoutReg = computed(() =>
    props.eligibleMembers.filter(m => !getRegistration(m.uid)),
)

const canSubmit = computed(() => {
  if (membersWithoutReg.value.length === 1) return true
  return !!selectedMemberUid.value
})
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader>{{ t('eventsUpcoming.registration') }}</SubHeader>
    <div class="flex items-center gap-2 flex-wrap">
      <template v-for="m in props.eligibleMembers" :key="`reg-${m.uid}`">
        <div v-if="getRegistration(m.uid)" class="flex items-center gap-1">
          <span v-if="props.eligibleMembers.length > 1" class="text-xs text-(--text-muted)">{{ m.name }}:</span>
          <SuccessBadge v-if="getRegistration(m.uid)!.status === 'ACCEPTED'">{{ t('eventsUpcoming.statusAccepted') }}</SuccessBadge>
          <InfoBadge v-else-if="getRegistration(m.uid)!.status === 'PENDING'">{{ t('eventsUpcoming.statusPending') }}</InfoBadge>
          <ErrorBadge v-else-if="getRegistration(m.uid)!.status === 'DENIED'">{{ t('eventsUpcoming.statusDenied') }}</ErrorBadge>
          <ErrorButton :disabled="props.registering" compact class="text-xs" @click="emit('withdraw', m.uid)">
            <font-awesome-icon :icon="['fas', 'xmark']"/>
          </ErrorButton>
        </div>
      </template>

      <template v-if="membersWithoutReg.length > 0">
        <SelectInput
            v-if="membersWithoutReg.length > 1"
            v-model="selectedMemberUid"
            class="text-sm w-40"
        >
          <option disabled value="">{{ t('eventsUpcoming.selectMember') }}</option>
          <option v-for="m in membersWithoutReg" :key="m.uid" :value="m.uid">{{ m.name }}</option>
        </SelectInput>
        <PrimaryButton :disabled="props.registering || !canSubmit" @click="emit('register')">
          <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
          {{ t('eventsUpcoming.register') }}
        </PrimaryButton>
      </template>
    </div>
  </NeutralContainer>
</template>

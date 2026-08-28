/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import type {FederatedRegistration} from '@/api/events'

const selectedMemberUid = defineModel<string>('selectedMemberUid', {required: true})

defineProps<{
  eventKey: string
  eligibleMembers: { uid: string; name: string }[]
  membersWithoutRegistration: { uid: string; name: string }[]
  getRegistration: (memberUid: string) => FederatedRegistration | undefined
  selectedUid: string | null
  registering: boolean
}>()

const emit = defineEmits<{
  register: []
  withdraw: [memberUid: string]
}>()

const {t} = useI18n()

function onSelect(event: Event) {
  selectedMemberUid.value = (event.target as HTMLSelectElement).value
}
</script>

<template>
  <div class="flex items-center gap-2 flex-wrap pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent">
    <template v-for="m in eligibleMembers" :key="`reg-${eventKey}-${m.uid}`">
      <div v-if="getRegistration(m.uid)" class="flex items-center gap-1" data-testid="federated-event-registration">
        <span v-if="eligibleMembers.length > 1" class="text-xs text-(--text-muted)">{{ m.name }}:</span>
        <SuccessBadge v-if="getRegistration(m.uid)!.status === 'ACCEPTED'">{{ t('eventsUpcoming.statusAccepted') }}</SuccessBadge>
        <InfoBadge v-else-if="getRegistration(m.uid)!.status === 'PENDING'">{{ t('eventsUpcoming.statusPending') }}</InfoBadge>
        <ErrorBadge v-else-if="getRegistration(m.uid)!.status === 'DENIED'">{{ t('eventsUpcoming.statusDenied') }}</ErrorBadge>
        <ErrorButton compact class="text-xs" @click.stop="emit('withdraw', m.uid)">
          <font-awesome-icon :icon="['fas', 'xmark']"/>
        </ErrorButton>
      </div>
    </template>

    <template v-if="membersWithoutRegistration.length > 0">
      <SelectInput
          v-if="membersWithoutRegistration.length > 1"
          :model-value="selectedUid ?? ''"
          class="text-sm w-40"
          @click.stop
          @change="onSelect"
      >
        <option disabled value="">{{ t('eventsUpcoming.selectMember') }}</option>
        <option v-for="m in membersWithoutRegistration" :key="m.uid" :value="m.uid">{{ m.name }}</option>
      </SelectInput>

      <PrimaryButton
          :disabled="registering || !selectedUid"
          compact
          class="text-sm"
          data-testid="federated-event-register"
          @click.stop="emit('register')"
      >
        <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
        {{ t('eventsUpcoming.register') }}
      </PrimaryButton>
    </template>
  </div>
</template>

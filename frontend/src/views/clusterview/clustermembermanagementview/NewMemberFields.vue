/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import FormLabel from '@/components/input/FormLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import type {ManagedStation} from '@/api/clusterMembers'
import {StationUserTypeLabels, type StationUserTypeName} from '@/api/types'

/** What is asked about somebody being taken on, in the order it is asked: the station first. */
const stationUid = defineModel<string>('stationUid', {required: true})
const firstName = defineModel<string>('firstName', {required: true})
const lastName = defineModel<string>('lastName', {required: true})
const email = defineModel<string>('email', {required: true})
const canLogin = defineModel<boolean>('canLogin', {required: true})
const userType = defineModel<StationUserTypeName>('userType', {required: true})

const props = defineProps<{
  stations: ManagedStation[]
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-1">
    <FormLabel>{{ t('clusterMemberManagement.create.station') }}</FormLabel>
    <SelectInput v-model="stationUid" class="w-full" data-testid="cluster-member-create-station">
      <option value="" disabled>{{ t('clusterMemberManagement.create.stationPlaceholder') }}</option>
      <option v-for="station in props.stations" :key="station.uid" :value="station.uid">{{ station.name }}</option>
    </SelectInput>
  </div>

  <div class="grid gap-3 sm:grid-cols-2">
    <div class="space-y-1">
      <FormLabel>{{ t('clusterMemberManagement.create.firstName') }}</FormLabel>
      <TextInput v-model="firstName" data-testid="cluster-member-create-first"/>
    </div>
    <div class="space-y-1">
      <FormLabel>{{ t('clusterMemberManagement.create.lastName') }}</FormLabel>
      <TextInput v-model="lastName" data-testid="cluster-member-create-last"/>
    </div>
  </div>

  <div class="space-y-1">
    <FormLabel>{{ t('clusterMemberManagement.create.userType') }}</FormLabel>
    <SelectInput v-model="userType" class="w-full">
      <option v-for="(label, key) in StationUserTypeLabels" :key="key" :value="key">{{ label }}</option>
    </SelectInput>
  </div>

  <div class="flex items-center gap-2">
    <CheckboxInput v-model="canLogin" data-testid="cluster-member-create-login"/>
    <span class="text-sm">{{ t('clusterMemberManagement.create.canLogin') }}</span>
  </div>

  <div v-if="canLogin" class="space-y-1">
    <FormLabel>{{ t('clusterMemberManagement.create.email') }}</FormLabel>
    <TextInput v-model="email" data-testid="cluster-member-create-email"/>
  </div>
  <MutedText v-else size="sm">{{ t('clusterMemberManagement.create.noLoginHint') }}</MutedText>
</template>

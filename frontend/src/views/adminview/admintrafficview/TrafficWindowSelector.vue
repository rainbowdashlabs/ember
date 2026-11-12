/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import {AuthBucket} from '@/api/generated/schema'
import WindowField from './WindowField.vue'

/** Allowed metrics, mirrors {@code TrafficChart}'s prop type. */
type Metric = 'ingressBytes' | 'egressBytes' | 'requests' | 'inout'

const windowHours = defineModel<number>('windowHours', {required: true})
const metric = defineModel<Metric>('metric', {required: true})
const authFilter = defineModel<AuthBucket | ''>('authFilter', {required: true})

const {t} = useI18n()

const windowOptions = computed(() => [
  {value: '24', label: t('traffic.window.last24h')},
  {value: '72', label: t('traffic.window.last3d')},
  {value: '168', label: t('traffic.window.last7d')},
  {value: '720', label: t('traffic.window.last30d')},
])

const windowHoursModel = computed({
  get: () => String(windowHours.value),
  set: v => windowHours.value = Number(v),
})
const metricModel = computed({
  get: () => metric.value,
  set: v => metric.value = v as Metric,
})
const authModel = computed({
  get: () => authFilter.value,
  set: v => authFilter.value = v as AuthBucket | '',
})
</script>

<template>
  <div class="flex flex-wrap gap-3 items-end">
    <WindowField v-model="windowHoursModel" :options="windowOptions"/>
    <label class="flex flex-col gap-1 text-sm">
      <span class="text-(--text-muted)">{{ t('traffic.controls.metric') }}</span>
      <SelectInput v-model="metricModel">
        <option value="egressBytes">{{ t('traffic.metric.egress') }}</option>
        <option value="ingressBytes">{{ t('traffic.metric.ingress') }}</option>
        <option value="inout">{{ t('traffic.metric.inout') }}</option>
        <option value="requests">{{ t('traffic.metric.requests') }}</option>
      </SelectInput>
    </label>
    <label class="flex flex-col gap-1 text-sm">
      <span class="text-(--text-muted)">{{ t('traffic.controls.bucket') }}</span>
      <SelectInput v-model="authModel">
        <option value="">{{ t('traffic.bucket.ALL') }}</option>
        <option :value="AuthBucket.AUTHENTICATED">{{ t('traffic.bucket.AUTHENTICATED') }}</option>
        <option :value="AuthBucket.UNAUTHENTICATED">{{ t('traffic.bucket.UNAUTHENTICATED') }}</option>
        <option :value="AuthBucket.FEDERATION">{{ t('traffic.bucket.FEDERATION') }}</option>
      </SelectInput>
    </label>
  </div>
</template>

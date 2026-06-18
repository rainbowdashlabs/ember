/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import {AuthBucket, type AuthBucketName} from '@/api/traffic'

const props = defineProps<{
  windowHours: number
  metric: 'ingressBytes' | 'egressBytes' | 'requests'
  authFilter: AuthBucketName | ''
}>()

const emit = defineEmits<{
  (e: 'update:windowHours', value: number): void
  (e: 'update:metric', value: 'ingressBytes' | 'egressBytes' | 'requests'): void
  (e: 'update:authFilter', value: AuthBucketName | ''): void
}>()

const {t} = useI18n()

const windowHoursModel = computed({
  get: () => String(props.windowHours),
  set: v => emit('update:windowHours', Number(v)),
})
const metricModel = computed({
  get: () => props.metric,
  set: v => emit('update:metric', v as 'ingressBytes' | 'egressBytes' | 'requests'),
})
const authModel = computed({
  get: () => props.authFilter,
  set: v => emit('update:authFilter', v as AuthBucketName | ''),
})
</script>

<template>
  <div class="flex flex-wrap gap-3 items-end">
    <label class="flex flex-col gap-1 text-sm">
      <span class="text-(--text-muted)">{{ t('traffic.controls.window') }}</span>
      <SelectInput v-model="windowHoursModel">
        <option value="24">{{ t('traffic.window.last24h') }}</option>
        <option value="72">{{ t('traffic.window.last3d') }}</option>
        <option value="168">{{ t('traffic.window.last7d') }}</option>
        <option value="720">{{ t('traffic.window.last30d') }}</option>
      </SelectInput>
    </label>
    <label class="flex flex-col gap-1 text-sm">
      <span class="text-(--text-muted)">{{ t('traffic.controls.metric') }}</span>
      <SelectInput v-model="metricModel">
        <option value="egressBytes">{{ t('traffic.metric.egress') }}</option>
        <option value="ingressBytes">{{ t('traffic.metric.ingress') }}</option>
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

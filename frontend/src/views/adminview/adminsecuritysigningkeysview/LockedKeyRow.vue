/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import {SigningKeyKind} from '@/api/generated/schema'
import type {LockedSigningKey} from '@/api/generated/schema'
import {fingerprintRows} from '@/util/fingerprint'
import {formatDate} from '@/util/format'

/**
 * One signing key that no longer opens: whose it is, whether it is still the one in use, and the serial
 * number and fingerprint an administrator compares against the station's public page.
 */
const props = defineProps<{
  signingKey: LockedSigningKey
}>()

const {t} = useI18n()

const owner = computed(() => {
  if (props.signingKey.kind === SigningKeyKind.AUTHORITY) return t('adminSecurity.signingKeys.kindAuthority')
  return t('adminSecurity.signingKeys.kindStationKey', {
    station: props.signingKey.stationName ?? t('adminSecurity.signingKeys.unnamedStation'),
  })
})
</script>

<template>
  <li class="space-y-1">
    <div class="flex flex-wrap items-center gap-2">
      <span class="font-medium">{{ owner }}</span>
      <InfoBadge v-if="signingKey.active">{{ t('adminSecurity.signingKeys.active') }}</InfoBadge>
      <SecondaryBadge v-else>{{ t('adminSecurity.signingKeys.retired') }}</SecondaryBadge>
    </div>
    <dl class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
      <dt class="text-(--text-muted)">{{ t('adminSecurity.signingKeys.serial') }}</dt>
      <dd class="font-mono text-xs break-all">{{ signingKey.serialNumber }}</dd>
      <dt class="text-(--text-muted)">{{ t('adminSecurity.signingKeys.fingerprint') }}</dt>
      <dd class="font-mono text-xs">
        <span v-for="row in fingerprintRows(signingKey.sha256Fingerprint)" :key="row" class="block break-all">{{ row }}</span>
      </dd>
      <dt class="text-(--text-muted)">{{ t('adminSecurity.signingKeys.validUntil') }}</dt>
      <dd>{{ formatDate(signingKey.validUntil) }}</dd>
    </dl>
  </li>
</template>

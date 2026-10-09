/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import CertificateFactsList from './CertificateFactsList.vue'
import IndicationText from './IndicationText.vue'
import type {TimestampCheck} from '@/api/generated/schema'
import {formatDateTime} from '@/util/format'

/**
 * Everything the check says about one timestamp: when, by which service, whether that service is
 * one this installation trusts by its pinned root, and the verdict on the stamp itself.
 */
withDefaults(defineProps<{
  stamp: TimestampCheck
  /** Set for a document timestamp, which covers a span of the file of its own. */
  coversWholeFile?: boolean | null
}>(), {
  coversWholeFile: null,
})

const {t} = useI18n()

function yesNo(value: boolean): string {
  return value ? t('common.yes') : t('common.no')
}
</script>

<template>
  <div class="space-y-2">
    <dl class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
      <dt class="text-(--text-muted)">{{ t('sealVerification.details.timestampTime') }}</dt>
      <dd>{{ stamp.time ? formatDateTime(stamp.time) : t('common.unknown') }}</dd>
      <dt class="text-(--text-muted)">{{ t('sealVerification.details.pinned') }}</dt>
      <dd>{{ yesNo(stamp.pinnedAuthority) }}</dd>
      <dt class="text-(--text-muted)">{{ t('sealVerification.details.intact') }}</dt>
      <dd>{{ yesNo(stamp.intact) }}</dd>
      <template v-if="coversWholeFile !== null">
        <dt class="text-(--text-muted)">{{ t('sealVerification.details.coversWholeFile') }}</dt>
        <dd>{{ yesNo(coversWholeFile) }}</dd>
      </template>
      <dt class="text-(--text-muted)">{{ t('sealVerification.details.indication') }}</dt>
      <dd><IndicationText :indication="stamp.indication" :sub-indication="stamp.subIndication"/></dd>
    </dl>
    <CertificateFactsList :label="t('sealVerification.details.timestampService')" :certificate="stamp.authority"/>
  </div>
</template>

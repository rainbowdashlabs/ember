/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {RevocationStatus, type SealCheck} from '@/api/generated/schema'
import {formatDateTime} from '@/util/format'
import {certificateName, provingTimestamp, revokedAfterTimestamp} from './sealVerdict'

/**
 * Who sealed, when, which timestamp backs it and whether the key has since been revoked, in plain
 * words. The revocation is only said for a seal of this installation, whose lists it holds, and for a
 * seal of a federation partner, whose lists it takes in with the partner's authorities.
 */
const props = defineProps<{
  check: SealCheck
}>()

const {t} = useI18n()

const sealer = computed(() => certificateName(props.check.signer) ?? t('common.unknown'))

const stamp = computed(() => provingTimestamp(props.check))

const stampText = computed(() => {
  const proving = stamp.value
  if (!proving?.time) return t('sealVerification.facts.noTimestamp')
  return t('sealVerification.facts.timestampBy', {
    time: formatDateTime(proving.time),
    service: certificateName(proving.authority) ?? t('common.unknown'),
  })
})

const revocationText = computed(() => {
  const revocation = props.check.revocation
  if (revocation.status === RevocationStatus.GOOD) return t('sealVerification.revocation.good')
  if (revocation.status !== RevocationStatus.REVOKED || !revocation.revokedAt) return t('sealVerification.revocation.unknown')
  const date = formatDateTime(revocation.revokedAt)
  const said = revokedAfterTimestamp(props.check)
    ? t('sealVerification.revocation.revokedAfter', {date})
    : t('sealVerification.revocation.revoked', {date})
  return revocation.reason ? `${said} ${t(`sealVerification.revocation.reason.${revocation.reason}`)}` : said
})
</script>

<template>
  <dl class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
    <dt class="text-(--text-muted)">{{ t('sealVerification.facts.sealer') }}</dt>
    <dd data-testid="seal-sealer">{{ sealer }}</dd>
    <dt class="text-(--text-muted)">{{ t('sealVerification.facts.sealedAt') }}</dt>
    <dd>{{ check.signingTime ? t('sealVerification.facts.sealedAtClock', {time: formatDateTime(check.signingTime)}) : t('common.unknown') }}</dd>
    <dt class="text-(--text-muted)">{{ t('sealVerification.facts.timestamp') }}</dt>
    <dd>{{ stampText }}</dd>
    <template v-if="check.issuedHere || check.partner">
      <dt class="text-(--text-muted)">{{ t('sealVerification.facts.key') }}</dt>
      <dd data-testid="seal-revocation">{{ revocationText }}</dd>
    </template>
  </dl>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import {SetupLink, setupLinkState} from './setupLinkState'
import {formatDate} from '@/util/format'
import type {StationMember} from '@/api/types'

const {t} = useI18n()

const props = defineProps<{
  member: StationMember
  canEdit?: boolean
}>()

const emit = defineEmits<{
  resendSetup: [event: Event]
}>()

const state = computed(() => setupLinkState(props.member))
const pending = computed(() => state.value !== SetupLink.DONE)
const expired = computed(() => state.value === SetupLink.EXPIRED)

/**
 * Whether sending the setup mail again could reach anybody at all.
 *
 * <p>A member entered without an address of their own has none the list can offer, and where a
 * guardian has a real address the mail goes to them instead, which is why the offer stands beside a
 * line that shows no address.
 */
const canBeWrittenTo = computed(() => props.member.mailReaches !== 'NOBODY')
const canResend = computed(() => pending.value && props.canEdit && canBeWrittenTo.value)

const pendingTitle = computed(() => {
  const base = t('membersList.accountPending')
  if (!props.member.setupMailExpiresAt) return base
  const date = formatDate(props.member.setupMailExpiresAt)
  const when = expired.value ? 'membersList.accountPendingExpired' : 'membersList.accountPendingExpires'
  return `${base} ${t(when, {date})}`
})

/** What pressing the paper plane will do, said in full, because who receives it is not obvious. */
const resendTitle = computed(() => {
  const who = props.member.mailReaches === 'GUARDIANS'
      ? t('membersList.setupMailToGuardians')
      : t('membersList.setupMailToMember')
  return `${pendingTitle.value} ${who}`
})

/** Why there is no button here, which the hourglass alone never said. */
const waitingTitle = computed(() => `${pendingTitle.value} ${t('membersList.setupMailToNobody')}`)
</script>

<template>
  <div v-if="pending" class="ml-auto flex items-center gap-1.5">
    <ErrorBadge v-if="expired" data-testid="setup-link-expired" class="text-[10px]">
      <font-awesome-icon :icon="['fas', 'link-slash']" class="mr-1 h-2.5 w-2.5"/>
      {{ t('membersList.setupLinkExpired') }}
    </ErrorBadge>
    <IconButton
        v-if="canResend"
        :icon="['fas', 'paper-plane']"
        :title="resendTitle"
        :label="t('membersList.accountPendingResend')"
        :class="expired ? 'text-error hover:bg-error/15' : 'text-warning hover:bg-warning/15'"
        :data-testid="expired ? 'resend-setup-expired' : 'resend-setup'"
        @click.stop="emit('resendSetup', $event)"
    />
    <font-awesome-icon
        v-else
        :icon="['fas', 'hourglass-half']"
        :title="waitingTitle"
        class="w-3.5 h-3.5"
        :class="expired ? 'text-error' : 'text-warning'"
    />
  </div>
</template>

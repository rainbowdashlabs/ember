/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import type {MemberCheckSummary} from '@/api/types'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import {isLockedByMe, isLockedByOther, lockerName} from './memberHelpers'

const props = defineProps<{
  member: MemberCheckSummary
  currentMemberId: number | undefined
}>()

const {t} = useI18n()

const lockedByMe = computed(() => isLockedByMe(props.member, props.currentMemberId))
const lockedByOther = computed(() => isLockedByOther(props.member, props.currentMemberId))
const locker = computed(() => lockerName(props.member))
</script>

<template>
  <ErrorBadge v-if="lockedByOther">
    {{ t('inventory.check.locked') }}: {{ locker }}
  </ErrorBadge>
  <InfoBadge v-else-if="lockedByMe">{{ t('inventory.check.lockedByMe') }}</InfoBadge>
  <SecondaryBadge v-else-if="!member.lastCheckedAt">{{
      t('inventory.check.neverChecked')
    }}
  </SecondaryBadge>
</template>

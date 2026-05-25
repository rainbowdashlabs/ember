/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import type {AttendanceEntry} from '@/api/types'

const {t} = useI18n()

const props = defineProps<{
  entries: AttendanceEntry[]
}>()

const unconfirmed = computed(() => props.entries.filter(e => e.status === 'UNCONFIRMED').length)
const present = computed(() => props.entries.filter(e => e.status === 'PRESENT').length)
const absent = computed(() => props.entries.filter(e => e.status === 'ABSENT').length)
const declined = computed(() => props.entries.filter(e => e.status === 'DECLINED').length)
</script>

<template>
  <div class="flex gap-3 text-sm flex-wrap">
    <SecondaryBadge v-if="unconfirmed > 0">
      {{ unconfirmed }} {{ t('attendanceSession.unconfirmed') }}
    </SecondaryBadge>
    <SuccessBadge>{{ present }} {{ t('attendanceSession.present') }}</SuccessBadge>
    <ErrorBadge>{{ absent }} {{ t('attendanceSession.absent') }}</ErrorBadge>
    <InfoBadge>{{ declined }} {{ t('attendanceSession.declined') }}</InfoBadge>
  </div>
</template>

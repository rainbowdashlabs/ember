/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import IconButton from '@/components/button/IconButton.vue'
import {formatDateTime} from '@/util/format'
import type {BeaconReport} from '@/api/beacon'

/**
 * Somebody's own words, forwarded without their name.
 *
 * <p>The contact shown is the operator's of the instance it came from, which is who can be written to
 * about it. The page is shown as text and never as a link: it arrives from elsewhere.
 */
defineProps<{report: BeaconReport}>()

const emit = defineEmits<{acknowledge: [id: number]}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer :class="{'opacity-50': report.acknowledged}">
    <div class="flex items-start justify-between gap-3">
      <div class="min-w-0 space-y-1">
        <p class="whitespace-pre-wrap break-words">{{ report.message }}</p>
        <MutedText tag="p" size="sm">{{ formatDateTime(report.reportedAt) }}</MutedText>
        <MutedText v-if="report.version" tag="p" size="sm">{{ report.version }}</MutedText>
        <MutedText v-if="report.page" tag="p" size="sm">{{ report.page }}</MutedText>
        <MutedText v-if="report.contactMail || report.contactName" tag="p" size="sm">
          {{ t('beacon.contact') }}: {{ report.contactName }} {{ report.contactMail }}
        </MutedText>
      </div>
      <IconButton
          v-if="!report.acknowledged"
          :icon="['fas', 'check']"
          :label="t('adminProblems.acknowledge')"
          @click="emit('acknowledge', report.id)"
      />
    </div>
  </NeutralContainer>
</template>

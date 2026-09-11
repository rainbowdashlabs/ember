/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import type {Mailbox} from '@/api/mailImport'
import {formatDateTime} from '@/util/format'

/** What a mailbox is set to, how it is doing, and what can be done to it. */
const props = defineProps<{
  mailbox: Mailbox
  busy: boolean
}>()

const emit = defineEmits<{
  test: []
  run: []
  resume: []
  edit: []
  remove: []
}>()

const {t} = useI18n()

const state = computed(() => {
  if (props.mailbox.suspended) return 'suspended'
  if (!props.mailbox.enabled) return 'off'
  return 'on'
})
</script>

<template>
  <div class="flex flex-wrap items-start justify-between gap-3">
    <div class="min-w-0">
      <div class="flex flex-wrap items-center gap-2">
        <SectionHeader class="!mb-0">{{ mailbox.name }}</SectionHeader>
        <SuccessBadge v-if="state === 'on'">{{ t('mailImport.state.on') }}</SuccessBadge>
        <SecondaryBadge v-else-if="state === 'off'">{{ t('mailImport.state.off') }}</SecondaryBadge>
        <ErrorBadge v-else>{{ t('mailImport.state.suspended') }}</ErrorBadge>
        <InfoBadge v-if="mailbox.verifyDkim">{{ t('mailImport.dkimChecked') }}</InfoBadge>
      </div>
      <MutedText size="sm" tag="p" class="break-words">
        {{ mailbox.username }} · {{ mailbox.host }}:{{ mailbox.port }} · {{ mailbox.folder }}
      </MutedText>
      <MutedText size="sm" tag="p">
        {{ mailbox.lastCheckAt
            ? t('mailImport.lastChecked', {when: formatDateTime(mailbox.lastCheckAt)})
            : t('mailImport.neverChecked') }}
      </MutedText>
    </div>
    <div class="flex shrink-0 flex-wrap items-center gap-2">
      <SecondaryButton :disabled="busy" data-testid="mailbox-test" @click="emit('test')">
        {{ t('mailImport.testConnection') }}
      </SecondaryButton>
      <SecondaryButton :disabled="busy" data-testid="mailbox-run" @click="emit('run')">
        {{ t('mailImport.runNow') }}
      </SecondaryButton>
      <SecondaryButton v-if="mailbox.suspended" :disabled="busy" @click="emit('resume')">
        {{ t('mailImport.resume') }}
      </SecondaryButton>
      <EditButton :label="t('common.edit')" @click="emit('edit')"/>
      <DeleteButton :label="t('common.delete')" @click="emit('remove')"/>
    </div>
  </div>
</template>

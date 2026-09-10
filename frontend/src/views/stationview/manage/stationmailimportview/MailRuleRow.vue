/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type {MailRule} from '@/api/mailImport'
import type {StationMember} from '@/api/types'

/** One rule, as a line somebody can read without opening it. */
const props = defineProps<{
  rule: MailRule
  members: StationMember[]
}>()

const emit = defineEmits<{
  edit: []
  delete: []
  acknowledge: []
}>()

const {t} = useI18n()

const senders = computed(() => props.rule.senderPatterns.join(', '))

const namedMembers = computed(() => props.rule.memberIds
    .map(id => props.members.find(member => member.id === id)?.name ?? String(id))
    .join(', '))
</script>

<template>
  <div class="flex flex-wrap items-start justify-between gap-2 py-2">
    <div class="min-w-0 flex-1">
      <div class="flex flex-wrap items-center gap-2">
        <span class="text-sm font-medium">{{ rule.name }}</span>
        <SecondaryBadge v-if="!rule.enabled">{{ t('mailImport.state.off') }}</SecondaryBadge>
        <InfoBadge v-if="rule.readSubjectForMember">{{ t('mailImport.rule.readsSubject') }}</InfoBadge>
        <InfoBadge v-if="rule.hidden">{{ t('mailImport.rule.hidden') }}</InfoBadge>
      </div>
      <MutedText size="sm" tag="p" class="break-words">{{ t('mailImport.rule.from', {senders}) }}</MutedText>
      <MutedText size="sm" tag="p" class="break-words">
        {{ t('mailImport.rule.takes', {types: rule.acceptedTypes.join(', ')}) }}
        <template v-if="rule.attachmentNameFilter"> · {{ t('mailImport.rule.named', {name: rule.attachmentNameFilter}) }}</template>
        <template v-if="rule.subjectFilter"> · {{ t('mailImport.rule.subject', {subject: rule.subjectFilter}) }}</template>
      </MutedText>
      <MutedText v-if="namedMembers" size="sm" tag="p" class="break-words">
        {{ t('mailImport.rule.filesUnder', {members: namedMembers}) }}
      </MutedText>
      <MutedText size="sm" tag="p">{{ t(`mailImport.action.${rule.action}`) }}</MutedText>

      <div v-if="rule.lostMember" class="mt-1 flex flex-wrap items-center gap-2">
        <ErrorBadge>{{ t('mailImport.rule.lostMember') }}</ErrorBadge>
        <SecondaryButton size="sm" @click="emit('acknowledge')">{{ t('mailImport.rule.acknowledge') }}</SecondaryButton>
      </div>
    </div>
    <div class="flex shrink-0 items-center gap-1">
      <EditButton :label="t('common.edit')" @click="emit('edit')"/>
      <DeleteButton :label="t('common.delete')" @click="emit('delete')"/>
    </div>
  </div>
</template>

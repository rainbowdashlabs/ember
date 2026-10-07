/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import type {NameChangeView} from '@/api/generated/schema'
import {formatDateTime} from '@/util/format'

/**
 * One member's requested name: who asked, from which name to which, and the two decisions.
 */
defineProps<{
  request: NameChangeView
  processing: boolean
}>()

const emit = defineEmits<{
  approve: []
  deny: []
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="flex flex-wrap items-center justify-between gap-3" data-testid="name-request">
    <div class="flex min-w-0 flex-col gap-1">
      <MemberName :identity="request.member"/>
      <span class="text-sm">{{ t('memberChanges.nameRequestFromTo', {current: request.currentName, requested: request.requestedName}) }}</span>
      <MutedText>{{ formatDateTime(request.requestedAt) }}</MutedText>
    </div>
    <ButtonRow pair align="end">
      <SuccessButton :disabled="processing" data-testid="approve-name" @click="emit('approve')">{{ t('memberChanges.approveName') }}</SuccessButton>
      <ErrorButton :disabled="processing" data-testid="deny-name" @click="emit('deny')">{{ t('memberChanges.denyName') }}</ErrorButton>
    </ButtonRow>
  </NeutralContainer>
</template>

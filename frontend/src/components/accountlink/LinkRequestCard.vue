/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import {LinkOrigin, type LinkPrompt} from '@/api/generated/schema'
import {formatDate} from '@/util/format'

/**
 * One station's request to link the reader's account to one of its members: who asks, for which
 * member, how the station came to ask, and until when it waits. Accepting gives that station the
 * account; declining leaves its member without it.
 */
const props = defineProps<{
  prompt: LinkPrompt
  busy: boolean
}>()

const emit = defineEmits<{
  accept: []
  decline: []
}>()

const {t} = useI18n()

const how = computed(() => props.prompt.origin === LinkOrigin.IMPORT
    ? t('accountLinks.fromImport')
    : props.prompt.invitedBy
        ? t('accountLinks.invitedBy', {name: props.prompt.invitedBy})
        : t('accountLinks.invited'))
</script>

<template>
  <NeutralContainer class="space-y-3" data-testid="link-request">
    <SubHeader>{{ prompt.stationName }}</SubHeader>
    <p class="text-sm">{{ t('accountLinks.asks', {member: prompt.memberName}) }}</p>
    <MutedText tag="p" size="sm">{{ how }}</MutedText>
    <MutedText tag="p" size="sm">{{ t('accountLinks.until', {date: formatDate(prompt.expiresAt)}) }}</MutedText>
    <ButtonRow pair align="end">
      <SecondaryButton :disabled="busy" :icon="['fas', 'xmark']" data-testid="link-decline" @click="emit('decline')">
        {{ t('accountLinks.decline') }}
      </SecondaryButton>
      <SuccessButton :disabled="busy" :icon="['fas', 'link']" data-testid="link-accept" @click="emit('accept')">
        {{ t('accountLinks.accept') }}
      </SuccessButton>
    </ButtonRow>
  </NeutralContainer>
</template>

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
import {askerOf, isAssociationRequest} from './linkPromptText'
import {formatDate} from '@/util/format'

/**
 * One request to the reader's account: a station asking to link it to one of its members, or an
 * association asking it to take a role. Says who asks, for what, how they came to ask, and until when
 * it waits. Accepting gives the asker the account; declining leaves nothing behind.
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

const question = computed(() => isAssociationRequest(props.prompt)
    ? t('accountLinks.asksRole', {role: t(`accountLinks.role.${props.prompt.role}`)})
    : t('accountLinks.asks', {member: props.prompt.memberName}))

const how = computed(() => {
  if (isAssociationRequest(props.prompt)) return t('accountLinks.fromAssociation')
  if (props.prompt.origin === LinkOrigin.IMPORT) return t('accountLinks.fromImport')
  return props.prompt.invitedBy
      ? t('accountLinks.invitedBy', {name: props.prompt.invitedBy})
      : t('accountLinks.invited')
})
</script>

<template>
  <NeutralContainer class="space-y-3" data-testid="link-request">
    <SubHeader>{{ askerOf(prompt) }}</SubHeader>
    <p class="text-sm">{{ question }}</p>
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

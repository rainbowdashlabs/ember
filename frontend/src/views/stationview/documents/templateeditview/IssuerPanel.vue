/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Alert from '@/components/feedback/Alert.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import IssuerFields from '@/components/documents/IssuerFields.vue'
import {fromMember} from '@/components/input/select/memberOption'
import type {MemberWithName} from '@/api/generated/schema'
import type {TemplateDraft} from './templateDraft'

/**
 * Who issues the template's documents: a current member of the station with what they do there. Their
 * official name and the function fill the placeholders "Ausstellende Person: Name" and "Ausstellende
 * Person: Funktion", and the issuer's signature line is theirs, in every document, self service
 * included. A manager may pick somebody else for one document.
 *
 * <p>An issuer who left the station is still named here and shown as missing in every document until
 * another one is chosen. An association has no members, so each station names the issuer of its
 * templates in how it uses them.
 */
const draft = defineModel<TemplateDraft>({required: true})

const props = defineProps<{
  /** The current members of the station. */
  members: MemberWithName[]
  /** Whether the owner names the issuer itself, which only a station does. */
  choosesIssuer: boolean
}>()

const {t} = useI18n()

const options = computed(() => props.members.map(fromMember))
const left = computed(() => draft.value.issuerId !== null
    && !props.members.some(member => member.id === draft.value.issuerId))
</script>

<template>
  <NeutralContainer class="space-y-4" data-testid="template-issuer">
    <SubHeader>{{ t('documentTemplates.issuer.title') }}</SubHeader>
    <MutedText size="sm" tag="p">{{ t('documentTemplates.issuer.hint') }}</MutedText>
    <template v-if="choosesIssuer">
      <Alert v-if="left" variant="error">{{ t('documentTemplates.issuer.left') }}</Alert>
      <IssuerFields v-model:member-id="draft.issuerId" v-model:issuer-function="draft.issuerFunction" :members="options"/>
    </template>
    <MutedText v-else size="sm" tag="p">{{ t('documentTemplates.issuer.ofAssociation') }}</MutedText>
  </NeutralContainer>
</template>

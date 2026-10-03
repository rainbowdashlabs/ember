/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ToggleSetting from '@/components/input/toggle/ToggleSetting.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {IssuerChoice, PreviewIssuer} from '@/api/generated/schema'
import type {MemberOption} from '@/components/input/select/memberOption'
import IssuerFields from './IssuerFields.vue'

/**
 * Who issues the document a manager generates: the template's issuer, or another current member of the
 * station picked for this document or this run, with what they do. Shown only where the document names
 * its issuer. The choice reaches the caller as soon as a member is picked; without one, the template's
 * issuer stays.
 */
const choice = defineModel<IssuerChoice | null>({required: true})

const props = defineProps<{
  /** The issuer the template names, as the preview drew it. */
  templateIssuer: PreviewIssuer
  /** The current members of the station. */
  members: MemberOption[]
}>()

const {t} = useI18n()

const picking = ref(choice.value !== null)
const memberId = ref<number | null>(choice.value?.memberId ?? null)
const issuerFunction = ref(choice.value?.function ?? props.templateIssuer.function ?? '')

const named = computed(() => {
  const issuer = props.templateIssuer
  if (!issuer.name) return t('documentTemplates.issuer.missing')
  return issuer.function ? `${issuer.name}, ${issuer.function}` : issuer.name
})

watch([picking, memberId, issuerFunction], () => {
  choice.value = picking.value && memberId.value !== null
    ? {memberId: memberId.value, function: issuerFunction.value.trim() || null}
    : null
})
</script>

<template>
  <div class="space-y-2" data-testid="issuer-override">
    <FieldLabel>{{ t('documentTemplates.issuer.title') }}</FieldLabel>
    <MutedText size="sm" tag="p">{{ t('documentTemplates.issuer.fromTemplate', {issuer: named}) }}</MutedText>
    <ToggleSetting v-model="picking" :label="t('documentTemplates.issuer.other')"
                   :hint="t('documentTemplates.issuer.otherHint')" data-testid="issuer-other"/>
    <IssuerFields v-if="picking" v-model:member-id="memberId" v-model:issuer-function="issuerFunction" :members="members"/>
  </div>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ToggleSetting from '@/components/input/toggle/ToggleSetting.vue'
import Alert from '@/components/feedback/Alert.vue'
import type {SignatureSettingsResponse} from '@/api/generated/schema'
import {formatDateTime} from '@/util/format'

/**
 * The consent to letters being signed with the reader's picture without them signing each one: what it
 * means, since when it was given, and the switch that gives or takes it back.
 *
 * <p>A consent without a saved picture signs nothing, and the section says so rather than letting the
 * switch suggest otherwise.
 */
const props = defineProps<{
  settings: SignatureSettingsResponse
  busy: boolean
}>()

const emit = defineEmits<{change: [consented: boolean]}>()

const {t} = useI18n()

const consented = computed({
  get: () => props.settings.autoSignConsentedAt != null,
  set: (value: boolean) => emit('change', value),
})
</script>

<template>
  <NeutralContainer class="space-y-4" data-testid="signature-consent">
    <SectionHeader>{{ t('accountSignature.consent.heading') }}</SectionHeader>
    <MutedText tag="p" size="sm">{{ t('accountSignature.consent.what') }}</MutedText>
    <MutedText tag="p" size="sm">{{ t('accountSignature.consent.when') }}</MutedText>
    <ToggleSetting v-model="consented" :label="t('accountSignature.consent.toggle')" :disabled="busy"/>
    <MutedText v-if="settings.autoSignConsentedAt" tag="p" size="sm" data-testid="signature-consented-at">
      {{ t('accountSignature.consent.since', {time: formatDateTime(settings.autoSignConsentedAt)}) }}
    </MutedText>
    <Alert v-if="consented && !settings.hasImage" variant="info">{{ t('accountSignature.consent.noImage') }}</Alert>
  </NeutralContainer>
</template>

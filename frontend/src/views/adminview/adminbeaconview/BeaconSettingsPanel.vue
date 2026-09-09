/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import {beacon} from '@/api'
import type {BeaconStatus} from '@/api/beacon'

/**
 * What this instance sends, and whether it takes anything in.
 *
 * <p>The master switch is kept apart from the three beneath it, because turning reporting off should
 * be one action rather than three, and because the three beneath say nothing at all while it is off.
 */
const props = defineProps<{status: BeaconStatus}>()

const emit = defineEmits<{saved: [status: BeaconStatus]}>()

const {t} = useI18n()

const form = ref<BeaconStatus>({...props.status})
const saving = ref(false)
const error = ref('')
const saved = ref(false)

watch(() => props.status, value => { form.value = {...value} })

async function save() {
  saving.value = true
  error.value = ''
  saved.value = false
  try {
    const stored = await beacon.updateSettings(form.value)
    form.value = {...stored}
    saved.value = true
    emit('saved', stored)
  } catch {
    error.value = t('common.error')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('beacon.settingsTitle') }}</SubHeader>
    <MutedText tag="p" size="sm">{{ t('beacon.settingsHint') }}</MutedText>

    <Alert v-if="error" variant="error">{{ error }}</Alert>
    <Alert v-if="saved" variant="success">{{ t('beacon.settingsSaved') }}</Alert>

    <label class="flex items-center gap-2 text-sm">
      <ToggleInput v-model="form.enabled" data-testid="beacon-enabled"/>
      {{ t('beacon.enabled') }}
    </label>

    <div class="space-y-1">
      <FieldLabel>{{ t('beacon.url') }}</FieldLabel>
      <TextInput v-model="form.url" :disabled="!form.enabled"/>
    </div>

    <div class="space-y-2 pl-4 border-l-2 border-bg-light-accent dark:border-bg-dark-accent">
      <label class="flex items-center gap-2 text-sm">
        <ToggleInput v-model="form.forwardProblems" :disabled="!form.enabled"/>
        {{ t('beacon.forwardProblems') }}
      </label>
      <label class="flex items-center gap-2 text-sm">
        <ToggleInput v-model="form.forwardReports" :disabled="!form.enabled"/>
        {{ t('beacon.forwardReports') }}
      </label>
      <label class="flex items-center gap-2 text-sm">
        <ToggleInput v-model="form.metricsEnabled" :disabled="!form.enabled"/>
        {{ t('beacon.metricsEnabled') }}
      </label>
    </div>

    <div class="grid gap-3 sm:grid-cols-2">
      <div class="space-y-1">
        <FieldLabel>{{ t('beacon.contactName') }}</FieldLabel>
        <TextInput v-model="form.contactName" :disabled="!form.enabled"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('beacon.contactMail') }}</FieldLabel>
        <TextInput v-model="form.contactMail" :disabled="!form.enabled"/>
      </div>
    </div>
    <MutedText tag="p" size="sm">{{ t('beacon.contactHint') }}</MutedText>

    <label class="flex items-center gap-2 text-sm border-t border-bg-light-accent dark:border-bg-dark-accent pt-4">
      <ToggleInput v-model="form.receiving" data-testid="beacon-receiving"/>
      {{ t('beacon.receiving') }}
    </label>

    <div class="flex justify-end">
      <PrimaryButton :disabled="saving" @click="save">
        {{ saving ? t('common.loading') : t('common.save') }}
      </PrimaryButton>
    </div>
  </NeutralContainer>
</template>

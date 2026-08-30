/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import DummyFormRow from '@/views/helpcenter/stationview/forms/listhelp/DummyFormRow.vue'

defineProps<{
  view: string
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-3">
    <div class="flex items-center justify-between">
      <SubHeader>{{ t('forms.title') }}</SubHeader>
      <PrimaryButton v-if="view === 'manager'" :icon="['fas', 'plus']">
        {{ t('forms.create') }}
      </PrimaryButton>
    </div>

    <DummyFormRow
      title="Zufriedenheitsumfrage"
      description="Wie gefällt dir unsere Jugendfeuerwehr?"
      :manager-view="view === 'manager'"
    >
      <template #lock>
        <MutedIcon v-if="view === 'manager'" :icon="['fas', 'lock']" class="ml-1"/>
      </template>
      <template #badge>
        <SuccessBadge>{{ t('forms.statusOpen') }}</SuccessBadge>
      </template>
      <template #actions>
        <PrimaryButton v-if="view === 'member'">{{ t('forms.fillForm') }}</PrimaryButton>
        <template v-if="view === 'manager'">
          <SecondaryButton>{{ t('forms.viewAnalytics') }}</SecondaryButton>
          <SecondaryButton>{{ t('forms.close') }}</SecondaryButton>
          <ErrorButton>{{ t('forms.delete') }}</ErrorButton>
        </template>
      </template>
    </DummyFormRow>

    <DummyFormRow
      v-if="view === 'member'"
      title="Feedback Übungsabend"
      description="Rückmeldung zum letzten Übungsabend"
      :manager-view="false"
    >
      <template #badge><SuccessBadge>{{ t('forms.statusOpen') }}</SuccessBadge></template>
      <template #actions><SecondaryButton>{{ t('forms.editResponse') }}</SecondaryButton></template>
    </DummyFormRow>

    <DummyFormRow
      v-if="view === 'manager'"
      title="Feedback Übungsabend"
      description="Rückmeldung zum letzten Übungsabend"
      :manager-view="true"
    >
      <template #badge><ErrorBadge>{{ t('forms.statusClosed') }}</ErrorBadge></template>
      <template #actions>
        <SecondaryButton>{{ t('forms.viewAnalytics') }}</SecondaryButton>
        <ErrorButton>{{ t('forms.delete') }}</ErrorButton>
      </template>
    </DummyFormRow>

    <DummyFormRow
      v-if="view === 'manager'"
      title="Neue Umfrage (Entwurf)"
      :manager-view="true"
    >
      <template #badge><InfoBadge>{{ t('forms.statusDraft') }}</InfoBadge></template>
      <template #actions>
        <SecondaryButton>{{ t('forms.publish') }}</SecondaryButton>
        <SecondaryButton>{{ t('forms.edit') }}</SecondaryButton>
        <ErrorButton>{{ t('forms.delete') }}</ErrorButton>
      </template>
    </DummyFormRow>
  </div>
</template>

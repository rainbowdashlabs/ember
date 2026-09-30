/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DummyMemberSelector from '@/views/helpcenter/stationview/forms/fillhelp/DummyMemberSelector.vue'
import DummyTextQuestion from '@/views/helpcenter/stationview/forms/fillhelp/DummyTextQuestion.vue'
import DummyChoiceQuestion from '@/views/helpcenter/stationview/forms/fillhelp/DummyChoiceQuestion.vue'
import DummyRatingQuestion from '@/views/helpcenter/stationview/forms/fillhelp/DummyRatingQuestion.vue'
import DummyDateQuestion from '@/views/helpcenter/stationview/forms/fillhelp/DummyDateQuestion.vue'

const {t} = useI18n()
</script>

<template>
  <section>
    <SectionHeader>{{ t('helpCenter.sample.forms.survey') }}</SectionHeader>
    <p class="text-sm text-(--text-muted) mb-4">{{ t('helpCenter.sample.forms.surveyIntro') }}</p>

    <DummyMemberSelector/>
    <DummyTextQuestion/>
    <DummyChoiceQuestion/>
    <DummyRatingQuestion/>
    <DummyDateQuestion/>

    <div class="flex justify-end mt-4">
      <PrimaryButton :icon="['fas', 'paper-plane']" disabled>
        {{ t('forms.submit') }}
      </PrimaryButton>
    </div>
  </section>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.courseNew.title')" :subtitle="t('helpCenter.courseNew.subtitle')">
    <HelpSection :title="t('helpCenter.courseNew.whatIs')">
      <p>{{ t('helpCenter.courseNew.whatIsText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.courseNew.howTo')">
      <p>{{ t('helpCenter.courseNew.step1') }}</p>
      <p>{{ t('helpCenter.courseNew.step2') }}</p>
      <NeutralContainer class="space-y-2">
        <FieldLabel>{{ t('courses.name') }}</FieldLabel>
        <TextInput :model-value="t('helpCenter.courses.exampleName')"/>
        <MutedText tag="p" size="sm">{{ t('courses.setsHint') }}</MutedText>
        <div class="flex items-center gap-2">
          <TextInput :model-value="t('courses.defaultSet')" class="flex-1"/>
          <NumberInput :model-value="1" class="w-24"/>
        </div>
      </NeutralContainer>
      <p>{{ t('helpCenter.courseNew.step3') }}</p>
    </HelpSection>
    <HelpTip>{{ t('helpCenter.courseNew.tip') }}</HelpTip>
  </HelpArticle>
</template>

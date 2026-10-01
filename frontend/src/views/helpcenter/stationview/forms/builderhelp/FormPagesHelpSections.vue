/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import PageRouting from '@/views/stationview/forms/builderview/pages/PageRouting.vue'
import {sampleLayoutEditor} from '@/views/helpcenter/stationview/forms/builderhelp/fixtures'

/**
 * What the form editor can do beyond single questions: pages, where they lead, answers that decide,
 * moving things, the preview, copies, the message after sending and changes kept until saved. Shown
 * on the help for creating a form and for editing one alike, since both are the same editor.
 */
const {t} = useI18n()

const layout = sampleLayoutEditor(t)
</script>

<template>
  <HelpSection :title="t('helpCenter.formsPages.pagesTitle')">
    <p>{{ t('helpCenter.formsPages.pagesText') }}</p>
  </HelpSection>

  <HelpSection :title="t('helpCenter.formsPages.afterTitle')">
    <p>{{ t('helpCenter.formsPages.afterText') }}</p>
  </HelpSection>

  <HelpSection :title="t('helpCenter.formsPages.branchTitle')">
    <p>{{ t('helpCenter.formsPages.branchText') }}</p>
    <PageRouting inert :layout="layout" :page-index="0"/>
    <p>{{ t('helpCenter.formsPages.unreachedText') }}</p>
  </HelpSection>

  <HelpSection :title="t('helpCenter.formsPages.dragTitle')">
    <p>{{ t('helpCenter.formsPages.dragText') }}</p>
  </HelpSection>

  <HelpSection :title="t('helpCenter.formsPages.previewTitle')">
    <p>{{ t('helpCenter.formsPages.previewText') }}</p>
  </HelpSection>

  <HelpSection :title="t('helpCenter.formsPages.duplicateTitle')">
    <p>{{ t('helpCenter.formsPages.duplicateText') }}</p>
  </HelpSection>

  <HelpSection :title="t('helpCenter.formsPages.completionTitle')">
    <p>{{ t('helpCenter.formsPages.completionText') }}</p>
  </HelpSection>

  <HelpSection :title="t('helpCenter.formsPages.unsavedTitle')">
    <p>{{ t('helpCenter.formsPages.unsavedText') }}</p>
  </HelpSection>
</template>

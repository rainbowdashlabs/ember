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

const {t} = useI18n()
</script>

<template>
    <HelpArticle :title="t('helpCenter.pageForms.title')" :subtitle="t('helpCenter.pageForms.subtitle')">
        <HelpSection :title="t('helpCenter.pageForms.whatIs')">
            <p>{{ t('helpCenter.pageForms.whatIsText') }}</p>
        </HelpSection>
        <HelpSection :title="t('helpCenter.pageForms.createTitle')">
            <p>{{ t('helpCenter.pageForms.createText') }}</p>
        </HelpSection>
        <HelpSection :title="t('helpCenter.pageForms.embedTitle')">
            <p>{{ t('helpCenter.pageForms.embedText') }}</p>
        </HelpSection>
        <HelpTip>{{ t('helpCenter.pageForms.tip') }}</HelpTip>
    </HelpArticle>
</template>

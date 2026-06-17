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
    <HelpArticle :title="t('helpCenter.pagePolls.title')" :subtitle="t('helpCenter.pagePolls.subtitle')">
        <HelpSection :title="t('helpCenter.pagePolls.whatIs')">
            <p>{{ t('helpCenter.pagePolls.whatIsText') }}</p>
        </HelpSection>
        <HelpSection :title="t('helpCenter.pagePolls.createTitle')">
            <p>{{ t('helpCenter.pagePolls.createText') }}</p>
        </HelpSection>
        <HelpSection :title="t('helpCenter.pagePolls.embedTitle')">
            <p>{{ t('helpCenter.pagePolls.embedText') }}</p>
        </HelpSection>
        <HelpTip>{{ t('helpCenter.pagePolls.tip') }}</HelpTip>
    </HelpArticle>
</template>

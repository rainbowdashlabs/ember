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
    <HelpArticle :title="t('forms.contactSubmissions.title')"
                 :subtitle="t('helpCenter.pagesFormsSubmissions.subtitle')">
        <HelpSection :title="t('helpCenter.pagesFormsSubmissions.intro.title')">
            <p>{{ t('helpCenter.pagesFormsSubmissions.intro.body') }}</p>
        </HelpSection>
        <HelpSection :title="t('helpCenter.pagesFormsSubmissions.acknowledge.title')">
            <p>{{ t('helpCenter.pagesFormsSubmissions.acknowledge.body') }}</p>
            <HelpTip>{{ t('helpCenter.pagesFormsSubmissions.acknowledge.tip') }}</HelpTip>
        </HelpSection>
    </HelpArticle>
</template>

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
    <HelpArticle
        :title="t('helpCenter.adminStorageBackend.title')"
        :subtitle="t('helpCenter.adminStorageBackend.subtitle')"
    >
        <HelpSection :title="t('helpCenter.adminStorageBackend.put.title')">
            <p>{{ t('helpCenter.adminStorageBackend.put.text') }}</p>
            <p>{{ t('helpCenter.adminStorageBackend.put.warning') }}</p>
        </HelpSection>
        <HelpSection :title="t('helpCenter.adminStorageBackend.migrate.title')">
            <p>{{ t('helpCenter.adminStorageBackend.migrate.text') }}</p>
            <p>{{ t('helpCenter.adminStorageBackend.migrate.window') }}</p>
            <p>{{ t('helpCenter.adminStorageBackend.migrate.overrides') }}</p>
        </HelpSection>
        <HelpSection :title="t('helpCenter.adminStorageBackend.locks.title')">
            <p>{{ t('helpCenter.adminStorageBackend.locks.text') }}</p>
        </HelpSection>
        <HelpTip>{{ t('helpCenter.adminStorageBackend.tip') }}</HelpTip>
    </HelpArticle>
</template>

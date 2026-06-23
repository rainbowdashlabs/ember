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
        :title="t('helpCenter.stationStorageBackend.title')"
        :subtitle="t('helpCenter.stationStorageBackend.subtitle')"
    >
        <HelpSection :title="t('helpCenter.stationStorageBackend.where.title')">
            <p>{{ t('helpCenter.stationStorageBackend.where.text') }}</p>
            <p>{{ t('helpCenter.stationStorageBackend.where.types') }}</p>
        </HelpSection>

        <HelpSection :title="t('helpCenter.stationStorageBackend.moving.title')">
            <p>{{ t('helpCenter.stationStorageBackend.moving.text') }}</p>
            <p>{{ t('helpCenter.stationStorageBackend.moving.window') }}</p>
        </HelpSection>

        <HelpSection :title="t('helpCenter.stationStorageBackend.audit.title')">
            <p>{{ t('helpCenter.stationStorageBackend.audit.text') }}</p>
        </HelpSection>

        <HelpTip>{{ t('helpCenter.stationStorageBackend.tip') }}</HelpTip>
    </HelpArticle>
</template>

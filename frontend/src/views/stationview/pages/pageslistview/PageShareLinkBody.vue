/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ShareLinkPanel from '@/components/public/ShareLinkPanel.vue'
import type {PageShareLinkResponse} from '@/api/generated/schema'
import type {Failure} from '@/util/failure'

/**
 * The link a page is reached at, for whoever hands it out.
 *
 * <p>A link opens nothing while the station keeps its public pages switched off, and a new station
 * starts that way. Said here, beside the link, because the only other one to find out is a stranger
 * who holds the link and has nobody to ask.
 */
defineProps<{
  /** The page's link, or nothing while it is being fetched. */
  link: PageShareLinkResponse | null
  busy: boolean
  failure: Failure | null
}>()

const emit = defineEmits<{
  (e: 'replace'): void
  (e: 'create'): void
}>()

const {t} = useI18n()
</script>

<template>
    <div class="space-y-4">
        <SubHeader>{{ t('stationPages.shareLink') }}</SubHeader>
        <Alert v-if="link && !link.opens" variant="info" data-testid="share-link-closed">
            {{ t('shareLink.pagesClosed') }}
        </Alert>
        <ShareLinkPanel
            v-if="link?.token"
            :path="`/s/${link.token}`"
            :busy="busy"
            :failure="failure"
            replaceable
            @replace="emit('replace')"
        />
        <FailureAlert v-else-if="failure" :failure="failure"/>
        <div v-else class="space-y-3">
            <MutedText tag="p" size="sm">{{ t('shareLink.none') }}</MutedText>
            <ButtonRow align="end">
                <PrimaryButton :disabled="busy" :icon="['fas', 'link']" @click="emit('create')">
                    {{ t('shareLink.create') }}
                </PrimaryButton>
            </ButtonRow>
        </div>
    </div>
</template>

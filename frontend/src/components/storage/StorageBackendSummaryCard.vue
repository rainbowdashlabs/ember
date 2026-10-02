/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import StorageProbeOutcome from '@/components/storage/StorageProbeOutcome.vue'
import type {ProbeResult} from '@/api/generated/schema'

/**
 * Where an owner's files are kept, the same card on the station's, the association's and the instance's
 * storage screen.
 *
 * The first line says where the files are; any further line says what that rests on, such as the default a
 * station falls back to. A storage the owner keeps itself can be tested from here, and the answer is shown
 * beneath it.
 */
withDefaults(defineProps<{
    lines: string[]
    canProbe?: boolean
    probing?: boolean
    probeOutcome?: ProbeResult | null
}>(), {
    canProbe: false,
    probing: false,
    probeOutcome: null,
})

const emit = defineEmits<{
    probe: []
}>()

const {t} = useI18n()
</script>

<template>
    <NeutralContainer class="space-y-3">
        <SubHeader>{{ t('storageBackend.summary.title') }}</SubHeader>
        <MutedText
            v-for="(line, index) in lines"
            :key="index"
            tag="p"
            size="sm"
            :data-testid="index === 0 ? 'storage-backend-where' : undefined"
        >
            {{ line }}
        </MutedText>
        <div v-if="canProbe">
            <SecondaryButton :disabled="probing" data-testid="storage-backend-probe-saved" @click="emit('probe')">
                {{ probing ? t('storageBackend.summary.probing') : t('storageBackend.summary.probe') }}
            </SecondaryButton>
        </div>
        <StorageProbeOutcome :outcome="probeOutcome"/>
    </NeutralContainer>
</template>

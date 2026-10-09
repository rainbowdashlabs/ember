/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {ZOOM_STEPS, zoomStep} from './pdfZoom'

/**
 * Zooming a drawn PDF page in steps. The middle button names the zoom and brings the whole page back
 * into view.
 */
const zoom = defineModel<number>('zoom', {required: true})

const {t} = useI18n()

const percent = computed(() => Math.round(zoom.value * 100))
const smallest = computed(() => zoom.value <= (ZOOM_STEPS[0] ?? 1))
const largest = computed(() => zoom.value >= (ZOOM_STEPS.at(-1) ?? 1))
</script>

<template>
  <div class="flex items-center gap-1" data-testid="pdf-zoom">
    <IconButton :icon="['fas', 'magnifying-glass-minus']" :label="t('files.zoomOut')" :disabled="smallest"
                data-testid="pdf-zoom-out" @click="zoom = zoomStep(zoom, -1)"/>
    <SecondaryButton compact :title="t('files.zoomReset', {percent})" data-testid="pdf-zoom-reset"
                     @click="zoom = 1">{{ percent }} %</SecondaryButton>
    <IconButton :icon="['fas', 'magnifying-glass-plus']" :label="t('files.zoomIn')" :disabled="largest"
                data-testid="pdf-zoom-in" @click="zoom = zoomStep(zoom, 1)"/>
  </div>
</template>

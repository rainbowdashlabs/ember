/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {ImageFit, type ImageConfig} from '@/api/pageManage'

const props = defineProps<{
    src: string
    alt?: string
    title?: string
    config: ImageConfig | null | undefined
}>()

const containerRef = ref<HTMLElement | null>(null)
const naturalWidth = ref(0)
const naturalHeight = ref(0)
const renderedWidth = ref(0)
const renderedHeight = ref(0)

let observer: ResizeObserver | null = null

onMounted(() => {
    if (!containerRef.value) return
    observer = new ResizeObserver(entries => {
        for (const e of entries) {
            renderedWidth.value = e.contentRect.width
            renderedHeight.value = e.contentRect.height
        }
    })
    observer.observe(containerRef.value)
})

onBeforeUnmount(() => observer?.disconnect())

watch(() => props.src, () => {
    naturalWidth.value = 0
    naturalHeight.value = 0
})

function onImgLoad(e: Event) {
    const img = e.target as HTMLImageElement
    naturalWidth.value = img.naturalWidth
    naturalHeight.value = img.naturalHeight
}

function clampPct(v: number | null | undefined): number {
    if (v == null) return 0
    return Math.max(0, Math.min(90, v))
}

const cfg = computed<ImageConfig>(() => props.config ?? {})

const cropPercents = computed(() => ({
    T: clampPct(cfg.value.cropTop),
    R: clampPct(cfg.value.cropRight),
    B: clampPct(cfg.value.cropBottom),
    L: clampPct(cfg.value.cropLeft),
}))

const hasCrop = computed(() => {
    const {T, R, B, L} = cropPercents.value
    return T + R + B + L > 0
})

/** Aspect ratio of the *visible* (cropped) region — drives the container's height. */
const visibleAspect = computed(() => {
    if (!naturalWidth.value || !naturalHeight.value) return null
    const {T, R, B, L} = cropPercents.value
    const w = naturalWidth.value * (100 - L - R) / 100
    const h = naturalHeight.value * (100 - T - B) / 100
    if (w <= 0 || h <= 0) return null
    return w / h
})

/** Container style: max-width:100%, optional max-height, explicit width derived from max-height
 *  so aspect-ratio is honored (otherwise width would lock to 100% and break the aspect). */
const containerStyle = computed(() => {
    const c = cfg.value
    const style: Record<string, string> = {
        maxWidth: '100%',
        margin: '0 auto',
    }
    const aspect = visibleAspect.value
    if (aspect != null) {
        style.aspectRatio = `${aspect}`
        if (c.maxHeight != null && c.maxHeight > 0) {
            style.maxHeight = `${c.maxHeight}px`
            // Cap width so max-height can actually shrink the box without breaking the aspect.
            style.width = `min(100%, ${c.maxHeight * aspect}px)`
        } else {
            style.width = '100%'
        }
    } else {
        style.width = '100%'
        if (c.maxHeight != null && c.maxHeight > 0) style.maxHeight = `${c.maxHeight}px`
        style.aspectRatio = hasCrop.value ? '1 / 1' : '16 / 9'
    }
    return style
})

/** Frame style: border + radius + clipping. Radius is computed as px from the rendered
 *  dimensions so a 50% value produces a circle on a square and a stadium on a rectangle —
 *  never an ellipse. */
const frameStyle = computed(() => {
    const c = cfg.value
    const style: Record<string, string> = {
        width: '100%',
        height: '100%',
        overflow: 'hidden',
        boxSizing: 'border-box',
    }
    if (c.borderRadiusPercent != null && c.borderRadiusPercent > 0) {
        const minDim = Math.min(renderedWidth.value, renderedHeight.value)
        if (minDim > 0) {
            style.borderRadius = `${(c.borderRadiusPercent / 100) * minDim}px`
        }
    }
    if (c.borderWidthPx != null && c.borderWidthPx > 0) {
        style.border = `${c.borderWidthPx}px solid ${c.borderColor ?? 'var(--border)'}`
    }
    return style
})

/** Image style: uniform scale + translate when cropped, object-fit otherwise. */
const imageStyle = computed(() => {
    const {T, R, B, L} = cropPercents.value
    if (hasCrop.value) {
        const scale = 100 / Math.max(1, 100 - L - R)
        return {
            display: 'block',
            width: '100%',
            transformOrigin: 'top left',
            transform: `scale(${scale}) translate(${-L}%, ${-T}%)`,
        }
    }
    return {
        display: 'block',
        width: '100%',
        height: '100%',
        objectFit: (cfg.value.imageFit ?? ImageFit.CONTAIN).toLowerCase(),
    }
})
</script>

<template>
    <div ref="containerRef" :style="containerStyle">
        <div :style="frameStyle">
            <img
                :src="src"
                :alt="alt ?? ''"
                :title="title ?? alt ?? ''"
                :style="imageStyle"
                @load="onImgLoad"
            />
        </div>
    </div>
</template>

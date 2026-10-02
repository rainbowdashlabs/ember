/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed, useId } from 'vue'
import { nodeViewProps, NodeViewWrapper } from '@tiptap/vue-3'
import AuthImage from '@/components/display/AuthImage.vue'
import IconButton from '@/components/button/IconButton.vue'
import { useI18n } from 'vue-i18n'
import { isKbImageSrc } from '@/util/normalizeAuthSrc'

const props = defineProps(nodeViewProps)

const { t } = useI18n()

const imgWidth = computed(() => props.node.attrs.width ? String(props.node.attrs.width) : '')

const imgStyle = computed(() => {
  if (props.node.attrs.width) return `width: ${props.node.attrs.width}px`
  return ''
})

const widthId = useId()

const srcStr = computed(() => (props.node.attrs.src as string) ?? '')
const isAuthSrc = computed(() => isKbImageSrc(srcStr.value))

function onWidthChange(e: Event) {
  const val = (e.target as HTMLInputElement).value
  props.updateAttributes({ width: val ? parseInt(val) || null : null })
}
</script>

<template>
  <NodeViewWrapper as="div" class="image-node-view" :class="{ 'image-selected': selected }">
    <AuthImage
      v-if="isAuthSrc"
      :src="srcStr"
      :alt="(node.attrs.alt as string) || ''"
      :style="imgStyle"
      class="max-w-full rounded"
      draggable="false"
    />
    <img
      v-else
      :src="srcStr"
      :alt="(node.attrs.alt as string) || ''"
      :title="(node.attrs.title as string) || ''"
      :style="imgStyle"
      class="max-w-full rounded"
      draggable="false"
    />
    <div class="image-controls" @mousedown.stop>
      <label :for="widthId" class="text-xs text-[var(--text-muted)] mr-1">{{ t('markdownEditor.imageWidth') }}</label>
      <input
        :id="widthId"
        type="number"
        :value="imgWidth"
        :placeholder="t('markdownEditor.imageWidthAuto')"
        class="w-16 px-1 py-0.5 text-xs rounded border border-[var(--border)] bg-[var(--bg)] text-[var(--text)] text-center"
        min="50"
        step="10"
        @change="onWidthChange"
      />
      <span class="text-xs text-[var(--text-muted)]">px</span>
      <div class="w-px h-4 bg-[var(--border)] mx-1.5" />
      <IconButton
        :icon="['fas', 'trash']"
        :label="t('markdownEditor.removeImage')"
        class="hover:bg-red-100 dark:hover:bg-red-900/30 text-red-500 cursor-pointer"
        @click="deleteNode"
      >
        <font-awesome-icon :icon="['fas', 'trash']" class="w-3 h-3" />
      </IconButton>
    </div>
  </NodeViewWrapper>
</template>

<style>
.image-node-view {
  display: inline-block;
}

.image-node-view.image-selected img {
  outline: 2px solid var(--primary);
  outline-offset: 2px;
  border-radius: 4px;
}

.image-controls {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 4px 8px;
  margin-top: 4px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 0.5rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  white-space: nowrap;
  width: fit-content;
}
</style>

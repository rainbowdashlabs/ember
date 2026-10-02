/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {DialogContent, DialogOverlay, DialogPortal, DialogRoot} from 'reka-ui'
import ModalBody from '@/components/feedback/ModalBody.vue'
import {useDialogOverlay} from '@/components/feedback/dialogLayers'

/**
 * A dialog that slides in from the right edge and takes the screen's full height, for a record
 * with more to it than a centred dialog holds.
 *
 * <p>It behaves as {@link Modal} does: Escape and a press on the dimmed page close it, the focus
 * stays inside while it is open and returns afterwards, and it is labelled by its first heading.
 * It brings no close button of its own, since the header its content opens with carries one.
 */
const open = defineModel<boolean>({default: false})

const {overlay, layer, keepOpenUnlessOverlay} = useDialogOverlay(open)
</script>

<template>
  <DialogRoot v-model:open="open">
    <DialogPortal>
      <DialogOverlay
          ref="overlay"
          class="fixed inset-0 flex justify-end bg-black/40 data-[state=open]:animate-fade-in data-[state=closed]:animate-fade-out"
          :style="{zIndex: layer}"
      >
        <DialogContent
            :aria-describedby="undefined"
            class="flex h-full w-full max-w-2xl flex-col bg-(--bg) border-l border-(--border) shadow-2xl outline-none data-[state=open]:animate-fade-in data-[state=closed]:animate-fade-out"
            @pointer-down-outside="keepOpenUnlessOverlay"
        >
          <ModalBody>
            <slot/>
          </ModalBody>
        </DialogContent>
      </DialogOverlay>
    </DialogPortal>
  </DialogRoot>
</template>

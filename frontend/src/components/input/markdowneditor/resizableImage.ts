/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { VueNodeViewRenderer } from '@tiptap/vue-3'
import Image from '@tiptap/extension-image'
import ImageNodeView from '@/components/input/ImageNodeView.vue'

/**
 * The image node with a user-settable width, rendered through a Vue node view so the width can
 * be dragged in the editor.
 *
 * The width is written both as an attribute and as an inline style: the attribute is what the
 * Markdown conversion reads back, the style is what the browser honours.
 */
export const ResizableImage = Image.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      width: {
        default: null,
        parseHTML: (el: HTMLElement) => el.getAttribute('width') || el.style.width?.replace('px', '') || null,
        renderHTML: (attrs: Record<string, unknown>) =>
          attrs.width ? {width: String(attrs.width), style: `width: ${attrs.width}px`} : {},
      },
    }
  },
  addNodeView() {
    return VueNodeViewRenderer(ImageNodeView)
  },
}).configure({inline: false, allowBase64: false})

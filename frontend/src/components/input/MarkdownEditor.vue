/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed, ref, onBeforeUnmount, watch, nextTick, onMounted } from 'vue'
import {EditorContent, useEditor, type Content} from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Underline from '@tiptap/extension-underline'
import Link from '@tiptap/extension-link'
import Placeholder from '@tiptap/extension-placeholder'
import { Table } from '@tiptap/extension-table'
import { TableRow } from '@tiptap/extension-table-row'
import { TableHeader } from '@tiptap/extension-table-header'
import { TableCell } from '@tiptap/extension-table-cell'
import { Highlight } from '@tiptap/extension-highlight'
import { Youtube } from '@tiptap/extension-youtube'
import { Color } from '@tiptap/extension-color'
import { TextStyle } from '@tiptap/extension-text-style'
import { renderMarkdown } from '@/util/markdown'
import { useSession } from '@/composables/useSession'
import { useEditorFontAreaAttrs, useInjectedEditorFonts } from '@/composables/useEditorFonts'
import MediaBrowseModal from '@/components/media/MediaBrowseModal.vue'
import { createMarkdownTurndown } from './markdowneditor/markdownTurndown'
import { ResizableImage } from './markdowneditor/resizableImage'
import type { EditorTokens } from './markdowneditor/editorTokens'
import { extendTurndownWithTextFont, TextFont } from './markdowneditor/textFont'
import { BlockAlign } from './markdowneditor/blockAlign'
import { TextSize } from './markdowneditor/textSize'
import type { FontFamilyOption } from '@/api/generated/schema'
import { isYoutubeUrl, videoEmbedUrl } from '@/util/youtube'
import EditorToolbar from './markdowneditor/EditorToolbar.vue'
import EditorTableBar from './markdowneditor/EditorTableBar.vue'
import EditorLinkDialog from './markdowneditor/EditorLinkDialog.vue'
import EditorImageDialog from './markdowneditor/EditorImageDialog.vue'
import EditorVideoDialog from './markdowneditor/EditorVideoDialog.vue'
import EditorBubbleMenu from './markdowneditor/EditorBubbleMenu.vue'

const modelValue = defineModel<string>({required: true})

const props = defineProps<{
  placeholder?: string
  /**
   * Which library the picture picker reaches into. Defaults to the station being worked in; the
   * administration passes `INSTANCE_MEDIA_SCOPE`, because a system notice is read in every station
   * and its pictures cannot come out of one of them.
   */
  mediaScope?: string
  /**
   * Tokens the stored markdown carries that this editor shows as nodes of their own, such as the
   * placeholders of a document template. Read once, when the editor is made.
   */
  tokens?: EditorTokens
  /**
   * The font families selected words can be set in, such as those a document template reaches. The
   * menu offers a font only where they are given. Read once, when the editor is made. Inside the
   * template editor the text and the words show in the families they print in, from the fonts it loads.
   */
  fonts?: readonly FontFamilyOption[]
}>()

const editorFonts = useInjectedEditorFonts()
const fontArea = useEditorFontAreaAttrs()
const areaAttrs = computed(() => fontArea?.value ?? {})

const { sessionInfo } = useSession()
const stationUid = computed(() => props.mediaScope ?? sessionInfo.value?.stationId ?? '')

const turndown = createMarkdownTurndown()
props.tokens?.extendTurndown(turndown)
if (props.fonts) extendTurndownWithTextFont(turndown)

const isUpdatingFromProp = ref(false)
const isInTable = ref(false)
const currentLinkUrl = ref('')
const isOnLink = ref(false)
const editorContainer = ref<HTMLElement | null>(null)

const showLinkDialog = ref(false)
const linkDialogPos = ref({ top: 0, left: 0 })
const linkInitialText = ref('')
const linkInitialUrl = ref('')

const showImageDialog = ref(false)
const imageDialogPos = ref({ top: 0, left: 0 })
const showMediaBrowser = ref(false)
const pendingImageAlt = ref('')

const showVideoDialog = ref(false)
const videoDialogPos = ref({ top: 0, left: 0 })

/**
 * The editor and the extensions it runs with.
 *
 * <p>The starter kit's own link and underline are switched off because both are registered below
 * with their own configuration, and two of either is a duplicate-extension warning. Tables are
 * resizable, which keeps column widths in a colgroup; the stylesheet lays tables out fixed, the
 * only layout under which a browser reads those widths rather than sizing columns by their content.
 */
const editor = useEditor({
  extensions: [
    StarterKit.configure({ heading: { levels: [1, 2, 3] }, link: false, underline: false }),
    Underline,
    Link.configure({ openOnClick: false, autolink: true, HTMLAttributes: { class: 'text-[var(--color-primary)] underline cursor-text' } }),
    Placeholder.configure({ placeholder: props.placeholder ?? '' }),
    Table.configure({ resizable: true }),
    TableRow, TableHeader, TableCell,
    Highlight.configure({ multicolor: true }),
    Youtube.configure({ inline: false }),
    ResizableImage, TextStyle, Color, TextSize, BlockAlign,
    ...(props.tokens?.extensions ?? []),
    ...(props.fonts ? [TextFont.configure({ shown: family => editorFonts?.shown(family) ?? false })] : []),
  ],
  content: '',
  editorProps: {
    handleClick(_view, _pos, event) {
      const t = event.target as HTMLElement
      if (t.tagName === 'A' || t.closest('a')) { event.preventDefault(); event.stopPropagation(); return true }
      return false
    },
    handleDOMEvents: {
      click(_view, event) {
        const t = event.target as HTMLElement
        if (t.tagName === 'A' || t.closest('a')) { event.preventDefault(); return true }
        return false
      },
    },
  },
  onSelectionUpdate: ({ editor: ed }) => { updateState(ed) },
  onTransaction: ({ editor: ed }) => { updateState(ed) },
  onUpdate: ({ editor: ed }) => {
    if (isUpdatingFromProp.value) return
    modelValue.value = turndown.turndown(ed.getHTML())
  },
})

function updateState(ed: { isActive: (n: string, a?: Record<string, unknown>) => boolean; getAttributes: (n: string) => Record<string, unknown> }) {
  isInTable.value = ed.isActive('table')
  isOnLink.value = ed.isActive('link')
  currentLinkUrl.value = (ed.getAttributes('link').href as string) ?? ''
}

async function setEditorContent(md: string) {
  if (!editor.value) return
  isUpdatingFromProp.value = true
  let html = renderMarkdown(props.tokens ? props.tokens.prepare(md) : md)
  html = html.replace(/<p>(<img [^>]*>)<\/p>/g, '$1')
  editor.value.commands.setContent(html, { emitUpdate: false })
  await nextTick()
  isUpdatingFromProp.value = false
}

onMounted(async () => { await nextTick(); if (modelValue.value) await setEditorContent(modelValue.value) })

watch(modelValue, async (md, oldMd) => {
  if (!editor.value || md === oldMd) return
  const cur = turndown.turndown(editor.value.getHTML())
  if (cur !== md) await setEditorContent(md)
})

onBeforeUnmount(() => { editor.value?.destroy() })

/**
 * Puts content in at the cursor, for a screen that offers things to insert beside the toolbar, such
 * as the placeholder picker of a document template.
 */
function insert(content: Content) {
  editor.value?.chain().focus().insertContent(content).run()
}

defineExpose({ insert })

function cursorPos() {
  if (!editor.value) return { top: 0, left: 0 }
  const { from } = editor.value.state.selection
  const coords = editor.value.view.coordsAtPos(from)
  const rect = editorContainer.value?.getBoundingClientRect()
  if (!rect) return { top: 0, left: 0 }
  return { top: coords.bottom - rect.top, left: coords.left - rect.left }
}

function openLinkDialog() {
  if (!editor.value) return
  const { from, to } = editor.value.state.selection
  const existingHref = editor.value.getAttributes('link').href || ''
  let text = ''
  if (editor.value.isActive('link')) {
    const resolved = editor.value.state.doc.resolve(from)
    const linkMark = resolved.marks().find(m => m.type.name === 'link')
    if (linkMark) {
      let s = from, e = to
      editor.value.state.doc.nodesBetween(Math.max(0, from - 200), Math.min(editor.value.state.doc.content.size, from + 200), (node, pos) => {
        if (node.isText && node.marks.some(m => m.type.name === 'link' && m.attrs.href === linkMark.attrs.href)) {
          if (pos < s) s = pos; if (pos + node.nodeSize > e) e = pos + node.nodeSize
        }
      })
      text = editor.value.state.doc.textBetween(s, e, '')
    }
  }
  if (!text) text = editor.value.state.doc.textBetween(from, to, '')
  linkInitialText.value = text
  linkInitialUrl.value = existingHref as string
  linkDialogPos.value = cursorPos()
  showLinkDialog.value = true
}

function applyLink(url: string, text: string) {
  if (!editor.value) return
  const u = url.trim()
  if (!u) { editor.value.chain().focus().extendMarkRange('link').unsetLink().run() }
  else if (text && text !== editor.value.state.doc.textBetween(editor.value.state.selection.from, editor.value.state.selection.to, '')) {
    editor.value.chain().focus().insertContent(`<a href="${u}">${text}</a>`).run()
  } else { editor.value.chain().focus().extendMarkRange('link').setLink({ href: u }).run() }
  showLinkDialog.value = false
}

function removeLink() {
  editor.value?.chain().focus().extendMarkRange('link').unsetLink().run()
  showLinkDialog.value = false
}

function openImageDialog() { imageDialogPos.value = cursorPos(); showImageDialog.value = true }

function insertImageUrl(url: string, alt: string) {
  editor.value?.chain().focus().setImage({ src: url, alt }).run()
  showImageDialog.value = false
}

/**
 * Hands over to the station media library: browse what is already there, search it, upload into
 * it, insert. Members without a content permission see only their own uploads, which is what lets
 * anyone put a picture into a ticket without opening the station's website assets to them.
 */
function browseMedia(alt: string) {
  pendingImageAlt.value = alt
  showImageDialog.value = false
  showMediaBrowser.value = true
}

function insertFromMedia(payload: { url: string }) {
  editor.value?.chain().focus().setImage({ src: payload.url, alt: pendingImageAlt.value }).run()
  pendingImageAlt.value = ''
}

function openVideoDialog() { videoDialogPos.value = cursorPos(); showVideoDialog.value = true }

function applyVideo(url: string) {
  if (!editor.value) return
  if (isYoutubeUrl(url)) {
    editor.value.chain().focus().setYoutubeVideo({ src: url }).run()
  } else {
    editor.value.chain().focus().insertContent(
        `<iframe src="${videoEmbedUrl(url.trim())}" width="560" height="315" frameborder="0" allowfullscreen></iframe>`,
    ).run()
  }
  showVideoDialog.value = false
}
</script>

<template>
  <div ref="editorContainer" class="markdown-editor rounded-lg border border-[var(--border)] bg-[var(--bg)] relative"
       v-bind="areaAttrs">
    <EditorToolbar
      :editor="editor"
      :fonts="fonts"
      @open-link="openLinkDialog"
      @open-image="openImageDialog"
      @open-video="openVideoDialog"
    />

    <EditorTableBar v-if="isInTable" :editor="editor" />

    <EditorLinkDialog
      v-if="showLinkDialog"
      :initial-text="linkInitialText"
      :initial-url="linkInitialUrl"
      :is-editing="isOnLink"
      :position="linkDialogPos"
      @apply="applyLink"
      @remove="removeLink"
      @cancel="showLinkDialog = false; editor?.chain().focus().run()"
    />

    <EditorImageDialog
      v-if="showImageDialog"
      :position="imageDialogPos"
      @insert-url="insertImageUrl"
      @browse="browseMedia"
      @cancel="showImageDialog = false; editor?.chain().focus().run()"
    />

    <MediaBrowseModal
      v-model:open="showMediaBrowser"
      :station-uid="stationUid"
      mime-prefix="image/"
      @pick="insertFromMedia"
    />

    <EditorVideoDialog
      v-if="showVideoDialog"
      :position="videoDialogPos"
      @apply="applyVideo"
      @cancel="showVideoDialog = false; editor?.chain().focus().run()"
    />

    <EditorBubbleMenu
      v-if="editor"
      :editor="editor"
      :is-on-link="isOnLink"
      :current-link-url="currentLinkUrl"
      :show-link-dialog="showLinkDialog"
      @open-link="openLinkDialog"
      @remove-link="removeLink"
    />

    <EditorContent :editor="editor" class="markdown-editor-content p-4 min-h-[300px] markdown-content focus:outline-none" />
  </div>
</template>

<style>
.markdown-editor-content .tiptap { outline: none; min-height: 280px; }
.markdown-editor-content .tiptap p.is-editor-empty:first-child::before { content: attr(data-placeholder); float: left; color: var(--text-muted); pointer-events: none; height: 0; }
.markdown-editor-content .tiptap table { border-collapse: collapse; table-layout: fixed; width: 100%; margin: 1em 0; }
.markdown-editor-content .tiptap th, .markdown-editor-content .tiptap td { border: 1px solid var(--border); padding: 0.4em 0.6em; vertical-align: top; overflow-wrap: break-word; }
.markdown-editor-content .tiptap th { font-weight: bold; background: var(--bg-accent); }
.markdown-editor-content .tiptap .selectedCell { background: color-mix(in srgb, var(--color-primary) 15%, transparent); }
.markdown-editor-content .tiptap mark { padding: 0.1em 0.2em; border-radius: 2px; }
.markdown-editor-content .tiptap img { max-width: 100%; height: auto; border-radius: 4px; margin: 0.5em 0; }
.markdown-editor-content .tiptap pre { background: var(--bg-accent); border: 1px solid var(--border); border-radius: 0.5rem; padding: 0.75rem 1rem; margin: 0.75em 0; overflow-x: auto; }
.markdown-editor-content .tiptap pre code { background: none; border: none; padding: 0; font-size: 0.875em; color: var(--text); font-family: ui-monospace, SFMono-Regular, 'SF Mono', Menlo, Consolas, monospace; }
.markdown-editor-content .tiptap code { background: var(--bg-accent); border: 1px solid var(--border); border-radius: 0.25rem; padding: 0.1em 0.3em; font-size: 0.875em; font-family: ui-monospace, SFMono-Regular, 'SF Mono', Menlo, Consolas, monospace; }
.markdown-editor-content .tiptap hr { border: none; border-top: 2px solid color-mix(in srgb, var(--text) 25%, transparent); margin: 1.5em 0; }
.markdown-editor-content .tiptap .text-font { text-decoration: underline dotted var(--primary); text-underline-offset: 3px; cursor: help; }
</style>

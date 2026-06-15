/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onBeforeUnmount, watch, nextTick, onMounted } from 'vue'
import { useEditor, EditorContent } from '@tiptap/vue-3'
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
import Image from '@tiptap/extension-image'
import { Color } from '@tiptap/extension-color'
import { TextStyle } from '@tiptap/extension-text-style'
import { VueNodeViewRenderer } from '@tiptap/vue-3'
import TurndownService from 'turndown'
import { marked } from 'marked'
import { uploadKbImage, kbImageUrl } from '@/api/knowledgeBase'
import { getItem } from '@/api/storage'
import ImageNodeView from './ImageNodeView.vue'
import EditorToolbar from './markdowneditor/EditorToolbar.vue'
import EditorTableBar from './markdowneditor/EditorTableBar.vue'
import EditorLinkDialog from './markdowneditor/EditorLinkDialog.vue'
import EditorImageDialog from './markdowneditor/EditorImageDialog.vue'
import EditorVideoDialog from './markdowneditor/EditorVideoDialog.vue'
import EditorBubbleMenu from './markdowneditor/EditorBubbleMenu.vue'

const props = defineProps<{
  modelValue: string
  placeholder?: string
  fileId?: number
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

// --- Turndown (HTML → Markdown) ---

const turndown = new TurndownService({ headingStyle: 'atx', codeBlockStyle: 'fenced', bulletListMarker: '-' })

turndown.addRule('strikethrough', { filter: ['del', 's'], replacement: (c) => `~~${c}~~` })
turndown.addRule('underline', { filter: ['u'], replacement: (c) => `<u>${c}</u>` })
turndown.addRule('highlight', {
  filter: ['mark'],
  replacement: (c, node) => {
    const el = node as HTMLElement
    const color = el.getAttribute('data-color') || el.style.backgroundColor
    if (color && color !== '#fef08a') return `<mark data-color="${color}" style="background-color: ${color}">${c}</mark>`
    return `==${c}==`
  },
})
turndown.addRule('coloredText', {
  filter: (node) => node.nodeName === 'SPAN' && !!(node as HTMLElement).style.color,
  replacement: (c, node) => {
    const color = (node as HTMLElement).style.color
    return color ? `<span style="color: ${color}">${c}</span>` : c
  },
})
turndown.addRule('image', {
  filter: 'img',
  replacement: (_c, node) => {
    const el = node as HTMLImageElement
    const alt = el.getAttribute('alt') || ''
    let src = el.getAttribute('src') || ''
    if (src.includes('/kb/images/')) {
      try { const u = new URL(src, window.location.origin); u.searchParams.delete('token'); u.searchParams.delete('stationId'); src = u.pathname + (u.search || '') } catch { /* keep */ }
    }
    const width = el.getAttribute('width') || ''
    if (width) {
      return `\n<img src="${src}" alt="${alt}" width="${width}" style="width: ${width}px" />\n`
    }
    return `![${alt}](${src})`
  },
})
turndown.addRule('youtube', {
  filter: (node) => {
    const el = node as HTMLElement
    return el.hasAttribute('data-youtube-video') || (el.tagName === 'IFRAME' && (el.getAttribute('src') ?? '').includes('youtube'))
  },
  replacement: (_c, node) => {
    const el = node as HTMLElement
    const iframe = el.tagName === 'IFRAME' ? el : el.querySelector('iframe')
    if (!iframe) return ''
    const src = iframe.getAttribute('src') || ''
    const match = src.match(/embed\/([a-zA-Z0-9_-]{11})/)
    if (match) return `\n<iframe width="560" height="315" src="https://www.youtube-nocookie.com/embed/${match[1]}" frameborder="0" allowfullscreen></iframe>\n`
    return `\n<iframe src="${src}" frameborder="0" allowfullscreen></iframe>\n`
  },
})

// --- Custom Image Extension (width only, NodeView) ---

const ResizableImage = Image.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      width: {
        default: null,
        parseHTML: (el: HTMLElement) => el.getAttribute('width') || el.style.width?.replace('px', '') || null,
        renderHTML: (attrs: Record<string, unknown>) => attrs.width ? { width: String(attrs.width), style: `width: ${attrs.width}px` } : {},
      },
    }
  },
  addNodeView() {
    return VueNodeViewRenderer(ImageNodeView)
  },
}).configure({ inline: false, allowBase64: false })

// --- Editor State ---

const isUpdatingFromProp = ref(false)
const isInTable = ref(false)
const currentLinkUrl = ref('')
const isOnLink = ref(false)
const editorContainer = ref<HTMLElement | null>(null)

// Dialog state
const showLinkDialog = ref(false)
const linkDialogPos = ref({ top: 0, left: 0 })
const linkInitialText = ref('')
const linkInitialUrl = ref('')

const showImageDialog = ref(false)
const imageDialogPos = ref({ top: 0, left: 0 })

const showVideoDialog = ref(false)
const videoDialogPos = ref({ top: 0, left: 0 })

// --- Editor ---

const editor = useEditor({
  extensions: [
    // StarterKit bundles Link and Underline; we register them explicitly with custom config,
    // so disable the bundled versions to avoid the "duplicate extension names" warning.
    StarterKit.configure({ heading: { levels: [1, 2, 3] }, link: false, underline: false }),
    Underline,
    Link.configure({ openOnClick: false, autolink: true, HTMLAttributes: { class: 'text-[var(--primary)] underline cursor-text' } }),
    Placeholder.configure({ placeholder: props.placeholder ?? '' }),
    Table.configure({ resizable: true }),
    TableRow, TableHeader, TableCell,
    Highlight.configure({ multicolor: true }),
    Youtube.configure({ inline: false }),
    ResizableImage, TextStyle, Color,
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
    emit('update:modelValue', turndown.turndown(ed.getHTML()))
  },
})

function updateState(ed: { isActive: (n: string, a?: Record<string, unknown>) => boolean; getAttributes: (n: string) => Record<string, unknown> }) {
  isInTable.value = ed.isActive('table')
  isOnLink.value = ed.isActive('link')
  currentLinkUrl.value = (ed.getAttributes('link').href as string) ?? ''
}

// --- Content Sync ---

function addKbImageAuth(html: string): string {
  return html.replace(/src="([^"]*\/kb\/images\/[^"]*)"/g, (_m, url) => {
    try {
      const p = new URL(url, window.location.origin)
      if (!p.searchParams.has('token')) { p.searchParams.set('token', getItem('session_token') ?? ''); p.searchParams.set('stationId', getItem('station_id') ?? '') }
      return `src="${p.toString()}"`
    } catch { return `src="${url}"` }
  })
}

async function setEditorContent(md: string) {
  if (!editor.value) return
  isUpdatingFromProp.value = true
  let html = await marked.parse(md || '')
  html = addKbImageAuth(html)
  html = html.replace(/<p>(<img [^>]*>)<\/p>/g, '$1')
  editor.value.commands.setContent(html, { emitUpdate: false })
  await nextTick()
  isUpdatingFromProp.value = false
}

onMounted(async () => { await nextTick(); if (props.modelValue) await setEditorContent(props.modelValue) })

watch(() => props.modelValue, async (md, oldMd) => {
  if (!editor.value || md === oldMd) return
  const cur = turndown.turndown(editor.value.getHTML())
  if (cur !== md) await setEditorContent(md)
})

onBeforeUnmount(() => { editor.value?.destroy() })

// --- Dialog Helpers ---

function cursorPos() {
  if (!editor.value) return { top: 0, left: 0 }
  const { from } = editor.value.state.selection
  const coords = editor.value.view.coordsAtPos(from)
  const rect = editorContainer.value?.getBoundingClientRect()
  if (!rect) return { top: 0, left: 0 }
  return { top: coords.bottom - rect.top + 4, left: Math.max(0, Math.min(coords.left - rect.left, rect.width - 320)) }
}

function openLinkDialog() {
  if (!editor.value) return
  const { from, to } = editor.value.state.selection
  const existingHref = editor.value.getAttributes('link').href || ''
  // Get full link text if on existing link
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

async function uploadImage(file: File, alt: string) {
  if (!props.fileId || !editor.value) return
  try {
    const res = await uploadKbImage(props.fileId, file)
    editor.value.chain().focus().setImage({ src: kbImageUrl(res.imageId), alt }).run()
  } catch { /* failed */ }
  showImageDialog.value = false
}

function openVideoDialog() { videoDialogPos.value = cursorPos(); showVideoDialog.value = true }

function toEmbedUrl(url: string): string {
  const yt = url.match(/(?:youtube\.com\/watch\?v=|youtu\.be\/|youtube\.com\/embed\/)([a-zA-Z0-9_-]{11})/)
  if (yt) return `https://www.youtube-nocookie.com/embed/${yt[1]}`
  const vm = url.match(/vimeo\.com\/(\d+)/)
  if (vm) return `https://player.vimeo.com/video/${vm[1]}`
  const pt = url.match(/(https?:\/\/[^/]+)\/videos\/watch\/(.+)/)
  if (pt) return `${pt[1]}/videos/embed/${pt[2]}`
  const dm = url.match(/(?:dailymotion\.com\/video\/|dai\.ly\/)([a-zA-Z0-9]+)/)
  if (dm) return `https://www.dailymotion.com/embed/video/${dm[1]}`
  return url
}

function applyVideo(url: string) {
  if (!editor.value) return
  if (/youtube|youtu\.be/i.test(url)) { editor.value.chain().focus().setYoutubeVideo({ src: url }).run() }
  else { editor.value.chain().focus().insertContent(`<iframe src="${toEmbedUrl(url.trim())}" width="560" height="315" frameborder="0" allowfullscreen></iframe>`).run() }
  showVideoDialog.value = false
}
</script>

<template>
  <div ref="editorContainer" class="markdown-editor rounded-lg border border-[var(--border)] bg-[var(--bg)] relative">
    <EditorToolbar
      :editor="editor"
      :file-id="fileId"
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
      :file-id="fileId"
      :position="imageDialogPos"
      @insert-url="insertImageUrl"
      @upload-file="uploadImage"
      @cancel="showImageDialog = false; editor?.chain().focus().run()"
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
.markdown-editor-content .tiptap table { border-collapse: collapse; width: 100%; margin: 1em 0; }
.markdown-editor-content .tiptap th, .markdown-editor-content .tiptap td { border: 1px solid var(--border); padding: 0.4em 0.6em; min-width: 80px; vertical-align: top; }
.markdown-editor-content .tiptap th { font-weight: bold; background: var(--bg-accent); }
.markdown-editor-content .tiptap .selectedCell { background: color-mix(in srgb, var(--primary) 15%, transparent); }
.markdown-editor-content .tiptap mark { padding: 0.1em 0.2em; border-radius: 2px; }
.markdown-editor-content .tiptap img { max-width: 100%; height: auto; border-radius: 4px; margin: 0.5em 0; }
.markdown-editor-content .tiptap pre { background: var(--bg-accent); border: 1px solid var(--border); border-radius: 0.5rem; padding: 0.75rem 1rem; margin: 0.75em 0; overflow-x: auto; }
.markdown-editor-content .tiptap pre code { background: none; border: none; padding: 0; font-size: 0.875em; color: var(--text); font-family: ui-monospace, SFMono-Regular, 'SF Mono', Menlo, Consolas, monospace; }
.markdown-editor-content .tiptap code { background: var(--bg-accent); border: 1px solid var(--border); border-radius: 0.25rem; padding: 0.1em 0.3em; font-size: 0.875em; font-family: ui-monospace, SFMono-Regular, 'SF Mono', Menlo, Consolas, monospace; }
.markdown-editor-content .tiptap hr { border: none; border-top: 2px solid color-mix(in srgb, var(--text) 25%, transparent); margin: 1.5em 0; }
</style>

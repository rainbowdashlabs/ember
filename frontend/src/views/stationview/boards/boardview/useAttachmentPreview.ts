/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { nextTick, ref, watch, type Ref } from 'vue'
import { boards } from '@/api'
import type { BoardTicketAttachment } from '@/api/boards'
import { useAuthImages } from '@/composables/useAuthImage'
import { downloadAuthed } from '@/util/downloadAuthed'

/**
 * Previewing a ticket's attachments without leaving the ticket.
 *
 * Attachments sit behind the session, so images cannot be pointed at directly - they are fetched
 * as blobs and cached by attachment id, which serves both the thumbnails and the overlay. Only
 * images, PDFs and CSV files can be shown; anything else is offered as a download.
 *
 * @param boardKey     the board the ticket belongs to
 * @param ticketNumber the ticket the attachments hang off
 * @param attachments  the attachment list, watched so thumbnails follow uploads and deletions
 */
export function useAttachmentPreview(
  boardKey: Ref<string>,
  ticketNumber: Ref<number>,
  attachments: Ref<BoardTicketAttachment[]>,
) {
  const {srcFor, load: loadBlob} = useAuthImages<number>()

  const overlayRef = ref<HTMLElement | null>(null)
  const url = ref<string | null>(null)
  const name = ref('')
  const csv = ref<string[][] | null>(null)
  const shown = ref(false)
  const index = ref(0)

  function isImage(contentType: string) {
    return contentType.startsWith('image/')
  }

  function isPdf(contentType: string) {
    return contentType === 'application/pdf'
  }

  function isCsv(fileName: string) {
    return fileName.toLowerCase().endsWith('.csv')
  }

  function canPreview(att: BoardTicketAttachment) {
    return isImage(att.contentType) || isPdf(att.contentType) || isCsv(att.originalName)
  }

  function fileIcon(att: BoardTicketAttachment): string[] {
    if (isImage(att.contentType)) return ['fas', 'image']
    if (isPdf(att.contentType)) return ['fas', 'file-pdf']
    if (isCsv(att.originalName) || att.contentType.startsWith('text/')) return ['fas', 'file-lines']
    return ['fas', 'file']
  }

  function attachmentUrl(attachmentId: number): string {
    return `/boards/${boardKey.value}/tickets/${ticketNumber.value}/attachments/${attachmentId}/download`
  }

  async function loadThumbnails() {
    for (const att of attachments.value) {
      if (isImage(att.contentType) && !srcFor(att.id)) {
        await loadBlob(att.id, attachmentUrl(att.id))
      }
    }
  }

  watch(attachments, loadThumbnails, {immediate: true})
  watch(shown, (visible) => {
    if (visible) nextTick(() => overlayRef.value?.focus())
  })

  /**
   * Splits a CSV into rows and cells, accepting both comma and semicolon separators because
   * spreadsheet exports in this locale use either.
   */
  function parseCsv(text: string): string[][] {
    return text.trim().split('\n').map(line => {
      const cols: string[] = []
      let current = ''
      let inQuote = false
      for (const ch of line) {
        if (ch === '"') inQuote = !inQuote
        else if ((ch === ',' || ch === ';') && !inQuote) { cols.push(current); current = '' }
        else current += ch
      }
      cols.push(current)
      return cols
    })
  }

  async function load(att: BoardTicketAttachment) {
    name.value = att.originalName
    csv.value = null
    url.value = null
    shown.value = true
    if (isImage(att.contentType) || isPdf(att.contentType)) {
      if (!srcFor(att.id)) await loadBlob(att.id, attachmentUrl(att.id))
      url.value = srcFor(att.id)
      return
    }
    if (isCsv(att.originalName)) {
      csv.value = parseCsv(
        await boards.getAttachmentText(boardKey.value, ticketNumber.value, att.id))
    }
  }

  async function open(att: BoardTicketAttachment) {
    index.value = attachments.value.findIndex(a => a.id === att.id)
    await load(att)
  }

  function showAt(position: number) {
    const att = attachments.value[position]
    if (!att) return
    index.value = position
    load(att)
  }

  function previous() {
    if (index.value > 0) showAt(index.value - 1)
  }

  function next() {
    if (index.value < attachments.value.length - 1) showAt(index.value + 1)
  }

  async function download(att: BoardTicketAttachment) {
    await downloadAuthed(attachmentUrl(att.id), att.originalName)
  }

  async function downloadCurrent() {
    const att = attachments.value[index.value]
    if (att) await download(att)
  }

  return {
    srcFor,
    overlayRef,
    url,
    name,
    csv,
    shown,
    index,
    isImage,
    isPdf,
    isCsv,
    canPreview,
    fileIcon,
    open,
    previous,
    next,
    download,
    downloadCurrent,
  }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {MemberCompletion} from '@/api/stationMembers'

function escapeAttribute(value: string): string {
  return value.replace(/"/g, '&quot;')
}

/**
 * Renders raw mention markup as the HTML shown inside the editor.
 *
 * Three markup generations are understood: bulk mentions (`@[TYPE:Name:id]`),
 * member mentions (`@[stationUid/memberUid:Name]`) and the legacy numeric
 * member form (`@[id:Name]`).
 */
export function rawToHtml(raw: string, members: MemberCompletion[]): string {
  if (!raw) return ''
  const escaped = raw
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
  let result = escaped.replace(/@\[(GROUP|EVENT|REGISTERED|DECLINED):([^:]+):(\d+)]/g, (_match, type, name, id) => {
    return `<span contenteditable="false" data-mention-type="${type}" data-mention-name="${escapeAttribute(name)}" data-mention-id="${id}" class="mention-chip bulk-mention">@${name}</span>`
  })
  result = result.replace(/@\[([^/]+)\/([^:]+):([^\]]+)]/g, (_match, stationUid, memberUid, name) => {
    const member = members.find(m => m.memberUid === memberUid)
    const displayName = member?.name?.trim() || name
    return `<span contenteditable="false" data-mention-station="${stationUid}" data-mention-member="${memberUid}" data-mention-name="${escapeAttribute(name)}" class="mention-chip">@${displayName}</span>`
  })
  result = result.replace(/@\[(\d+):([^\]]+)]/g, (_match, id, name) => {
    const member = members.find(m => m.id === parseInt(id))
    const displayName = member?.name?.trim() || name
    return `<span contenteditable="false" data-mention-station="" data-mention-member="" data-mention-name="${escapeAttribute(name)}" class="mention-chip">@${displayName}</span>`
  })
  return result.replace(/\n/g, '<br>')
}

/**
 * Serialises the editor DOM back into raw mention markup.
 *
 * Chrome and Edge wrap every line typed after Enter in a block element while
 * Firefox emits a line break element, so block boundaries are also treated as
 * a newline to keep the line breaks the user typed.
 */
export function htmlToRaw(el: HTMLElement): string {
  let result = ''
  for (const node of el.childNodes) {
    if (node.nodeType === Node.TEXT_NODE) {
      result += node.textContent ?? ''
    } else if (node.nodeType === Node.ELEMENT_NODE) {
      const element = node as HTMLElement
      if (element.dataset.mentionType) {
        const type = element.dataset.mentionType
        const name = element.dataset.mentionName
        const id = element.dataset.mentionId
        result += `@[${type}:${name}:${id}]`
      } else if (element.dataset.mentionMember) {
        const stationUid = element.dataset.mentionStation
        const memberUid = element.dataset.mentionMember
        const name = element.dataset.mentionName
        result += `@[${stationUid}/${memberUid}:${name}]`
      } else if (element.tagName === 'BR') {
        result += '\n'
      } else if (element.tagName === 'DIV' || element.tagName === 'P') {
        if (result && !result.endsWith('\n')) result += '\n'
        result += htmlToRaw(element)
      } else {
        result += htmlToRaw(element)
      }
    }
  }
  return result
}

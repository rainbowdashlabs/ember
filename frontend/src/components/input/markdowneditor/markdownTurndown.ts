/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import TurndownService from 'turndown'

/**
 * Converts the editor's HTML back into the Markdown that is stored.
 *
 * Everything Markdown itself can express is written as Markdown; the rest is kept as the inline
 * HTML the renderer already accepts, so a formatting choice the user made survives a round trip
 * instead of being silently dropped. Highlights are the one hybrid: the default yellow becomes
 * `==text==`, any other colour has to stay HTML to carry the colour.
 */
export function createMarkdownTurndown(): TurndownService {
  const turndown = new TurndownService({
    headingStyle: 'atx',
    codeBlockStyle: 'fenced',
    bulletListMarker: '-',
  })

  turndown.addRule('strikethrough', {filter: ['del', 's'], replacement: (c) => `~~${c}~~`})
  turndown.addRule('underline', {filter: ['u'], replacement: (c) => `<u>${c}</u>`})

  turndown.addRule('highlight', {
    filter: ['mark'],
    replacement: (c, node) => {
      const el = node as HTMLElement
      const color = el.getAttribute('data-color') || el.style.backgroundColor
      if (color && color !== '#fef08a') {
        return `<mark data-color="${color}" style="background-color: ${color}">${c}</mark>`
      }
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
      const src = el.getAttribute('src') || ''
      const width = el.getAttribute('width') || ''
      if (width) {
        return `\n<img src="${src}" alt="${alt}" width="${width}" style="width: ${width}px" />\n`
      }
      return `![${alt}](${src})`
    },
  })

  turndown.addRule('table', {
    filter: 'table',
    replacement: (_content, node) => asMarkdownTable(turndown, node as HTMLElement),
  })

  turndown.addRule('youtube', {
    filter: (node) => {
      const el = node as HTMLElement
      return el.hasAttribute('data-youtube-video')
        || (el.tagName === 'IFRAME' && (el.getAttribute('src') ?? '').includes('youtube'))
    },
    replacement: (_c, node) => {
      const el = node as HTMLElement
      const iframe = el.tagName === 'IFRAME' ? el : el.querySelector('iframe')
      if (!iframe) return ''
      const src = iframe.getAttribute('src') || ''
      const match = src.match(/embed\/([a-zA-Z0-9_-]{11})/)
      if (match) {
        return `\n<iframe width="560" height="315" src="https://www.youtube-nocookie.com/embed/${match[1]}" frameborder="0" allowfullscreen></iframe>\n`
      }
      return `\n<iframe src="${src}" frameborder="0" allowfullscreen></iframe>\n`
    },
  })

  return turndown
}

/**
 * Writes a table as a table.
 *
 * <p>Turndown knows nothing about tables and, left alone, throws the tags away and keeps the words
 * inside them, so a table written in the editor came back as a run of plain text and the rows were
 * gone for good. Markdown can express a table, so it is written as one.
 *
 * <p>Markdown's table needs a header row: where the table has none, an empty one is written so the
 * rows below it survive, which is the point of the exercise.
 */
function asMarkdownTable(turndown: TurndownService, table: HTMLElement): string {
    const rows = [...table.querySelectorAll('tr')]
        .map(row => [...row.querySelectorAll('th, td')].map(cell => cellText(turndown, cell)))
        .filter(cells => cells.length > 0)
    if (rows.length === 0) return ''

    const width = Math.max(...rows.map(cells => cells.length))
    const hasHeader = table.querySelector('th') !== null
    const header = hasHeader ? rows[0]! : Array<string>(width).fill('')
    const body = hasHeader ? rows.slice(1) : rows

    const lines = [line(header, width), line(Array<string>(width).fill('---'), width)]
    for (const cells of body) lines.push(line(cells, width))
    return `\n\n${lines.join('\n')}\n\n`
}

/**
 * One cell, written so that it stays one cell.
 *
 * <p>A pipe would end the cell early and a line break would end the row, so both are made harmless.
 * What is inside is still converted, because a bold word in a cell is worth keeping.
 */
function cellText(turndown: TurndownService, cell: Element): string {
    return turndown
        .turndown(cell.innerHTML)
        .replace(/\|/g, '\\|')
        .replace(/\s*\n\s*/g, ' ')
        .trim()
}

/** A row, padded to the width of the widest one so the columns line up down the whole table. */
function line(cells: string[], width: number): string {
    const padded = [...cells]
    while (padded.length < width) padded.push('')
    return `| ${padded.join(' | ')} |`
}

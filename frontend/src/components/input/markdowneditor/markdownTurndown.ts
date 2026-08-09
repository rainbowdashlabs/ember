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

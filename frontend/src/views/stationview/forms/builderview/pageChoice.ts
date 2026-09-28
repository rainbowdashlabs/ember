/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type { Translate } from '@/util/failure'
import type { PageDraft } from './types'

/** A page as a menu or a list to pick from offers it: where it stands and what it is called there. */
export interface PageChoice {
  index: number
  key: string
  label: string
}

/** What a page is called wherever one is picked: its number, and its title where it has one. */
export function pageLabel(page: PageDraft, index: number, t: Translate): string {
  const title = page.title.trim()
  return title
    ? t('forms.pages.numberedTitle', { number: index + 1, title })
    : t('forms.pages.number', { number: index + 1 })
}

/** Every page as a choice, the one at `except` left out. */
export function pageChoices(pages: readonly PageDraft[], t: Translate, except = -1): PageChoice[] {
  return pages
    .map((page, index) => ({ index, key: page.key, label: pageLabel(page, index, t) }))
    .filter(choice => choice.index !== except)
}

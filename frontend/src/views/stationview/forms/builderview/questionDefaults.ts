/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { FormQuestionType, MultiLimitType, RatingIcon, type FormQuestionConfig } from '@/api/generated/schema'
import { blankOption, optionsOf } from '@/util/formOptions'

const MULTI_LIMIT_TYPES: readonly MultiLimitType[] = Object.values(MultiLimitType)
const RATING_ICONS: readonly RatingIcon[] = Object.values(RatingIcon)

/** The settings a question of the given kind starts with, one empty option included where it has options. */
export function defaultConfig(type: FormQuestionType): Record<string, unknown> {
  switch (type) {
    case FormQuestionType.CHOICE:
      return { multiSelect: false, dropdown: false, allowOther: false, options: [blankOption([])], multiLimitType: 'NONE', multiLimit: null }
    case FormQuestionType.TEXT: return { longAnswer: false }
    case FormQuestionType.RATING: return { scale: 5, icon: 'STAR' }
    case FormQuestionType.DATE: return {}
    case FormQuestionType.RANKING: return { options: [blankOption([])] }
    case FormQuestionType.LIKERT: return { statements: [blankOption([])], scaleMin: 1, scaleMax: 5, scaleLabels: [] }
  }
}

function flag(value: unknown): boolean | undefined {
  return typeof value === 'boolean' ? value : undefined
}

function whole(value: unknown): number | undefined {
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined
}

function oneOf<T extends string>(allowed: readonly T[], value: unknown): T | undefined {
  return allowed.find(candidate => candidate === value)
}

function texts(value: unknown): string[] | undefined {
  return Array.isArray(value) ? value.filter((entry): entry is string => typeof entry === 'string') : undefined
}

/**
 * The settings of a question in the shape the server reads them: only the settings its kind has,
 * marked with that kind. The editor keeps whatever its fields wrote; the server refuses a setting it
 * does not know, so what is sent is picked out here.
 *
 * @param type   the kind of question
 * @param config what the editor holds for it
 */
export function questionConfigOf(type: FormQuestionType, config: Record<string, unknown>): FormQuestionConfig {
  switch (type) {
    case FormQuestionType.CHOICE:
      return {
        questionType: type,
        options: optionsOf(config),
        multiSelect: flag(config.multiSelect),
        dropdown: flag(config.dropdown),
        allowOther: flag(config.allowOther),
        multiLimitType: oneOf(MULTI_LIMIT_TYPES, config.multiLimitType),
        multiLimit: whole(config.multiLimit),
      }
    case FormQuestionType.TEXT: return { questionType: type, longAnswer: flag(config.longAnswer) }
    case FormQuestionType.RATING:
      return { questionType: type, scale: whole(config.scale), icon: oneOf(RATING_ICONS, config.icon) }
    case FormQuestionType.DATE: return { questionType: type }
    case FormQuestionType.RANKING: return { questionType: type, options: optionsOf(config) }
    case FormQuestionType.LIKERT:
      return {
        questionType: type,
        statements: optionsOf(config, 'statements'),
        scaleMin: whole(config.scaleMin),
        scaleMax: whole(config.scaleMax),
        scaleLabels: texts(config.scaleLabels),
      }
  }
}

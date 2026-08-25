/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {QuizQuestionTypes, type QuizQuestionTypeName} from '@/api/quiz'

/**
 * One entry of the format reference. The wizard and the help centre render the same list, so what
 * a field means is written down once.
 *
 * @property key the field or column as it appears in a file
 * @property i18n the key under `quiz.format.fields` holding the sentence explaining it
 * @property required whether a file without it is refused
 */
export interface FormatField {
    key: string
    i18n: string
    required: boolean
}

/** The columns a sheet may carry. Everything but the question text is optional. */
export const CSV_COLUMNS: FormatField[] = [
    {key: 'Frage', i18n: 'question', required: true},
    {key: 'Antwort', i18n: 'answer', required: false},
    {key: 'Falsch', i18n: 'distractors', required: false},
    {key: 'Kategorie', i18n: 'category', required: false},
    {key: 'Typ', i18n: 'type', required: false},
    {key: 'Punkte', i18n: 'points', required: false},
    {key: 'Hinweis', i18n: 'description', required: false},
    {key: 'Bild', i18n: 'image', required: false},
    {key: 'ProAntwort', i18n: 'pointsPerCorrect', required: false},
    {key: 'Anzahl', i18n: 'requiredCount', required: false},
    {key: 'Geordnet', i18n: 'orderedRequired', required: false},
]

/** The catalog block of a catalog file. */
export const JSON_CATALOG_FIELDS: FormatField[] = [
    {key: 'name', i18n: 'catalogName', required: true},
    {key: 'description', i18n: 'catalogDescription', required: false},
    {key: 'trainingEnabled', i18n: 'trainingEnabled', required: false},
    {key: 'metadata.language', i18n: 'language', required: false},
    {key: 'metadata.source', i18n: 'source', required: false},
    {key: 'metadata.author', i18n: 'author', required: false},
    {key: 'metadata.license', i18n: 'license', required: false},
]

/** One entry of the categories list. */
export const JSON_CATEGORY_FIELDS: FormatField[] = [
    {key: 'key', i18n: 'categoryKey', required: true},
    {key: 'name', i18n: 'categoryName', required: true},
    {key: 'description', i18n: 'categoryDescription', required: false},
    {key: 'position', i18n: 'categoryPosition', required: false},
]

/** One entry of the questions list. */
export const JSON_QUESTION_FIELDS: FormatField[] = [
    {key: 'title', i18n: 'title', required: true},
    {key: 'quizQuestionType', i18n: 'questionType', required: true},
    {key: 'config', i18n: 'config', required: true},
    {key: 'categoryKey', i18n: 'questionCategoryKey', required: false},
    {key: 'description', i18n: 'description', required: false},
    {key: 'imageUrl', i18n: 'image', required: false},
    {key: 'points', i18n: 'points', required: false},
    {key: 'autoPoints', i18n: 'autoPoints', required: false},
    {key: 'position', i18n: 'position', required: false},
]

/**
 * What each question type expects, as the answers look in a sheet and as the config looks in a
 * catalog file. Every type Ember has appears here, and a test on the shipped examples keeps them
 * from drifting apart.
 */
export interface QuestionTypeFormat {
    type: QuizQuestionTypeName
    spellings: string
    csvAnswer: string
    config: string
}

export const QUESTION_TYPE_FORMATS: QuestionTypeFormat[] = [
    {
        type: QuizQuestionTypes.MULTIPLE_CHOICE,
        spellings: 'MC, Multiple Choice, multiple_choice',
        csvAnswer: 'Brandklasse F',
        config: '{"options": [{"text": "Brandklasse F", "correct": true}], "pointsPerCorrect": 2}',
    },
    {
        type: QuizQuestionTypes.TRUE_FALSE,
        spellings: 'TF, wahr-falsch, true_false',
        csvAnswer: 'ja / nein',
        config: '{"correctAnswer": false}',
    },
    {
        type: QuizQuestionTypes.FREE_ANSWER,
        spellings: 'Freitext, FREE, free_answer',
        csvAnswer: 'Unfallverhütungsvorschrift',
        config: '{"answers": ["Unfallverhütungsvorschrift"], "lines": 3}',
    },
    {
        type: QuizQuestionTypes.FILL_IN_THE_BLANK,
        spellings: 'Lückentext, fill_blank',
        csvAnswer: '15 Meter',
        config: '{"text": "…", "answers": ["15 Meter"], "distractors": ["20 Meter"], "useDropdown": true}',
    },
    {
        type: QuizQuestionTypes.CONNECT,
        spellings: 'Zuordnung, connect',
        csvAnswer: 'LF=Löschgruppenfahrzeug;DLK=Drehleiter mit Korb',
        config: '{"pairs": [{"left": "LF", "right": "Löschgruppenfahrzeug"}]}',
    },
    {
        type: QuizQuestionTypes.ORDERING,
        spellings: 'Reihenfolge, ordering',
        csvAnswer: 'Absichern;Notruf;Erste Hilfe',
        config: '{"items": ["Absichern", "Notruf", "Erste Hilfe"]}',
    },
    {
        type: QuizQuestionTypes.ENUMERATION,
        spellings: 'Aufzählung, enumeration',
        csvAnswer: 'Sauerstoff;Wärme;brennbarer Stoff',
        config: '{"answers": ["Sauerstoff", "Wärme"], "requiredCount": 3, "orderedRequired": false}',
    },
    {
        type: QuizQuestionTypes.IMAGE_TEXT,
        spellings: 'IMAGE_TEXT',
        csvAnswer: 'Verteiler',
        config: '{"imageUrl": "https://…/verteiler.png", "answer": "Verteiler"}',
    },
]

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {FieldSettings} from '@/api/profileFields'
import type {FieldType} from '@/api/generated/schema'
import {FieldWidths, type FieldWidthName} from '@/components/profilefields/fieldLayout'
import type {PronounOffer} from './fieldmodal/genderPronouns'

const {HALF, THIRD} = FieldWidths

/**
 * One question a template writes down.
 *
 * <p>Whether an answer is expected and whether only the member management may write it belong to the
 * field itself, not to its settings: the server reads the settings strictly and refuses a key they do
 * not name.
 */
export interface TemplateField {
  name: string
  fieldType: FieldType
  config: FieldSettings
  required?: boolean
  readonly?: boolean
  /** The name of a yes/no field of the same template this one is only shown behind. */
  shownIf?: string
  /** How much of a row it takes, so the fields of a template that belong on one line stand on one. */
  width?: FieldWidthName
}

/**
 * Built-in profile field presets shown on the members config view.
 */
export interface FieldTemplate {
  name: string
  icon: string
  fields: TemplateField[]
}

/** The templates of what is known about a member, offered before the gender. */
const MEMBER_DATA_TEMPLATES: readonly FieldTemplate[] = [
  {
    name: 'Adresse', icon: 'house', fields: [
      {name: 'Straße', fieldType: 'TEXT', config: {}, required: true, width: HALF},
      {name: 'Hausnummer', fieldType: 'TEXT', config: {}, required: true, width: HALF},
      {name: 'Postleitzahl', fieldType: 'TEXT', config: {}, required: true, width: HALF},
      {name: 'Ort', fieldType: 'TEXT', config: {}, required: true, width: HALF},
    ],
  },
  {
    name: 'Geburtsdatum', icon: 'calendar-plus', fields: [
      {name: 'Geburtsdatum', fieldType: 'BIRTH_DATE', config: {}, required: true, readonly: true},
    ],
  },
  {
    name: 'Festnetz', icon: 'phone', fields: [
      {name: 'Festnetznummer', fieldType: 'TEXT', config: {notifyOnChange: true}},
    ],
  },
  {
    name: 'Mobilnummer', icon: 'mobile-screen', fields: [
      {name: 'Mobilnummer', fieldType: 'TEXT', config: {notifyOnChange: true}},
    ],
  },
  {
    name: 'Notfallkontakt', icon: 'triangle-exclamation', fields: [
      {name: 'Notfallkontakt Name', fieldType: 'TEXT', config: {}, required: true, width: HALF},
      {name: 'Notfallkontakt Telefon', fieldType: 'TEXT', config: {}, required: true, width: HALF},
    ],
  },
  {
    name: 'Führerschein', icon: 'id-card', fields: [
      {name: 'Führerscheinklasse', fieldType: 'TEXT', config: {}, width: HALF},
      {
        name: 'Führerschein gültig bis',
        fieldType: 'EXPIRY_DATE',
        config: {warnFromDays: 60, reminderDays: [60, 14]},
        width: HALF,
      },
    ],
  },
  {
    name: 'JuLeiCa', icon: 'id-card', fields: [
      {name: 'JuLeiCa', fieldType: 'BOOLEAN', config: {}, readonly: true, width: HALF},
      {
        name: 'JuLeiCa Ablaufdatum',
        fieldType: 'EXPIRY_DATE',
        config: {warnFromDays: 90, reminderDays: [90, 30]},
        readonly: true,
        shownIf: 'JuLeiCa',
        width: HALF,
      },
    ],
  },
  {
    name: 'Beitrittsdatum', icon: 'calendar-plus', fields: [
      {name: 'Beitrittsdatum', fieldType: 'DATE', config: {}, readonly: true},
    ],
  },
  {
    name: 'Personalnummer', icon: 'hashtag', fields: [
      {name: 'Personalnummer', fieldType: 'TEXT', config: {}, readonly: true},
    ],
  },
]

/** The templates of qualifications, offered after the gender. */
const QUALIFICATION_TEMPLATES: readonly FieldTemplate[] = [
  {
    name: 'Jugendflamme', icon: 'fire', fields: [
      {
        name: 'Jugendflamme',
        fieldType: 'CHOICE',
        config: {options: ['Keine', 'Jugendflamme 1', 'Jugendflamme 2', 'Jugendflamme 3']},
        readonly: true,
        width: HALF,
      },
      {name: 'Jugendflamme Datum', fieldType: 'DATE', config: {}, readonly: true, width: HALF},
    ],
  },
  {
    name: 'Leistungsspange', icon: 'medal', fields: [
      {name: 'Leistungsspange', fieldType: 'BOOLEAN', config: {}, readonly: true, width: HALF},
      {
        name: 'Leistungsspange Datum',
        fieldType: 'DATE',
        config: {},
        readonly: true,
        shownIf: 'Leistungsspange',
        width: HALF,
      },
    ],
  },
  {
    name: 'Erste Hilfe Kurs', icon: 'heart', fields: [
      {name: 'Erste Hilfe Kurs Datum', fieldType: 'DATE', config: {}, readonly: true, width: THIRD},
      {name: 'Erste Hilfe Kurs Stunden', fieldType: 'NUMBER', config: {}, readonly: true, width: THIRD},
      {
        name: 'Erste Hilfe gültig bis',
        fieldType: 'EXPIRY_DATE',
        config: {warnFromDays: 90, reminderDays: [90, 30]},
        readonly: true,
        width: THIRD,
      },
    ],
  },
  {
    name: 'TS Maschinist', icon: 'gears', fields: [
      {name: 'TS Maschinist Datum', fieldType: 'DATE', config: {}, readonly: true},
    ],
  },
]

/**
 * The gender template, whose two predefined answers carry their pronouns.
 *
 * @param presets the two predefined answers as the server names them
 */
function genderTemplate(presets: PronounOffer['presets']): FieldTemplate {
  return {
    name: 'Geschlecht', icon: 'rainbow', fields: [
      {
        name: 'Geschlecht',
        fieldType: 'GENDER',
        config: {
          options: ['Männlich', 'Weiblich', 'Divers', 'Nicht-binär', 'Andere'],
          pronouns: {Männlich: presets.MALE, Weiblich: presets.FEMALE},
        },
        readonly: true,
      },
    ],
  }
}

/**
 * Default templates offered as quick-add buttons in the members config view.
 *
 * @param presets the two predefined answers of a gender field as the server names them, which the
 *                gender template carries
 */
export function fieldTemplates(presets: PronounOffer['presets']): FieldTemplate[] {
  return [...MEMBER_DATA_TEMPLATES, genderTemplate(presets), ...QUALIFICATION_TEMPLATES]
}

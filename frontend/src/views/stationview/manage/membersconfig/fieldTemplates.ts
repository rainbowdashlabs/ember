/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {ProfileFieldConfig} from '@/api/profileFields'

/**
 * Built-in profile field presets shown on the members config view.
 */
export interface FieldTemplate {
  name: string
  icon: string
  fields: Array<{ name: string; fieldType: string; config: ProfileFieldConfig }>
}

/**
 * Default templates offered as quick-add buttons in the members config view.
 */
export const fieldTemplates: FieldTemplate[] = [
  {
    name: 'Adresse', icon: 'house', fields: [
      {name: 'Straße', fieldType: 'TEXT', config: {required: true}},
      {name: 'Postleitzahl', fieldType: 'TEXT', config: {required: true}},
      {name: 'Ort', fieldType: 'TEXT', config: {required: true}},
    ],
  },
  {
    name: 'Geburtsdatum', icon: 'calendar-plus', fields: [
      {name: 'Geburtsdatum', fieldType: 'BIRTH_DATE', config: {required: true, readonly: true}},
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
      {name: 'Notfallkontakt Name', fieldType: 'TEXT', config: {required: true}},
      {name: 'Notfallkontakt Telefon', fieldType: 'TEXT', config: {required: true}},
    ],
  },
  {
    name: 'Führerschein', icon: 'id-card', fields: [
      {name: 'Führerscheinklasse', fieldType: 'TEXT', config: {}},
      {name: 'Führerschein gültig bis', fieldType: 'DATE', config: {}},
    ],
  },
  {
    name: 'Beitrittsdatum', icon: 'calendar-plus', fields: [
      {name: 'Beitrittsdatum', fieldType: 'DATE', config: {readonly: true}},
    ],
  },
  {
    name: 'Personalnummer', icon: 'hashtag', fields: [
      {name: 'Personalnummer', fieldType: 'TEXT', config: {readonly: true}},
    ],
  },
  {
    name: 'Geschlecht', icon: 'rainbow', fields: [
      {
        name: 'Geschlecht',
        fieldType: 'ENUM',
        config: {options: ['Männlich', 'Weiblich', 'Divers', 'Nicht-binär', 'Andere'], readonly: true},
      },
    ],
  },
  {
    name: 'Jugendflamme', icon: 'fire', fields: [
      {
        name: 'Jugendflamme Stufe',
        fieldType: 'ENUM',
        config: {options: ['Jugendflamme 1', 'Jugendflamme 2', 'Jugendflamme 3'], readonly: true},
      },
      {name: 'Jugendflamme Datum', fieldType: 'DATE', config: {readonly: true}},
    ],
  },
  {
    name: 'Leistungsspange', icon: 'medal', fields: [
      {name: 'Leistungsspange', fieldType: 'BOOLEAN', config: {readonly: true}},
      {name: 'Leistungsspange Datum', fieldType: 'DATE', config: {readonly: true}},
    ],
  },
]

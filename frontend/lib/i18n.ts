'use client';

import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import enTranslations from './locales/en.json';
import ruTranslations from './locales/ru.json';
import uzTranslations from './locales/uz.json';
import type { Language } from './types';

i18n.use(initReactI18next).init({
  resources: {
    EN: { translation: enTranslations },
    RU: { translation: ruTranslations },
    UZ: { translation: uzTranslations },
  },
  lng: 'EN',
  fallbackLng: 'EN',
  interpolation: {
    escapeValue: false,
  },
});

export function setLanguage(lang: Language) {
  i18n.changeLanguage(lang);
}

export default i18n;

# Checking the Telugu, Tamil and Hindi text

Every sentence in Telugu, Tamil and Hindi was drafted without a native speaker and **needs review** before the
grandfather (or anyone) relies on it. The most important rows are the ones the alarm **speaks aloud**
(keys starting with `voice.`) and the words on his screens (`alarm.`, `elder`, `elderSetup.`).

## For the reviewer (no technical knowledge needed)

1. Open your language's file in Excel or Google Sheets (File → Import):
   - Telugu: `review-te.csv` · Tamil: `review-ta.csv` · Hindi: `review-hi.csv`
2. Read the **English** and the **Current draft** on each row.
3. If the draft is wrong or sounds unnatural, write the better wording in **Corrected**. Leave it empty if the
   draft is fine.
4. Keep anything in curly brackets exactly as it is, such as `{{name}}`, `{{time}}` or `{0}`. The app fills those
   in (for example `{{name}}` becomes "తాతయ్య"). The **Notes** column says when a row has them.
5. Rows ending in `_one` are the singular ("1 day"), rows ending in `_other` the plural ("3 days").
6. Save as **CSV (UTF-8)** with the same file name and send it back.

## For the developer

```sh
cd app
npm run translations:import -- ../docs/translations/review-te.csv   # applies the "Corrected" column
npm test                                                           # all languages must still have every key
npm run translations:export                                        # refresh the review files
```

Rows that lost a placeholder are not applied and are listed. When a language is fully checked, set
`"_reviewStatus": "reviewed by <name> on <date>"` at the top of `app/src/i18n/locales/<lang>.json`.

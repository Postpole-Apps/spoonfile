# Spoonfile — Google Play listing

Draft listing copy for `com.postpole.spoonfile`. `CopyRulesTest` reads this file: keep the
`### Title`, `### Short description` and `### Full description` headings inside each language
section, with the text directly under them. Title limit 30 characters, short description limit 80.

**Category:** Food & Drink

**Contact email:** admin@postpole.com

**Privacy policy URL:** https://pocket-ledger-postpole.web.app/spoonfile/privacy

DE, FR and ES drafts below are machine-assisted translations and are marked **needs native review**.

## Keywords (US)

recipe organizer, recipe keeper, recipe manager, save recipes, recipe saver, recipe box, cookbook,
my recipes, family recipes, recipe card, scan recipe, import recipes, grocery list, shopping list,
pay once, no subscription

"Family recipes" means recipes from your family. The app has no family sharing.

## English (EN)

### Title

Spoonfile: Recipe Organizer

### Short description

Save recipes from any website or cookbook. Your data is stored on your device.

### Full description

Spoonfile is a recipe organizer you pay for once. Save recipes from any recipe website, scan a page from your cookbook, or type in family recipes. Your data is stored on your device; no account needed.

• Save from any website: share a link from your browser and Spoonfile pulls out the ingredients and steps.
• Scan cookbook pages with your camera.
• Scale servings, cook step by step with the screen kept on, and build a shopping list.
• 75 recipes free. Unlock unlimited recipes with a one-time purchase.
• Bring your collection: import .paprikarecipes files. Export anytime to a backup file or to .paprikarecipes, a format other recipe apps can import.
• Moving to a new phone? Android's phone-to-phone transfer brings your recipes along.

Paprika is a trademark of its owner; this app is not affiliated with it.

## Deutsch (DE) — needs native review

### Title

Spoonfile: Rezept-Organizer

### Short description

Rezepte speichern. Deine Daten werden auf deinem Gerät gespeichert.

### Full description

Spoonfile ist ein Rezept-Organizer, den du nur einmal bezahlst. Speichere Rezepte von jeder Rezeptwebsite, scanne eine Seite aus deinem Kochbuch oder tippe Familienrezepte ein. Deine Daten werden auf deinem Gerät gespeichert; kein Konto nötig.

• Von jeder Website speichern: Teile einen Link aus deinem Browser, und Spoonfile liest Zutaten und Schritte heraus.
• Kochbuchseiten mit der Kamera scannen.
• Portionen umrechnen, Schritt für Schritt kochen, während der Bildschirm an bleibt, und eine Einkaufsliste anlegen.
• 75 Rezepte kostenlos. Schalte unbegrenzt viele Rezepte mit einem einmaligen Kauf frei.
• Bring deine Sammlung mit: Importiere .paprikarecipes-Dateien. Exportiere jederzeit in eine Sicherungsdatei oder als .paprikarecipes, ein Format, das andere Rezept-Apps importieren können.
• Wechsel aufs neue Handy? Die Handy-zu-Handy-Übertragung von Android bringt deine Rezepte mit.

Paprika ist eine Marke ihres Inhabers; diese App steht in keiner Verbindung zu ihr.

## Français (FR) — needs native review

### Title

Spoonfile : Carnet de recettes

### Short description

Recettes du web et des livres. Vos données sont stockées sur votre appareil.

### Full description

Spoonfile est un carnet de recettes que vous payez une seule fois. Enregistrez des recettes depuis n'importe quel site, scannez une page de votre livre de cuisine ou saisissez les recettes de famille. Vos données sont stockées sur votre appareil ; aucun compte requis.

• Enregistrez depuis n'importe quel site : partagez un lien depuis votre navigateur et Spoonfile en extrait les ingrédients et les étapes.
• Scannez les pages de vos livres de cuisine avec l'appareil photo.
• Ajustez les portions, cuisinez étape par étape avec l'écran allumé et préparez une liste de courses.
• 75 recettes gratuites. Débloquez les recettes illimitées avec un achat unique.
• Apportez votre collection : importez des fichiers .paprikarecipes. Exportez à tout moment vers un fichier de sauvegarde ou en .paprikarecipes, un format que d'autres applis de recettes peuvent importer.
• Vous changez de téléphone ? Le transfert de téléphone à téléphone d'Android emporte vos recettes.

Paprika est une marque de son propriétaire ; cette application n'est pas affiliée à celle-ci.

## Español (ES) — needs native review

### Title

Spoonfile: Libro de recetas

### Short description

Guarda recetas de webs y libros. Tus datos se almacenan en tu dispositivo.

### Full description

Spoonfile es un organizador de recetas que pagas una sola vez. Guarda recetas de cualquier web de recetas, escanea una página de tu libro de cocina o escribe las recetas de la familia. Tus datos se almacenan en tu dispositivo; sin cuenta.

• Guarda desde cualquier web: comparte un enlace desde tu navegador y Spoonfile extrae los ingredientes y los pasos.
• Escanea páginas de libros de cocina con la cámara.
• Ajusta las raciones, cocina paso a paso con la pantalla encendida y crea una lista de la compra.
• 75 recetas gratis. Desbloquea recetas ilimitadas con una compra única.
• Trae tu colección: importa archivos .paprikarecipes. Exporta cuando quieras a un archivo de copia de seguridad o a .paprikarecipes, un formato que otras aplicaciones de recetas pueden importar.
• ¿Cambias de teléfono? La transferencia de teléfono a teléfono de Android se lleva tus recetas.

Paprika es una marca de su propietario; esta aplicación no está afiliada a ella.

## Play Console checklist (first upload is ads-era)

The first Play-bound AAB ships ads (sf-01) and the `remove_ads` product (sf-02). Do not upload a
listing that denies ads, and do not declare ads without the matching build. Applied by Release and QA after approval.

- [ ] **App content → Ads: Contains ads = Yes.**
- [ ] **Target audience: 18 and over only.** Designed for children? **No.** Not directed to children under 13.
- [ ] **Privacy policy:** publish `spoonfile-privacy-ads.md` at the URL above (set its "Last updated" date on publish), then link it in the listing. `privacy_policy_url` in `app_config.xml` already points to that URL, so confirm it resolves before upload.
- [ ] **Developer website:** https://pocket-ledger-postpole.web.app (app-ads.txt is already live there; do not change it).
- [ ] **Contact email:** admin@postpole.com.
- [ ] **Advertising ID declaration: Yes.** Purposes: Advertising or marketing, Analytics, Fraud prevention, security and compliance, and Personalization (personalised ads can be served after consent). `AD_ID` stays declared in the manifest.
- [ ] **Data safety:** answer as below, then walk it against the ads-containing AAB before the first upload.
- [ ] Confirm the short and full descriptions and any feature graphic do not contradict **Contains ads = Yes** (no wording that promises an ad-less app).

## Data safety answers

Full AdMob disclosure (not "Advertising ID only"), per
https://developers.google.com/admob/android/privacy/play-data-disclosure.

**Collected and shared**

| Data type | Source | Purposes |
| --- | --- | --- |
| Device or other IDs (Advertising ID, Firebase installation ID) | Google Mobile Ads SDK, Firebase | Advertising or marketing, Analytics, Fraud prevention, security, and compliance |
| Approximate location (derived from IP) | Google Mobile Ads SDK | Advertising or marketing, Analytics, Fraud prevention, security, and compliance |
| App interactions | Google Mobile Ads SDK, GA4 / Firebase Analytics | Advertising or marketing, Analytics, Fraud prevention, security, and compliance |
| Diagnostics | Google Mobile Ads SDK, ML Kit | Advertising or marketing, Analytics, Fraud prevention, security, and compliance |

**Collected, not shared**

| Data type | Source | Purposes |
| --- | --- | --- |
| Crash logs | Crashlytics | App functionality / diagnostics |
| Purchase history | Google Play Billing | App functionality |

**Not collected:** recipes, photos, notes, shopping lists, or the URLs a user imports.

- Data is encrypted in transit: yes.
- Analytics events carry only types, counts and buckets, never URLs, domains, titles or ingredient text.
- Fetching a recipe page connects the phone directly to that website. Mention this in the privacy policy.
- No accounts and no backend: your recipes stay on your device.

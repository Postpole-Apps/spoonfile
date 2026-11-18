# Spoonfile: Google Play listing

Draft store listing for `com.postpole.spoonfile`. `CopyRulesTest` checks this file: the title must be 30 characters or fewer, the short description 80 or fewer, and the copy rules below must hold. The test reads the `### Title`, `### Short description` and `### Full description` sections of each language block, so keep those headings.

Copy rules in short: no mention of working without a connection, no family sharing, sync or multi-device claims (Play restore on the same Google account is fine, nothing is synced), no blanket "no data" or "100% private" claims, no hard-coded price. The in-app privacy line stays "Your recipes stay on your device".

- Category: Food & Drink
- Default language: English (US). Other languages: German, French, Spanish (the DE / FR / ES drafts below **need native review**).
- Contact email: `admin@postpole.com` (`support_email` in `app_config.xml`)
- Privacy policy URL: TODO until the Postpole page is published (`privacy_policy_url` in `app_config.xml` is still the placeholder). Do not invent a URL.

## Keywords (US)

recipe organizer, recipe keeper, recipe manager, save recipes, recipe saver, recipe box, cookbook, my recipes, family recipes, recipe card, scan recipe, import recipes, grocery list, shopping list, pay once, no subscription, no ads

"Family recipes" means recipes from your family, not family sharing.

## EN listing

### Title

`Spoonfile: Recipe Organizer`

### Short description

`Save recipes from any website or cookbook. Pay once. Recipes stay on your phone.`

### Full description

Spoonfile is a recipe organizer you pay for once. Save recipes from any recipe website, scan a page from your cookbook, or type in family recipes. Your recipes stay on your phone; no account needed and no ads.

• Save from any website: share a link from your browser and Spoonfile pulls out the ingredients and steps.
• Scan cookbook pages with your camera.
• Scale servings, cook step by step with the screen kept on, and build a shopping list.
• 75 recipes free. Unlock unlimited recipes with a one-time purchase.
• Bring your collection: import .paprikarecipes files. Export anytime to a backup file or to .paprikarecipes, a format other recipe apps can import.
• Moving to a new phone? Android's phone-to-phone transfer brings your recipes along.

Paprika is a trademark of its owner; this app is not affiliated with it.

## DE listing (needs native review)

### Title

`Spoonfile: Rezepte-Organizer`

### Short description

`Rezepte aus Web und Kochbuch sichern. Einmal zahlen. Bleibt auf deinem Handy.`

### Full description

Spoonfile ist ein Rezept-Organizer, für den du nur einmal zahlst. Speichere Rezepte von jeder Rezept-Website, scanne eine Seite aus deinem Kochbuch oder tippe Familienrezepte ein. Deine Rezepte bleiben auf deinem Handy; kein Konto nötig und keine Werbung.

• Von jeder Website speichern: Teile einen Link aus deinem Browser, und Spoonfile holt Zutaten und Schritte heraus.
• Kochbuchseiten mit der Kamera scannen.
• Portionen umrechnen, Schritt für Schritt kochen, während der Bildschirm an bleibt, und eine Einkaufsliste anlegen.
• 75 Rezepte kostenlos. Schalte mit einem einmaligen Kauf unbegrenzt viele Rezepte frei.
• Bring deine Sammlung mit: Importiere .paprikarecipes-Dateien. Exportiere jederzeit in eine Sicherungsdatei oder als .paprikarecipes, ein Format, das andere Rezept-Apps importieren können.
• Neues Handy? Die Gerätewechsel-Funktion von Android nimmt deine Rezepte mit.

Paprika ist eine Marke ihres Inhabers; diese App steht in keiner Verbindung zu ihr.

## FR listing (needs native review)

### Title

`Spoonfile: Carnet de recettes`

### Short description

`Sauvegardez vos recettes. Un seul paiement. Elles restent sur votre téléphone.`

### Full description

Spoonfile est un classeur de recettes que vous payez une seule fois. Enregistrez des recettes depuis n'importe quel site, scannez une page de votre livre de cuisine ou saisissez les recettes de famille. Vos recettes restent sur votre téléphone ; aucun compte nécessaire et aucune publicité.

• Enregistrez depuis n'importe quel site : partagez un lien depuis votre navigateur et Spoonfile en extrait les ingrédients et les étapes.
• Scannez les pages de vos livres de cuisine avec l'appareil photo.
• Ajustez les portions, cuisinez étape par étape avec l'écran allumé et préparez une liste de courses.
• 75 recettes gratuites. Débloquez des recettes illimitées avec un achat unique.
• Apportez votre collection : importez des fichiers .paprikarecipes. Exportez à tout moment vers un fichier de sauvegarde ou en .paprikarecipes, un format que d'autres applis de recettes peuvent importer.
• Vous changez de téléphone ? Le transfert de téléphone à téléphone d'Android emporte vos recettes.

Paprika est une marque de son propriétaire ; cette appli n'est pas affiliée à celle-ci.

## ES listing (needs native review)

### Title

`Spoonfile: Libro de recetas`

### Short description

`Guarda recetas de webs y libros. Un solo pago. Se quedan en tu teléfono.`

### Full description

Spoonfile es un organizador de recetas que pagas una sola vez. Guarda recetas de cualquier web de recetas, escanea una página de tu libro de cocina o escribe las recetas de familia. Tus recetas se quedan en tu teléfono; sin cuenta y sin anuncios.

• Guarda desde cualquier web: comparte un enlace desde tu navegador y Spoonfile extrae los ingredientes y los pasos.
• Escanea páginas de libros de cocina con la cámara.
• Ajusta las raciones, cocina paso a paso con la pantalla encendida y crea una lista de la compra.
• 75 recetas gratis. Desbloquea recetas ilimitadas con una compra única.
• Trae tu colección: importa archivos .paprikarecipes. Exporta cuando quieras a un archivo de copia de seguridad o a .paprikarecipes, un formato que otras apps de recetas pueden importar.
• ¿Cambias de teléfono? La transferencia de teléfono a teléfono de Android se lleva tus recetas.

Paprika es una marca de su propietario; esta app no está afiliada a ella.

## Data safety answers

Collected:

- App interactions (GA4 / Firebase Analytics). Purpose: analytics.
- Crash logs and diagnostics (Crashlytics; ML Kit diagnostics). Purpose: app functionality / diagnostics.
- Device or other IDs: Advertising ID and Firebase installation ID. Purposes: analytics, advertising or marketing.

Data is encrypted in transit: yes.

Not collected: recipes, photos, notes, shopping lists, or the URLs a user imports. Recipes, photos, notes and shopping lists stay on the device.

Notes for the privacy policy:

- Fetching a recipe page connects the phone directly to that website. Spoonfile has no server in between.
- Analytics events carry only types, counts and buckets, never URLs, domains, titles or ingredient text.

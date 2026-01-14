# PdfMaster - Product Plan

## Overview
A comprehensive PDF utility app for Android with viewing, editing, scanning, and conversion features.

---

## Feature List

### Core Features (MVP)
| Feature | Description | Priority |
|---------|-------------|----------|
| PDF Viewer | Open, read, zoom, scroll, page navigation, night mode | P0 |
| File Browser | Browse device storage, recent files, favorites | P0 |
| Share/Open With | Receive PDFs from other apps via intent | P0 |
| Bookmarks | Save pages within PDF | P0 |
| Search | Text search within PDF | P0 |

### Document Creation
| Feature | Description | Priority |
|---------|-------------|----------|
| PDF Scanner | Camera → crop → enhance → PDF | P0 |
| Image to PDF | Convert single/multiple images to PDF | P0 |
| Create Blank PDF | Create new PDF with text/drawings | P1 |

### PDF Operations
| Feature | Description | Priority |
|---------|-------------|----------|
| Merge PDFs | Combine multiple PDFs into one | P0 |
| Split PDF | Extract pages or split by range | P0 |
| Compress PDF | Reduce file size (quality options) | P0 |
| Rotate Pages | Rotate individual or all pages | P1 |
| Reorder Pages | Drag to reorder pages | P1 |
| Delete Pages | Remove specific pages | P1 |

### Conversion
| Feature | Description | Priority |
|---------|-------------|----------|
| PDF to Image | Export pages as JPG/PNG | P0 |
| PDF to Word | Export to DOCX | P1 |
| PDF to Text | Extract plain text | P1 |

### PDF Editing (Full Editor) ⭐ KEY FEATURE
| Feature | Description | Priority |
|---------|-------------|----------|
| **Edit Existing Text** | Select and modify text directly in PDF | P0 |
| **Add New Text** | Insert text anywhere on page | P0 |
| **Delete Text** | Remove text from PDF | P0 |
| **Change Font** | Modify font family, size, color of text | P0 |
| **Delete Pages** | Remove specific pages from PDF | P0 |
| **Rearrange Pages** | Drag-and-drop to reorder pages | P0 |
| **Add Blank Page** | Insert empty page at any position | P1 |
| **Duplicate Page** | Copy existing page | P1 |
| **Replace Page** | Swap page with image/another PDF page | P1 |
| **Edit Images** | Move, resize, delete, replace images in PDF | P1 |
| **Add Images** | Insert images into PDF pages | P1 |
| **Crop Pages** | Crop/resize page dimensions | P1 |
| **Undo/Redo** | Full edit history with unlimited undo | P0 |
| **Save Copy** | Save as new file preserving original | P0 |

### Annotations (Markup Tools)
| Feature | Description | Priority |
|---------|-------------|----------|
| Highlight | Highlight text in colors | P1 |
| Underline | Underline text | P1 |
| Strikethrough | Strike through text | P1 |
| Freehand Draw | Draw with pen tool | P1 |
| Add Sticky Notes | Comment boxes on PDF | P1 |
| Add Shapes | Rectangle, circle, arrow, line | P1 |
| Add Stamps | Predefined stamps (Approved, Rejected, etc.) | P2 |
| Whiteout/Redact | Cover sensitive content permanently | P1 |

### Forms & Signatures
| Feature | Description | Priority |
|---------|-------------|----------|
| Fill Forms | Interactive PDF form filling | P1 |
| Digital Signature | Draw/image signature, place on PDF | P1 |
| Saved Signatures | Store multiple signatures | P1 |

### Security
| Feature | Description | Priority |
|---------|-------------|----------|
| Password Protect | Encrypt PDF with password | P1 |
| Unlock PDF | Remove password (if user knows it) | P1 |
| App Lock | PIN/Biometric to open app | P2 |

### OCR
| Feature | Description | Priority |
|---------|-------------|----------|
| Text Recognition | Extract text from scanned/image PDFs | P1 |
| Searchable PDF | Convert scanned PDF to searchable | P1 |

### Organization
| Feature | Description | Priority |
|---------|-------------|----------|
| Favorites | Star important files | P1 |
| Recent Files | Quick access to recently opened | P0 |
| Tags/Labels | Organize with custom tags | P2 |
| Folders | Create virtual folders in app | P2 |

### UI/UX
| Feature | Description | Priority |
|---------|-------------|----------|
| Dark Mode | System/manual dark theme | P1 |
| Material You | Dynamic theming (Android 12+) | P2 |
| Tablet Support | Optimized tablet layout | P2 |
| Multiple Tabs | Open multiple PDFs | P2 |

### Extras
| Feature | Description | Priority |
|---------|-------------|----------|
| Text-to-Speech | Read PDF aloud | P2 |
| Cloud Integration | Google Drive, Dropbox import/export | P2 |
| Batch Operations | Process multiple files at once | P2 |
| Print | System print integration | P1 |
| Share | Share PDF via any app | P0 |

---

## Monetization Strategy

### Free Tier
- View PDFs (unlimited)
- Basic annotations (highlight, underline)
- Scanner (with small watermark)
- Merge (2 files max)
- Compress (1 per day)
- Image to PDF (5 images max)
- Page operations (delete, rotate, reorder) - 1 PDF/day
- Banner ads + occasional interstitial

### Premium (One-time $5.99 OR $3.49/month)
- Remove all ads
- Remove watermarks
- **Full PDF text editing (add, modify, delete text)**
- **Unlimited page operations**
- **Font customization**
- **Image editing in PDFs**
- Unlimited merge/split/compress
- Unlimited conversions
- Signature tool
- Form filling
- OCR features
- Password protection
- Whiteout/Redact tool
- Priority support
- All future features

---

## Technical Architecture

### Tech Stack
| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| UI Framework | Jetpack Compose |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 34 (Android 14) |
| Architecture | MVVM + Clean Architecture |
| DI | Hilt |
| Async | Kotlin Coroutines + Flow |
| Local Storage | Room Database |
| PDF Rendering | PdfRenderer (Android) |
| PDF Manipulation | iText 7 / PdfBox |
| Scanner | CameraX + ML Kit Document Scanner |
| OCR | ML Kit Text Recognition |
| Image Processing | Coil |
| Ads | Google AdMob |
| Billing | Google Play Billing Library |
| Analytics | Firebase Analytics |
| Crash Reporting | Firebase Crashlytics |

### Project Structure
```
app/
├── data/
│   ├── local/
│   │   ├── database/
│   │   │   ├── PdfDatabase.kt
│   │   │   ├── dao/
│   │   │   └── entity/
│   │   └── preferences/
│   │       └── UserPreferences.kt
│   ├── repository/
│   │   ├── PdfRepositoryImpl.kt
│   │   ├── FileRepositoryImpl.kt
│   │   └── SettingsRepositoryImpl.kt
│   └── model/
│       └── (data transfer objects)
├── domain/
│   ├── model/
│   │   ├── PdfDocument.kt
│   │   ├── PdfPage.kt
│   │   ├── Annotation.kt
│   │   ├── TextBlock.kt
│   │   └── Signature.kt
│   ├── repository/
│   │   ├── PdfRepository.kt
│   │   ├── FileRepository.kt
│   │   └── SettingsRepository.kt
│   └── usecase/
│       ├── viewer/
│       │   ├── OpenPdfUseCase.kt
│       │   ├── SearchTextUseCase.kt
│       │   └── BookmarkUseCase.kt
│       ├── scanner/
│       │   ├── ScanDocumentUseCase.kt
│       │   └── EnhanceImageUseCase.kt
│       ├── editor/                    # ⭐ PDF EDITING
│       │   ├── page/
│       │   │   ├── DeletePageUseCase.kt
│       │   │   ├── ReorderPagesUseCase.kt
│       │   │   ├── RotatePageUseCase.kt
│       │   │   ├── AddBlankPageUseCase.kt
│       │   │   └── DuplicatePageUseCase.kt
│       │   ├── text/
│       │   │   ├── ExtractTextUseCase.kt
│       │   │   ├── EditTextUseCase.kt
│       │   │   ├── AddTextUseCase.kt
│       │   │   ├── DeleteTextUseCase.kt
│       │   │   └── ChangeTextStyleUseCase.kt
│       │   ├── image/
│       │   │   ├── AddImageUseCase.kt
│       │   │   ├── EditImageUseCase.kt
│       │   │   └── RemoveImageUseCase.kt
│       │   └── history/
│       │       ├── UndoUseCase.kt
│       │       └── RedoUseCase.kt
│       ├── tools/
│       │   ├── MergePdfsUseCase.kt
│       │   ├── SplitPdfUseCase.kt
│       │   ├── CompressPdfUseCase.kt
│       │   └── ConvertPdfUseCase.kt
│       ├── annotate/
│       │   ├── HighlightUseCase.kt
│       │   ├── DrawUseCase.kt
│       │   ├── AddShapeUseCase.kt
│       │   └── AddStampUseCase.kt
│       ├── signature/
│       │   ├── CreateSignatureUseCase.kt
│       │   ├── PlaceSignatureUseCase.kt
│       │   └── SaveSignatureUseCase.kt
│       ├── security/
│       │   ├── EncryptPdfUseCase.kt
│       │   └── DecryptPdfUseCase.kt
│       └── ocr/
│           ├── RecognizeTextUseCase.kt
│           └── MakeSearchableUseCase.kt
├── presentation/
│   ├── ui/
│   │   ├── home/
│   │   │   ├── HomeScreen.kt
│   │   │   ├── HomeViewModel.kt
│   │   │   └── components/
│   │   ├── viewer/
│   │   │   ├── ViewerScreen.kt
│   │   │   ├── ViewerViewModel.kt
│   │   │   └── components/
│   │   ├── scanner/
│   │   │   ├── ScannerScreen.kt
│   │   │   └── ScannerViewModel.kt
│   │   ├── editor/                    # ⭐ EDITOR UI
│   │   │   ├── EditorScreen.kt
│   │   │   ├── EditorViewModel.kt
│   │   │   ├── PageManagerScreen.kt
│   │   │   ├── TextEditorScreen.kt
│   │   │   └── components/
│   │   │       ├── PageThumbnail.kt
│   │   │       ├── TextToolbar.kt
│   │   │       ├── FontPicker.kt
│   │   │       ├── ColorPicker.kt
│   │   │       └── EditHistory.kt
│   │   ├── tools/
│   │   │   ├── MergeScreen.kt
│   │   │   ├── SplitScreen.kt
│   │   │   ├── CompressScreen.kt
│   │   │   └── ConvertScreen.kt
│   │   ├── signature/
│   │   │   ├── SignatureScreen.kt
│   │   │   └── SignaturePadComposable.kt
│   │   ├── settings/
│   │   │   └── SettingsScreen.kt
│   │   └── components/
│   │       ├── PdfPageView.kt
│   │       ├── FileListItem.kt
│   │       ├── ToolButton.kt
│   │       ├── LoadingIndicator.kt
│   │       └── AdBanner.kt
│   ├── navigation/
│   │   ├── NavGraph.kt
│   │   └── Screen.kt
│   └── theme/
│       ├── Theme.kt
│       ├── Color.kt
│       └── Type.kt
├── di/
│   ├── AppModule.kt
│   ├── DatabaseModule.kt
│   ├── RepositoryModule.kt
│   └── UseCaseModule.kt
├── util/
│   ├── PdfUtils.kt
│   ├── FileUtils.kt
│   ├── ImageUtils.kt
│   └── Extensions.kt
├── billing/
│   ├── BillingManager.kt
│   └── PremiumManager.kt
├── ads/
│   └── AdManager.kt
└── PdfMasterApp.kt
```

### Key Dependencies
```kotlin
// PDF
implementation("com.itextpdf:itext7-core:7.2.5")
implementation("org.apache.pdfbox:pdfbox-android:2.0.27.0")

// Scanner & OCR
implementation("com.google.mlkit:document-scanner:16.0.0-beta1")
implementation("com.google.mlkit:text-recognition:16.0.0")

// CameraX
implementation("androidx.camera:camera-camera2:1.3.0")
implementation("androidx.camera:camera-lifecycle:1.3.0")
implementation("androidx.camera:camera-view:1.3.0")

// Compose
implementation("androidx.compose.ui:ui:1.5.0")
implementation("androidx.compose.material3:material3:1.2.0")

// Room
implementation("androidx.room:room-runtime:2.6.0")
implementation("androidx.room:room-ktx:2.6.0")

// Hilt
implementation("com.google.dagger:hilt-android:2.48")

// Billing
implementation("com.android.billingclient:billing-ktx:6.0.0")

// Ads
implementation("com.google.android.gms:play-services-ads:22.5.0")
```

---

## Screen Flow

```
App Launch
    │
    ▼
┌─────────────────────────────────────────────────┐
│                   HOME SCREEN                    │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌────────┐│
│  │ All PDFs│ │ Recent  │ │Favorites│ │ Folders ││
│  └─────────┘ └─────────┘ └─────────┘ └────────┘│
│                                                  │
│  [File List / Grid View]                        │
│                                                  │
│  ┌──────────────────────────────────────────┐  │
│  │            QUICK TOOLS BAR                │  │
│  │  [Scan] [Merge] [Compress] [Convert]     │  │
│  └──────────────────────────────────────────┘  │
│                                                  │
│              [+ FAB: New/Import]                │
└─────────────────────────────────────────────────┘
                    │
        ┌───────────┴───────────┐
        ▼                       ▼
   PDF VIEWER              TOOLS SCREEN
   - Read                  - Merge
   - Annotate              - Split
   - Edit                  - Compress
   - Sign                  - Convert
   - Share                 - Protect
```

---

## Development Phases

### Phase 1: Foundation
- [ ] Project setup with architecture (Compose + Hilt + Room)
- [ ] Navigation structure
- [ ] Theme and design system (Material 3)
- [ ] File browser with storage permissions
- [ ] Recent files tracking
- [ ] PDF viewer (basic rendering, zoom, scroll)

### Phase 2: Core Tools
- [ ] PDF Scanner with camera (ML Kit Document Scanner)
- [ ] Image to PDF converter
- [ ] Merge multiple PDFs
- [ ] Split PDF by pages/ranges
- [ ] Compress PDF (quality options)
- [ ] PDF to Image export

### Phase 3: Full PDF Editor ⭐ KEY PHASE
- [ ] Page management UI (thumbnails grid)
- [ ] Delete pages
- [ ] Rearrange pages (drag & drop)
- [ ] Rotate pages
- [ ] Add blank pages
- [ ] Duplicate pages
- [ ] Text editing engine (iText/PdfBox integration)
- [ ] Select existing text
- [ ] Edit/rewrite existing text
- [ ] Delete text
- [ ] Add new text boxes
- [ ] Font picker (family, size, color)
- [ ] Image editing in PDF
- [ ] Add images to pages
- [ ] Move/resize/delete images
- [ ] Undo/Redo system
- [ ] Save / Save As

### Phase 4: Annotations & Markup
- [ ] Highlight tool
- [ ] Underline tool
- [ ] Strikethrough tool
- [ ] Freehand drawing/pen
- [ ] Add shapes (rectangle, circle, arrow, line)
- [ ] Sticky notes/comments
- [ ] Whiteout/redact tool
- [ ] Stamps (Approved, Rejected, Draft, etc.)

### Phase 5: Forms & Signatures
- [ ] Digital signature drawing
- [ ] Image signature import
- [ ] Signature placement on PDF
- [ ] Saved signatures management
- [ ] PDF form detection
- [ ] Interactive form filling
- [ ] Form data export

### Phase 6: Advanced Features
- [ ] OCR text recognition (ML Kit)
- [ ] Convert scanned PDF to searchable
- [ ] Password protect PDF
- [ ] Unlock password-protected PDF
- [ ] PDF to Word conversion
- [ ] PDF to Text extraction
- [ ] Page cropping

### Phase 7: Polish & Monetization
- [ ] AdMob integration (banner + interstitial)
- [ ] Google Play Billing (subscription + one-time)
- [ ] Premium feature gating
- [ ] Dark mode / Material You theming
- [ ] Settings screen
- [ ] Onboarding flow
- [ ] App lock (PIN/biometric)
- [ ] Performance optimization
- [ ] Memory management for large PDFs

### Phase 8: Release Preparation
- [ ] Testing on multiple devices/Android versions
- [ ] Play Store listing (screenshots, description)
- [ ] Feature graphic and icon
- [ ] Privacy policy
- [ ] Terms of service
- [ ] ProGuard/R8 optimization
- [ ] Signed release build
- [ ] Beta testing

---

## Success Metrics

| Metric | Target (Month 6) |
|--------|------------------|
| Downloads | 10,000+ |
| Rating | 4.0+ stars |
| DAU | 1,000+ |
| Premium conversion | 2-3% |
| Monthly revenue | $300-500 |

---

## Competitive Advantages

1. **Clean UI** - No clutter, modern Material 3 design
2. **Fast** - Native performance, no web views
3. **Offline** - All features work without internet
4. **Privacy** - No cloud upload required
5. **Fair pricing** - Reasonable limits on free tier
6. **No subscription push** - One-time purchase option

---

*Generated: January 2026*
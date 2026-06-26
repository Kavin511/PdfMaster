# PdfMaster — Product Plan v2 (Target: $1M Annual Revenue)

> Rewrite of `PRODUCT_PLAN.md`. The original targeted ~$300–500/month (a ~$5K/year side project).
> This version is engineered backwards from **$1,000,000 in annual gross revenue** as a solo founder.
> It assumes the existing codebase (viewer, merge/split/compress, scanner, whiteout-overlay editor)
> as the starting point and re-sequences everything around the money layer that is currently missing.

---

## 1. The $1M Math (read this first)

$1M/year gross = **$83,333 MRR** = ~**$2,740/day**. You do not get there with banner ads on a
free utility. You get there with **subscriptions at volume**. Three levers only: **traffic ×
conversion × price**. Here is the target unit economics, blended after Google Play's 15% fee
(the reduced rate applies to the first $1M/year):

| Lever | Conservative | Plan target | Aggressive |
|---|---|---|---|
| Monthly active users (MAU) | 2,000,000 | 1,200,000 | 700,000 |
| MAU → paid conversion | 1.5% | 2.5% | 4.0% |
| Active paying subscribers | 30,000 | 30,000 | 28,000 |
| Blended gross revenue / payer / year | $34 | $34 | $36 |
| **Gross annual revenue** | **~$1.02M** | **~$1.02M** | **~$1.0M** |

**The single hardest number is MAU.** Everything in this plan exists to either (a) grow MAU
cheaply or (b) raise conversion/ARPU so you need less MAU. Three viable routes to ~1M MAU:

1. **Organic ASO + share-loop** (slowest, cheapest, highest margin) — 18–30 months.
2. **Paid UA with a positive payback loop** (fastest, needs the LTV > CAC math below) — 9–18 months.
3. **Web + Android combo** (iLovePDF/Smallpdf model) — web pulls enormous free SEO traffic that
   cross-promotes the app. Highest ceiling, biggest build. Treated as the **Phase 4 multiplier**.

### LTV > CAC gate (the only rule that matters for paid UA)
- Blended revenue/payer/year (gross): **$34** → net after 15%: **~$29**.
- At 2.5% conversion, **value of one install ≈ $0.72 net** in year one (before repeat-year renewals).
- With ~35% annual renewal, **3-year LTV/install ≈ $1.30 net**.
- **Therefore: only buy installs below ~$0.60 blended CPI.** Above that, paid UA burns cash —
  fall back to organic. Re-check this gate monthly; it is the kill-switch on ad spend.

---

## 2. Monetization Redesign

The original $5.99 one-time / $3.49.mo pricing **caps you at ~$1M only if you sell 200K lifetime
units** — unrealistic. Restructure to a subscription-first ladder with a high-anchor lifetime.

### Pricing ladder
| Plan | Price | Role |
|---|---|---|
| **Weekly** (with 3-day free trial) | **$4.99/wk** | The conversion workhorse for one-off "I need to edit this PDF NOW" intent. Highest revenue/payer. |
| **Annual** | **$39.99/yr** (anchored as "$3.33/mo, save 84%") | The value plan; best retention. |
| **Lifetime** | **$79.99 one-time** | High anchor; captures subscription-averse users (your original audience). |
| **Free** | $0 (no ads) | The MAU engine. Generous enough to rank/retain, gated by watermark + daily limits enough to convert. |

> Weekly + trial is the highest-yield pattern for intent-driven utilities (user has a document
> to fix today). Annual maximizes retention. Lifetime monetizes the "no subscriptions" crowd you
> already named as a competitive advantage — keep it, just price it as an anchor, not the default.

### Free vs. Premium gating (revenue-tuned)
**Free tier** — must be good enough to win 4.5★ and rank, but create *daily friction* on
high-intent actions:
- View PDFs: unlimited (this is your retention/MAU hook — never gate it)
- Scanner: unlimited but **visible watermark** (the watermark IS the share-loop growth engine)
- Merge: 2 files/op · Compress: 1/day · Split: 1/day · Image→PDF: 5 images · Page ops: 3/day

> **No ads.** This is a deliberate choice, not an omission. Ad ARPU for a short-session utility is
> ~$0.02–0.10/MAU/mo — a rounding error next to subscription LTV — and interstitials suppress the
> retention and conversion that actually produce the $1M. The watermark + limits do the free-tier
> friction job *better*, because the watermark also drives the share-loop growth. (Ads are
> additionally off the table here because the founder's AdMob account is permanently banned;
> reusing AdMob risks the Play account too.)

**Premium unlocks** (the wall, ordered by willingness-to-pay):
1. **Remove watermark** (the #1 purchase trigger — most conversions come from here)
2. **Unlimited everything** (merge/split/compress/convert/page-ops)
3. **PDF text editing + fonts/colors** (the headline feature — see §4 on making it real)
4. **Password protect / unlock**, **signatures**, **form filling**, **OCR**, **redact**
5. **PDF→Word/Excel export**, batch operations, cloud sync

### Conversion surfaces (build these — they ARE the revenue)
- **Hard paywall** triggered at the moment of intent (tap "Remove watermark", "Edit text",
  "Unlock unlimited"), not a passive settings screen.
- **Trial → paid**: 3-day trial on weekly/annual, with day-2 reminder notification.
- **Onboarding paywall**: show the annual plan once, immediately after onboarding value props.
- **Win-back**: discounted offer to users who cancel or let trial lapse.

---

## 3. The Conversion & Retention Funnel

Revenue = MAU × (the leaks you plug below). Instrument every step in Firebase/analytics from day one.

```
Install → Onboarding (value props) → Onboarding paywall (annual)  ── ~1-3% buy here
   ↓ (no buy)
First successful tool use (activation — TRACK THIS, it predicts everything)
   ↓
Hit a gate (watermark / daily limit / edit-text wall) → Hard paywall  ── ~60% of revenue
   ↓ (no buy)
Day 2: trial-reminder / value notification
   ↓
Repeat usage → eventual convert OR churn
   ↓
Cancel/lapse → Win-back offer  ── recover ~10-15%
```

**Activation is the leading indicator.** A user who completes one tool action in session 1
converts and retains dramatically better. Optimize onboarding to push toward *one successful
action* (scan a doc / open a PDF), not toward the paywall first.

**Retention targets** (utility-app realistic): D1 ≥ 35%, D7 ≥ 18%, D30 ≥ 8%. Below these,
fix retention before spending on UA — a leaky bucket makes paid growth lose money.

---

## 4. Product Priorities, Re-sequenced for Revenue

The current code is a strong foundation but **mis-prioritized for money**. Re-order:

### P0 — The money layer (nothing earns until this ships)
- [x] **Google Play Billing v7**: `BillingManager` + `PremiumManager` (built)
- [x] **Feature gating** wired to entitlement across every non-stubbed tool (built — `FeatureGate`)
- [x] **Watermark on free scanner output** (growth loop + purchase trigger) (built)
- [ ] **Paywall screens**: onboarding paywall + hard paywall + trial flow (hard-paywall dialog exists; onboarding + trial flow remain)
- [ ] **Analytics + Crashlytics**: funnel events (activation, gate-hit, paywall-view, purchase)
- [ ] **Create Play Console products** (`pdfmaster_premium` weekly/annual base plans + `pdfmaster_lifetime`) and test on an internal track
- ~~AdMob~~ — **cut.** No ads: low ARPU, suppresses conversion, and AdMob is unavailable (banned account). See §2.

### P1 — Make the headline feature credible
The editor currently fakes text editing via **whiteout + overlay** (`EditorViewModel.renderPdfWithElements`).
That's market-acceptable but fidelity-limited. Decide explicitly:
- [ ] **Option A (cheap):** ship whiteout-overlay, market honestly as "add/replace text", invest
  in font-matching + background-color sampling to reduce the visible seam. ~1 week polish.
- [ ] **Option B (premium):** license **ComPDFKit** (already wired, `USE_COMPDFKIT=false`) for true
  text-stream editing. Real per-app license cost — only worth it once MRR justifies it. Flip the
  flag, fix imports. Defer until ~$10K MRR.
- [ ] Recommendation: **A now, B later.** Don't block launch on B.

### P1 — Close the credibility gaps (currently "Not implemented")
- [ ] Password **protect / unlock** (`encryptPdf`/`decryptPdf`/`validatePassword` are stubs) — expected by users, easy win
- [ ] **PDF→Text** and **PDF→Word** export (premium, high willingness-to-pay)
- [ ] Add-blank-page (stub)

### P2 — Retention & ARPU expanders
- [ ] OCR → searchable PDF (premium)
- [ ] Signatures + form filling (already have screens — finish + gate)
- [ ] Annotations polish (highlight/redact properly — redact currently approximated)
- [ ] Cloud import/export (Drive/Dropbox) — raises switching cost & retention

### P3 — The $1M multiplier (the route that breaks the MAU ceiling)
- [ ] **Companion web app** (`pdfmaster.web`-style): browser-based merge/compress/convert that
  ranks for "compress pdf online" etc. Web SEO is the cheapest mass-traffic source in this
  category and cross-promotes app installs. This is how iLovePDF/Smallpdf scaled. Biggest build,
  highest ceiling — pursue once the app funnel is proven and profitable.

---

## 5. Roadmap by Revenue Milestone (not by feature phase)

| Stage | Goal | Focus | Rough timeline (solo) |
|---|---|---|---|
| **M0 — Monetize** | First dollar | P0 money layer + ship existing tools to Play | 4–6 weeks |
| **M1 — $1K MRR** | Prove the funnel | ASO, paywall A/B, fix activation & D7 retention | 2–4 months |
| **M2 — $10K MRR** | Prove repeatability | Scale ASO, turn on paid UA *only if CPI < $0.60*, add P1 features | 4–8 months |
| **M3 — $40K MRR** | Scale the loop | Localize top 10 markets, optimize trial→paid, add P2 ARPU expanders | 6–12 months |
| **M4 — $83K MRR ($1M ARR)** | Break the ceiling | Launch web companion, paid UA at scale, lifecycle/win-back automation | 12–24 months |

**Total realistic horizon to $1M ARR: 18–36 months.** Anyone promising faster is selling something.

---

## 6. Growth Engine (how MAU actually grows)

1. **ASO is the foundation.** PDF intent keywords have massive volume. Win "pdf editor", "scan
   to pdf", "compress pdf", "pdf to word". Iterate title/screenshots/listing monthly. This is the
   highest-ROI activity for a solo founder.
2. **Share-loop.** Free-tier watermark on shared/scanned docs = free impressions → installs.
   Every shared PDF is an ad. Make the watermark tasteful but present.
3. **Localization.** Cloning the listing + UI strings into Hindi, Spanish, Portuguese, Indonesian,
   Arabic multiplies install volume for near-zero marginal cost. Do this at M3.
4. **Paid UA — gated.** Only spend when blended CPI < $0.60 and D7 ≥ 18%. Scale spend in lockstep
   with measured LTV. Treat the LTV/CAC gate in §1 as a hard rule.
5. **Web SEO (M4).** The ceiling-breaker. Free online PDF tools rank organically and funnel installs.

---

## 7. KPIs & Unit Economics Dashboard (track weekly)

| Metric | Target |
|---|---|
| Installs / week | growth MoM |
| Activation rate (1st tool action) | ≥ 40% |
| D1 / D7 / D30 retention | 35% / 18% / 8% |
| MAU → trial start | ≥ 5% |
| Trial → paid | ≥ 35% |
| MAU → paid (blended) | ≥ 2.5% |
| Blended ARPPU / year (gross) | ≥ $34 |
| Blended CPI (if buying) | < $0.60 |
| LTV : CAC | ≥ 3:1 |
| MRR | → $83,333 |

---

## 8. Technical Additions Required (vs. current code)

| Area | Current state | Needed for $1M |
|---|---|---|
| Billing | **built** — Play Billing v7, entitlement cache, restore | Create Play Console products + test track |
| Ads | not pursued | **None** — deliberately cut (low ARPU, hurts conversion, AdMob banned) |
| Paywall | hard-paywall dialog built; `PremiumScreen` live | Add onboarding paywall + trial + win-back |
| Analytics | none | Firebase Analytics funnel + Crashlytics + remote-config for A/B |
| Gating | **built** — `FeatureGate` across all non-stubbed tools | Gate security/OCR/forms once those are implemented |
| Editor | whiteout-overlay, ComPDFKit disabled | Polish overlay (now) / license ComPDFKit (at scale) |
| Security tools | stubbed | Implement encrypt/decrypt/validate |
| Conversions | stubbed | PDF→Text/Word |
| Remote config | none | Server-controlled pricing/gates for experiments without releases |

---

## 9. Risks & Honest Caveats

- **MAU is the make-or-break.** ~1M MAU solo is hard. If organic + share-loop stalls and CPI
  stays above the gate, $1M is not reachable on app installs alone — the **web companion (P3)
  becomes mandatory**, not optional.
- **Category is brutally competitive** (Adobe, CamScanner, Xodo, iLovePDF). Win on *speed +
  clean UI + fair lifetime option + privacy/offline* — the differentiators already named in v1.
- **Whiteout editing fidelity** can generate refunds/1★ reviews if oversold. Market it honestly.
- **ComPDFKit license cost** must be justified by MRR before flipping it on.
- **Play policy**: aggressive paywalls risk rejection. Keep the free tier genuinely useful.
- **Solo bandwidth**: M0→M4 is 18–36 months of sustained work. Sequence ruthlessly; the money
  layer (M0) and ASO/retention (M1) come before *every* new feature.

---

## 10. Immediate Next Weeks (do this, in order)

1. ~~`BillingManager` + `PremiumManager` + entitlement gating~~ — **done**
2. ~~Free-tier watermark + daily/per-op limits across tools~~ — **done** (`FeatureGate`)
3. Create Play Console products (`pdfmaster_premium` weekly/annual + `pdfmaster_lifetime`); test the purchase + restore flow on an internal track (week 1)
4. Build onboarding paywall + 3-day trial flow + win-back offer (week 1–2)
5. Add Firebase Analytics funnel events + Crashlytics (week 2)
6. Implement password protect/unlock; polish whiteout editor seam (week 2–3)
7. Play listing + ASO pass + privacy policy + signed release → **ship and get the first dollar** (week 3–4)

> Ship M0 monetized before building anything in P2/P3. You cannot optimize a funnel that isn't earning.
> No ad work appears anywhere in this list — by design.

---

*Rewritten: 2026-06-24 (ads removed; billing + gating now built). Supersedes the $300–500/mo target in `PRODUCT_PLAN.md`.*

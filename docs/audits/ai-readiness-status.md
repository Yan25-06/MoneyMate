# AI Readiness Status / Trang thai san sang AI - MoneyMate

- **Date / Ngay:** 2026-04-03
- **Scope / Pham vi:** Current readiness for AI features (receipt scanner, assistant).
- **Context / Boi canh:** Offline-first architecture with sync-hardening baseline.

## 1) Overall Status / Tong quan
- **VI:** **FOUNDATION READY, FEATURE LOGIC PENDING.**
- **EN:** **FOUNDATION READY, FEATURE LOGIC PENDING.**

- **VI:** Nen tang kien truc da san sang de tich hop AI an toan, nhung can tiep tuc hardening business logic AI truoc rollout rong.
- **EN:** The architecture is ready for safe AI integration, but AI business logic still needs hardening before broad rollout.

## 2) Existing AI Foundations / Nen tang AI da co
- **VI:** Da co worker infrastructure: `AIReceiptScannerWorker` wired qua `MoneyMateWorkerFactory`.
- **EN:** Worker infrastructure exists: `AIReceiptScannerWorker` wired via `MoneyMateWorkerFactory`.

- **VI:** Local write path on dinh (Repository + executor), phu hop flow AI propose -> user confirm -> save.
- **EN:** Local write path is stable (Repository + executor), suitable for AI propose -> user confirm -> save flow.

- **VI:** Sync foundation da duoc harden giup tranh memory/query pressure khi AI tao nhieu records.
- **EN:** Sync foundation has been hardened to reduce memory/query pressure when AI generates many records.

- **VI:** Metadata schema (`updated_at`, `sync_status`, `is_deleted`) da san sang cho traceability AI changes.
- **EN:** Metadata schema (`updated_at`, `sync_status`, `is_deleted`) is ready for AI change traceability.

## 3) Mandatory AI Guardrails / Luu y bat buoc khi trien khai AI
- **VI:** Human-in-the-loop: khong auto-commit giao dich AI neu chua user confirm.
- **EN:** Human-in-the-loop: never auto-commit AI-generated transaction without user confirmation.

- **VI:** Strict validation: amount > 0, type hop le, wallet/category ton tai, timestamp hop le.
- **EN:** Strict validation: amount > 0, valid type, existing wallet/category, valid timestamp.

- **VI:** Deterministic fallback: parse fail -> fallback manual entry, giu OCR text de user sua nhanh.
- **EN:** Deterministic fallback: parse fail -> manual entry fallback, keep OCR text for quick edits.

- **VI:** Background only: OCR/AI call trong Worker, UI chi observe state.
- **EN:** Background only: OCR/AI calls run in Worker; UI only observes state.

- **VI:** Duplicate prevention: can co guard theo image hash + amount + timestamp bucket.
- **EN:** Duplicate prevention: enforce guard using image hash + amount + timestamp bucket.

- **VI:** Privacy by default: redact fields nhay cam, khong log raw payload AI.
- **EN:** Privacy by default: redact sensitive fields, do not log raw AI payloads.

- **VI:** Rate limit + quota handling bat buoc cho cloud AI calls.
- **EN:** Rate limit + quota handling is mandatory for cloud AI calls.

## 4) Current Gaps Before Release / Khoang trong truoc khi release
- **VI:** Chua co benchmark corpus da ngon ngu/tien te cho receipt parsing.
- **EN:** No multilingual/multi-currency benchmark corpus yet for receipt parsing.

- **VI:** Chua co confidence policy de quyet dinh auto-fill muc nao.
- **EN:** Confidence policy for auto-fill decisions is not finalized.

- **VI:** Chua co funnel analytics day du: scan -> parse -> edit -> save -> sync.
- **EN:** Full funnel analytics is missing: scan -> parse -> edit -> save -> sync.

- **VI:** Chua co anti-abuse controls day du (spam requests, oversized input, malformed payload).
- **EN:** Anti-abuse controls are not complete yet (spam requests, oversized input, malformed payload).

## 5) AI Production Gate Checklist / Checklist gate production AI
- [ ] Freeze AI output contract (JSON schema + required fields).
- [ ] Implement confidence threshold + UX warning when confidence is low.
- [ ] Add duplicate prevention gate before transaction insert.
- [ ] Add integration tests for OCR -> parse -> save -> sync path.
- [ ] Add logging redaction and crash-report privacy policy.
- [ ] Benchmark latency and memory on low/mid-tier devices.

## 6) Suggested Rollout Plan / De xuat rollout
1. **VI:** Chot OCR local + deterministic parser truoc.  
   **EN:** Finalize local OCR + deterministic parser first.
2. **VI:** Them cloud AI extraction nhu tang cuong tuy chon.  
   **EN:** Add cloud AI extraction as an optional enhancement.
3. **VI:** Bat internal dogfood, do quality va edit-rate.  
   **EN:** Run internal dogfood and track quality plus edit-rate.
4. **VI:** Canary rollout theo user percentage, monitor save-success-rate.  
   **EN:** Canary rollout by user percentage, monitor save-success-rate.

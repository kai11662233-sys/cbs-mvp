# CBS-MVP 運用・移行ガイド (Discovery機能拡張)

## 1. 概要
Discovery機能の拡張（CSV取り込み、ProfitScore概算、Freshnessチェック）に伴う運用ガイドおよび仕様解説です。

## 2. 変更内容と移行戦略

### 2.1 DBスキーマ変更
今回のリリースにおけるDBスキーマ（`schema.sql`）の変更はありません。既存の `discovery_items` テーブルを使用します。
ただし、以下のカラムがCSV取り込みによって更新されるようになります：
- `price_yen`
- `weight_kg`
- `last_checked_at`
- `snapshot` (JSONB)
- `safety_score`, `profit_score`, `freshness_score`, `overall_score`

### 2.2 データ移行
- 既存データに対するマイグレーションスクリプトの実行は不要です。
- CSV取り込み（Ingest）機能はアップサート（`upsert`）として動作するため、既存の `source_url` を持つレコードは自動的に最新情報に更新されます。

### 2.3 ロールバック
- アプリケーションのロールバックは、旧バージョンのJAR/Dockerイメージへの切り戻しで対応可能です。DBスキーマ変更がないため、DBのリストアは原則不要です。

## 3. 監査ログ (State Transitions)

`state_transitions` テーブルには、以下のイベントが記録されます。

| トリガー | Entity Type | From | To | Actor | Reason Code | 備考 |
|---|---|---|---|---|---|---|
| **新規作成 (手動)** | `DISCOVERY_ITEM` | `NULL` | `NEW` | `OPs` | `Manual Create` | 管理画面等からの単発登録 |
| **新規作成 (CSV)** | `DISCOVERY_ITEM` | `NULL` | `NEW` | `SYSTEM` | `CSV Ingest` | CSVアップロードによる一括登録 |
| **Draft作成成功** | `DISCOVERY_ITEM` | `CHECKED` | `DRAFTED` | `SYSTEM` | - | Draft ID発行時 |
| **Draft作成失敗** | - | - | - | - | - | 失敗時はログ記録されず、例外（Conflict/UnprocessableEntity）としてAPIレスポンスされる |

※ CSV Ingest時の「更新」については、ログ量削減のため `state_transitions` には記録されません（`discovery_items.updated_at` および `snapshot` で追跡）。

## 4. 動作確認手順 (curl)

### A. 正常系

1. **CSVアップロード**
   ```bash
   curl -X POST http://localhost:8080/discovery/feeds/csv \
     -H "X-OPS-KEY: dev123" \
     -F "file=@items.csv"
   ```
   *期待値*: 200 OK, `{"inserted": N, "updated": M, "errors": []}`

2. **Draft作成**
   ```bash
   curl -X POST http://localhost:8080/discovery/items/{id}/draft \
     -H "X-OPS-KEY: dev123" \
     -H "Content-Type: application/json" \
     -d '{"fxRate": 150.0}'
   ```
   *期待値*: 200 OK, `{"draftId": 123, ...}`

### B. 異常系

1. **不正なCSVフォーマット**
   *期待値*: 200 OK（部分成功）または 422 Unprocessable Entity。レスポンスの `errors` 配下に詳細が出力される。

2. **Freshnessチェックエラー**
   *条件*: `last_checked_at` が24時間以上前のアイテムに対してDraft作成を実行。
   *期待値*: 409 Conflict, `FRESHNESS_TOO_OLD`

3. **認証エラー**
   *条件*: `X-OPS-KEY` なし。
   *期待値*: 401 Unauthorized

### C. 冪等性確認

1. **Draft作成連打**
   *手順*: 上記「Draft作成」を同一IDに対して2回実行。
   *期待値*:
     - 1回目: 200 OK (Draft作成)
     - 2回目: 200 OK (ALREADY_DRAFTED ステータスで、既存の Draft ID が返却される)

## 5. 仕様上の注意点

- **Kill Switch**: `PAUSED` 状態の場合、Draft作成APIは 503 Service Unavailable を返します。
- **Profit Score概算**: CSV取り込み時点の Profit Score はあくまで `PricingCalculator` による概算です。Draft作成時に最新の `PricingService` ロジック（Gateチェック含む）で再評価され、最終的な可否が決定されます。
- **Freshness**: `DISCOVERY_FRESHNESS_REQUIRED_HOURS` システムフラグ（デフォルト24）により制御されます。

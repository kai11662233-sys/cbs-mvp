# CBS-MVP 運用・移行ガイド (Discovery & Webhook拡張)

## 1. 概要
Discovery機能の拡張（CSV取り込み、ProfitScore概算、Freshnessチェック）およびeBay Webhookによる注文自動取り込み機能に関する運用ガイドおよび仕様解説です。

## 2. 変更内容と移行戦略

### 2.1 DBスキーマ変更
今回のリリースにおけるDBスキーマ (`schema.sql`) の変更はありません。既存の `discovery_items` および `orders` テーブルを使用します。
ただし、以下のカラムがCSV取り込みによって更新されるようになります：
- `discovery_items`: `price_yen`, `weight_kg`, `last_checked_at`, `snapshot` (JSONB), `scores...`

### 2.2 データ移行
- 既存データに対するマイグレーションスクリプトの実行は不要です。
- CSV取り込み (Ingest) 機能はアップサート (upsert) として動作するため、既存の `source_url` を持つレコードは自動的に最新状態に更新されます。

### 2.3 ロールバック
- アプリケーションのロールバックは、旧バージョンのJAR/Dockerイメージへの切り戻しで対応可能です。DBスキーマ変更がないため、DBのリストアは原則不要です。

## 3. 監査ログ (State Transitions)

`state_transitions` テーブルには、以下のイベントが記録されます。

| トリガー | Entity Type | From | To | Actor | Reason Code | 備考 |
|---|---|---|---|---|---|---|
| **新規作成 (手動)** | `DISCOVERY_ITEM` | `NULL` | `NEW` | `OPs` | `Manual Create` | 管理画面等からの単発登録 |
| **新規作成 (CSV)** | `DISCOVERY_ITEM` | `NULL` | `NEW` | `SYSTEM` | `CSV Ingest` | CSVアップロードによる一括登録 |
| **Draft作成成功** | `DISCOVERY_ITEM` | `CHECKED` | `DRAFTED` | `SYSTEM` | - | Draft ID発行時 |
| **自動注文取込** | `ORDER` | `SOLD` | `IMPORTED` | `SYSTEM` | `WEBHOOK` | Webhook経由 (Auditログ形式は要確認) |

※ CSV Ingest時の「更新」については、ログ量削減のため `state_transitions` には記録されません（`discovery_items.updated_at` および `snapshot` で追跡）。

## 4. 動作確認手順 (curl)

### A. Discovery機能 (Feature 1)

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

### B. eBay Webhook (Feature 2)

eBayからの通知を模倣して注文を取り込みます。

1. **Webhookシミュレーション**
   ```bash
   curl -X POST http://localhost:8080/ebay/webhook \
     -H "Content-Type: application/json" \
     -d '{"notification": {"data": {"orderId": "11-222-333"}}}'
   ```
   *条件*: 上記OrderIdに対応するスタブ応答が設定されている、または実APIが有効であること。
   *期待値*: 200 OK (初回の取り込み成功)

## 5. 仕様上の注意点

- **Kill Switch**: `PAUSED` 状態の場合、Draft作成APIは 503 Service Unavailable を返します。
- **Profit Score概算**: CSV取り込み時点の Profit Score はあくまで `PricingCalculator` による概算です。Draft作成時に最新の `PricingService` ロジック（Gateチェック含む）で再評価され、最終的な可否が決定されます。
- **Webhook署名**: MVP実装では `X-Ebay-Signature` ヘッダーの検証ロジックは実装されていますが、検証自体は**スキップ**（TODO扱い）されています。本番運用時は有効化を検討してください。
- **Webhook冪等性**: 同じ `orderId` で複数回Webhookを受けても、2回目以降はスキップされ 200 OK を返します。

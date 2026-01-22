# CBS-MVP (Cross-Border Sales MVP)

eBay輸出ビジネス向け在庫・注文・価格管理システム

## 🚀 クイックスタート

### 必要条件
- Java 21
- PostgreSQL
- 環境変数設定（下記参照）

### 起動
```bash
./gradlew bootRun
```

### 環境変数
```bash
# 必須
export JWT_SECRET="your-32-character-minimum-secret"
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/cbs"
export SPRING_DATASOURCE_USERNAME="postgres"
export SPRING_DATASOURCE_PASSWORD="password"

# オプション
export EBAY_CLIENT_ID="your-ebay-client-id"
export EBAY_CLIENT_SECRET="your-ebay-client-secret"
export FX_API_KEY="your-exchangerate-api-key"
```

---

## 📖 API使い方ガイド

### 🔐 認証

#### ログイン
```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

レスポンス:
```json
{"token": "eyJhbGciOiJIUzI1NiJ9..."}
```

以降のAPIは `Authorization: Bearer <token>` ヘッダーを付与

---

### 📦 Candidate（仕入れ候補）

#### CSV一括インポート
```bash
# 通常インポート（重複URL=エラー）
curl -X POST http://localhost:8080/candidates/import-csv \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@candidates.csv"

# 重複スキップモード（運用向け）
curl -X POST "http://localhost:8080/candidates/import-csv?skipDuplicates=true" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@candidates.csv"
```

#### CSVプレビュー（バリデーションのみ）
```bash
curl -X POST http://localhost:8080/candidates/import-csv/preview \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@candidates.csv"
```

#### CSV形式
```csv
sourceUrl,sourcePriceYen,weightKg,sizeTier
https://example.com/item1,5000,1.5,M
https://example.com/item2,8000,2.0,L
```

| カラム | 必須 | 説明 |
|--------|------|------|
| sourceUrl | ✅ | 仕入れ元URL |
| sourcePriceYen | ✅ | 仕入れ価格(円) |
| weightKg | - | 重量(kg) |
| sizeTier | - | S/M/L/XL/XXL |

---

### 💱 為替レート

#### 現在のレート取得
```bash
curl http://localhost:8080/fx/rate \
  -H "Authorization: Bearer $TOKEN"
```

#### レート更新（手動）
```bash
curl -X POST http://localhost:8080/fx/refresh \
  -H "Authorization: Bearer $TOKEN"
```

> **異常検知**: 前回比±5%以上の変動で警告ログが出力されます

---

### 📊 運用ステータス

#### システム状態確認（認証不要）
```bash
curl http://localhost:8080/ops/status
```

#### サマリー取得（X-OPS-KEY必要）
```bash
curl http://localhost:8080/ops/summary \
  -H "X-OPS-KEY: your-ops-key"
```

---

### 📦 注文管理

#### 売れた注文の登録
```bash
curl -X POST http://localhost:8080/orders/sold \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"orderId": "12345", "candidateId": 1, "salePriceUsd": 150.00}'
```

---

### 🏥 ヘルスチェック

```bash
curl http://localhost:8080/health
```

---

## ⚙️ 設定

### application.yml 主要設定

```yaml
# JWT
jwt:
  secret: ${JWT_SECRET}  # 最低32文字
  expiration-hours: 24

# eBay
ebay:
  client-id: ${EBAY_CLIENT_ID}
  client-secret: ${EBAY_CLIENT_SECRET}
  sandbox: true
  fulfillment-policy-id: ${EBAY_FULFILLMENT_POLICY_ID:}
  payment-policy-id: ${EBAY_PAYMENT_POLICY_ID:}
  return-policy-id: ${EBAY_RETURN_POLICY_ID:}
  webhook-verification-token: ${EBAY_WEBHOOK_TOKEN:}

# FX
fx:
  api-key: ${FX_API_KEY}
  base-currency: USD
  target-currency: JPY
```

---

## 🔒 セキュリティ

- **JWT認証**: ログイン後にトークンを取得し、ヘッダーで送信
- **X-OPS-KEY**: 運用系エンドポイント用の固定キー認証
- **Webhook署名検証**: eBayからの通知はHMAC-SHA256で検証

---

## 📁 プロジェクト構成

```
src/main/java/com/example/cbs_mvp/
├── candidate/     # 仕入れ候補管理
├── ebay/          # eBay連携
├── entity/        # データベースエンティティ
├── fx/            # 為替レート
├── ops/           # 運用管理
├── pricing/       # 価格計算
├── repo/          # リポジトリ
├── security/      # 認証・認可
└── service/       # ビジネスロジック
```

---

## 📝 ライセンス

MIT

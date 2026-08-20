# 設計と実装方針

この文書は、実装前に作成された設計資料一式を、リポジトリ内で参照できる形に要約したものです。数値や UI が「暫定」と書かれている項目は、実機確認後に変更できます。

## 目的

ブラウザ、SNS、チャット、画像ビューアなどで QR コードを見つけた際、アプリへ切り替えず、音量上ボタンの高速 4 連打だけで読み取ります。

## 決定済み

- 対象キーは `KEYCODE_VOLUME_UP`
- 1 タップは `ACTION_DOWN(repeatCount = 0)` の後に `ACTION_UP` が来た場合
- 4 タップ、各 DOWN 間隔 220 ms 以下、最初の DOWN から最後の UP まで 700 ms 以下
- repeat、別キー、不正な DOWN / UP 順序、タイムアウトで候補をリセット
- MVP は `onKeyEvent()` から `false` を返し、通常の音量処理を Android に任せる
- API 30 以降は `AccessibilityService.takeScreenshot()`
- API 24〜29 は既存の MediaProjection と CaptureService
- キャプチャ結果の共通境界は `Bitmap`
- QR デコードは既存 `BarcodeUtil.decodeQRCode()` を再利用
- 1 件は即処理、複数件は元アプリの位置選択 UI
- 自動コピーは設定可能。MVP の暫定初期値は OFF

## 構成

```text
QrAccessibilityService
  -> VolumeQuadTapDetector
  -> ScreenCaptureFacade
       -> AccessibilityScreenCapture (API 30+)
       -> CaptureService / MediaProjection (API 24-29)
  -> QrDecoder
  -> QrResultDispatcher
       -> ResultHandler (0/1件)
       -> MainActivity (複数選択)
```

## 元実装からの扱い

| 対象 | 方針 |
|---|---|
| `LICENSE` | そのまま維持 |
| `BarcodeUtil.decodeQRCode()` | 中核として維持 |
| `Result.toBarcodeResult()` | 維持 |
| `CaptureService` | API 24〜29 のフォールバックへ限定 |
| `MainActivity` | 初期設定と複数 QR 選択へ再構成 |
| `BarcodeResult` と座標 UI | 維持 |
| FileProvider と画像一時保存 | 複数選択・既存連携のため当面維持 |
| WeChat / Alipay 特殊処理 | MVP では互換経路を維持。実機評価後に再判断 |

## 実機で確認する事項

1. 220 / 700 ms の操作性と通常の音量操作による誤発動
2. 音量が 4 段上がる副作用を許容できるか
3. 音量パネルが QR を隠すか
4. 端末再起動、プロセス終了、画面ロック後の MediaProjection 再許可 UX
5. プレーンテキスト表示を Toast から小型オーバーレイへ変える必要があるか
6. 自動コピーの既定値
7. WeChat / Alipay 特殊処理を一般 URL 処理へ統一するか
8. API 34 以降でウィンドウ単位スクリーンショットを使うか

## 検証状況と次作業

ローカル JVM 単体テスト、`assembleDebug`、`assembleRelease`、`lintDebug` は完了しています。静的解析はエラー 0 件で、残る 9 件は元実装の WindowInsets 取得と未使用リソースに関する警告です。

次は以下を実機で確認します。

1. API 30 以降で四連打、スクリーンショット、単一 QR を確認
2. 複数 QR のマーカー位置と画面回転を確認
3. API 24〜29 の許可失効条件を確認
4. timing と UX の調整後、toolchain と targetSdk を別変更で更新

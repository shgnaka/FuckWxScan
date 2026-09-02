# 設計と実装方針

この文書は、実装前に作成された設計資料一式を、リポジトリ内で参照できる形に要約したものです。数値や UI が「暫定」と書かれている項目は、実機確認後に変更できます。

## 目的

ブラウザ、SNS、チャット、画像ビューアなどで QR コードを見つけた際、アプリへ切り替えず、設定した物理操作で読み取ります。

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
- 画面取得はジェスチャー 1 回につき 1 回だけ行い、取得した同じ画像を QR 読み取りとスクショ保存に使う
- QR が 0 件なら、選択画面を出さずに取得画像をスクショとして保存
- QR が 1 件以上なら、「QR を読み取る」と「スクショを保存」の 2 択を表示
- 「QR を読み取る」は 1 件なら既存の結果処理、複数件なら元アプリの位置選択 UI
- 「スクショを保存」は取得画像を `Pictures/Screenshots` へ保存し、再キャプチャしない
- 選択画面の戻る・外側タップ・キャンセルでは、一時画像を削除
- 自動コピーは設定可能。MVP の暫定初期値は OFF
- 実験機能として線形加速度による振り判定と、ジャイロによる手首ひねり判定を個別に設定可能
- 手首ひねりは、一方向への回転、停止、同じ軸の反対方向への回転を短時間に行った場合だけ成立
- ジャイロがない端末では手首ひねりを利用不可とし、音量操作と振り判定は維持

## 構成

```text
QrAccessibilityService
  -> VolumeQuadTapDetector
  -> ShakeGestureDetector
  -> WristTwistGestureDetector
  -> ScreenCaptureFacade
       -> AccessibilityScreenCapture (API 30+)
       -> CaptureService / MediaProjection (API 24-29)
  -> QrDecoder
  -> ScanFlowPolicy
       -> MediaStoreScreenshotSaver (0件)
       -> ScreenshotActionActivity (1件以上)
            -> ResultHandler (1件)
            -> QrSelectionActivity (複数選択)
            -> MediaStoreScreenshotSaver (保存)
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
| FileProvider と画像一時保存 | 選択後の保存と Alipay 互換経路のため維持。キャンセル時は削除 |
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
9. 手首ひねりの回転速度、積算角度、往路・復路時間を実機ログから調整
10. 振り判定と手首ひねり判定を同時に有効にした場合の誤発動と消費電力

## 検証状況と次作業

`ScanFlowPolicyTest` は、QR 0 件、1 件、複数件、および各選択肢の分岐を検証します。今回の環境では Gradle 7.4 の配布物を取得できないため、変更後の JVM 単体テストと APK ビルドは未実行です。`git diff --check` と対象コードの静的確認は完了しています。

次は以下を実機と CI で確認します。

1. API 30 以降で四連打、スクリーンショット、単一 QR の 2 択を確認
2. 「QR を読み取る」で既存結果処理、「スクショを保存」で画像アプリから開けることを確認
3. 複数 QR の選択、戻る、外側タップで一時画像が破棄されることを確認
4. QR なしで自動保存され、選択画面が出ないことを確認
5. API 24〜29 の許可失効条件を確認
6. 実機確認後、必要なら表示文言と保存先を別変更で調整

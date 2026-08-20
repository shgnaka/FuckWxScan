<!--
  Modified from li-yu/FuckWxScan by contributors to shgnaka/FuckWxScan in 2026.
  The original side-bar-launch documentation was replaced for the volume gesture fork.
-->
# QR Volume Scanner

Android に「画面内の QR コードを読む」ための物理ショートカットを追加するユーティリティです。
音量上ボタンを高速で 4 回押すと、現在表示されている画面を取得し、端末内で QR コードを認識します。

このプロジェクトは [li-yu/FuckWxScan](https://github.com/li-yu/FuckWxScan) のフォークです。
既存の MediaProjection、ZXing デコード、複数 QR 選択 UI を再利用しつつ、AccessibilityService による物理ジェスチャーを追加しています。

> [!WARNING]
> 現在は開発中です。実機テスト前のため、日常利用向けのリリースはまだありません。

## 現在の実装

- 音量上ボタンの高速 4 連打を検出
- 各タップ間隔 220 ms 以下、全体 700 ms 以下
- 長押しの repeat イベント、別キー、不正な DOWN / UP 順序を除外
- キーイベントを消費せず、Android 標準の音量操作を維持
- Android 11 以降は AccessibilityService.takeScreenshot()
- Android 7〜10 は既存 MediaProjection をフォールバック利用
- 既存 ZXing の複数 QR、反転、明度補正デコードを再利用
- 0 件、1 件、複数件の結果処理
- URL 起動、プレーンテキスト表示、自動コピー設定
- ユーザー補助サービスと旧 Android 用画面取得の初期設定画面

## セットアップ

1. アプリをインストールして起動します。
2. 「ユーザー補助設定を開く」を押し、`QR Volume Scanner` を有効にします。
3. Android 7〜10 では、アプリに戻って「画面取得を準備する」も実行します。
4. QR コードが表示された状態で、音量上ボタンを素早く 4 回押します。

通常の音量イベントは Android に渡すため、MVP では音量も 4 段上がります。また、音量パネルがスクリーンショットへ写り込む可能性があります。

## 対応範囲

| Android | API | 画面取得 |
|---|---:|---|
| 7〜10 | 24〜29 | MediaProjection |
| 11 以降 | 30 以降 | AccessibilityService.takeScreenshot() |

`minSdk 24`、`compileSdk 33`、`targetSdk 33` を維持したまま機能改造を進めています。SDK とビルドツールの更新は、MVP の実機確認後に別変更として行う方針です。

## プライバシー

QR デコード自体は ZXing により端末内で完結し、このアプリ独自のサーバーへ画像や結果を送信しません。複数 QR の選択や Alipay 互換経路に必要な場合は、アプリ専用領域へスクリーンショットを保存します。Alipay 互換経路を選んだ場合は、その画像を Alipay アプリへ明示的に引き渡します。また、URL を開いた後の通信とデータ処理は遷移先アプリの規約に従います。

## 開発資料

- [設計と未決定事項](docs/DESIGN.md)
- [権利・ライセンス整理](docs/RIGHTS_AND_LICENSES.md)

## ライセンス

元の `LICENSE` を変更せず維持し、本フォークも Apache License 2.0 の条件に従います。再利用箇所、変更表示、依存ライセンス、配布時の確認事項は [権利・ライセンス整理](docs/RIGHTS_AND_LICENSES.md) を参照してください。

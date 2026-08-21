# 権利・ライセンス整理

この文書は開発・配布時の確認用メモであり、個別案件への法的助言ではありません。最終的な公開・販売条件に不安がある場合は、専門家へ確認してください。

## 結論

このフォークは、元リポジトリ [li-yu/FuckWxScan](https://github.com/li-yu/FuckWxScan) の Apache License 2.0 に基づいて、利用、改変、再配布、商用利用できます。元リポジトリの `LICENSE` は変更せず残しています。

Apache License 2.0 は、著作権ライセンスと一定の特許ライセンスを付与する permissive license です。GPL のように派生物全体のソース公開を強制するライセンスではありません。一方、配布時には表示と文書の義務があります。

## 元リポジトリから継承したもの

- Kotlin / Android のプロジェクト構成
- MediaProjection と CaptureService による画面取得
- ZXing を使う `BarcodeUtil.decodeQRCode()`
- `BarcodeResult` と QR 座標の算出
- 複数 QR の位置選択 UI
- URL、WeChat、Alipay の既存結果処理の考え方
- Compose テーマ、ランチャー画像などのリポジトリ内アセット

## このフォークで追加・変更したもの

- 音量上ボタンの 4 連打検出器と単体テスト
- 加速度・ジャイロを使う物理ジェスチャー検出器、診断表示、単体テスト
- AccessibilityService の登録とキーイベント監視
- API 30 以降の `takeScreenshot()` 実装
- キャプチャ、デコード、結果処理の責務分離
- 自動コピー設定と初期設定画面
- API 24〜29 のフォールバック起動経路
- 日本語 UI、README、設計・権利文書

変更した元ファイルには、2026 年の変更であることを示すコメントを付けています。新規ソースには `SPDX-License-Identifier: Apache-2.0` を付けています。Git 履歴でも元のコミットと本フォークの差分を追跡できます。

## 配布時に必要なこと

ソース、APK、またはその両方を第三者へ配布する場合は、少なくとも次を確認します。

1. 受領者へ Apache License 2.0 の全文を渡す。リポジトリと配布アーカイブに `LICENSE` を含める。
2. 改変したファイルに、変更したことが分かる目立つ表示を残す。
3. 元コードにある著作権、特許、商標、帰属表示のうち、派生物に関係するものを削除しない。
4. このフォークの `NOTICE` を、配布物または付属文書の読める場所に含める。
5. 元作者や第三者が本フォークを推奨・保証しているように表示しない。

APK 単体で配布する場合も、`LICENSE` と `NOTICE` を同梱した ZIP、配布ページ、またはアプリ内の法的情報画面など、受領者が確認できる方法を用意します。リリース作成時に改めて確認してください。

根拠は [Apache License 2.0 本文](https://www.apache.org/licenses/LICENSE-2.0) の第 2〜4 節と第 6 節です。元リポジトリにはフォーク作成時点で独立した `NOTICE` ファイルがありませんでしたが、本フォークでは由来と主要変更を明確にするため追加しています。

## 依存関係

| 依存 | 用途 | ライセンス上の扱い |
|---|---|---|
| AndroidX Core / Lifecycle / Activity / Compose | Android UI とライフサイクル | Android Open Source Project 系。主に Apache License 2.0 |
| ZXing Core 3.5.1 | QR デコード | Apache License 2.0 |
| Kotlin / kotlinx.coroutines | Kotlin と非同期処理 | Apache License 2.0 |
| JUnit 4.13.2 | ローカル単体テストのみ | Eclipse Public License 1.0。APK には組み込まない |

確認先:

- [Android Open Source Project のライセンス説明](https://source.android.com/docs/setup/about/licenses)
- [ZXing の LICENSE](https://github.com/zxing/zxing/blob/master/LICENSE)
- [Kotlin の LICENSE](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt)
- [kotlinx.coroutines の LICENSE](https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt)
- [JUnit 4 の LICENSE](https://github.com/junit-team/junit4/blob/main/LICENSE-junit.txt)

依存バージョンを追加・更新した場合は、この表とリリース物の第三者表示も更新します。

## 名前、アイコン、商標

Apache License 2.0 は、出所説明に必要な範囲を除き、元作者や第三者の商標利用権を付与しません。本フォークでは表示名を `QR Volume Scanner` に変更しました。

元リポジトリ由来のランチャー画像は著作物としてリポジトリのライセンス対象と考えられますが、広く配布する前に独自アイコンへ差し替えるのが安全です。また、`WeChat`、`Alipay`、Android などの名称・パッケージ名は互換処理の説明にのみ使い、提携や推奨を示す表現やロゴ利用は避けます。

## アプリ ID と署名

現在の `applicationId` とパッケージ名は元実装の `cn.liyuyu.fuckwxscan` を維持しています。技術検証には使えますが、正式配布前には固有の ID へ変更し、元アプリと異なる署名鍵・更新経路を使います。元作者のリリース APK を本フォークの署名で上書き更新することはできません。

## プライバシーとコンテンツ

画面取得には、表示中の個人情報や第三者コンテンツが含まれる可能性があります。本実装の QR デコードはローカルで行い、このアプリ独自のサーバーへ送信しません。ただし、Alipay 互換経路では読み取り対象画像を Alipay アプリへ渡し、URL を開いた後は遷移先アプリが通信する可能性があります。ユーザーが読み取った URL やテキスト自体の利用権、各遷移先の規約、スクリーンショット対象コンテンツの権利は、Apache License 2.0 とは別問題です。

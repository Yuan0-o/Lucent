# Release notes: the house format

Every release body follows the same shape, so the languages sit in the same place every time.

```
# <English subtitle, without the "Lucent x.y.z —" prefix>

<English notes, roughly 500 words, British English, dry, and never negative>

---

<details>
<summary>简体中文</summary>

# <Chinese title>

<Chinese notes>

</details>

---

<details>
<summary>日本語</summary>

# <Japanese title>

<Japanese notes>

</details>

---

<details>
<summary>한국어</summary>

# <Korean title>

<Korean notes>

</details>

---

**Build info**

- Android APK: <size> MiB
- Windows installer: <size> MiB
- Built from commit `<sha>`
- Build log: <run url>
```

The rules the checker enforces:

- One first-level title at the very top, and it is the subtitle alone, not the whole release name.
- Three separate `<details>` blocks, summarised exactly `简体中文`, `日本語`, `한국어`, in that order, each opening with its own `# ` title. Never one block with `###` sections inside.
- A `---` rule before every block, and one closing the English notes. The release workflow appends the
  closing rule with the build info block, so notes handed to it carry three rules and the published
  body carries four.
- The English notes stay between 300 and 700 words.
- If a build info block is present it carries the APK size, the installer size and the commit.

`python3 tools/release_notes_check.py <file>` checks a set of notes; the release workflow runs it on
the notes it is given, and refuses to build a release whose notes do not match.

---

The example below is the shipped 3.0.0 body, kept as a reference.

# Third Hand: The Notebook Becomes a Workshop

Lucent 3.0.0 turns the assistant from a well-mannered conversationalist into something with a workshop, a key to it, and a written record of everything it does in there. Nothing about the notes and tasks you already keep has changed; there is simply a great deal more behind the door now.

**A toolkit, not a toybox.** The assistant gets a workspace folder of your choosing and real tools: list, search, read, write, edit with a proper diff, copy, move, delete, zip, and a snapshot of every file before it is touched, so any mistake can be walked back. Long jobs become background tasks you can poll and stop, and commands run with a working directory and a timeout.

**Documents, made properly.** Word, Excel and PowerPoint files are now created and edited on your device, in formats written by Lucent itself — no office library, no upload, no conversion service. Word documents get styles, tables, images, headers, footers, page numbers and a table of contents; spreadsheets get formulas, formats, merges, frozen panes, filters, conditional colours and charts; presentations get masters, layouts, themes, speaker notes and editable charts. PDFs can be read, searched, split, merged and drawn as pictures — and a rendered page is something the assistant can look at, which is how it notices that slide seven has spilled its text.

**Code, kept honest.** Git has a full set of guarded tools — status, diff, log, blame, branch, commit, stash, merge, rebase, patch, fetch, pull and push — and the dangerous corners are refused rather than regretted. GitHub follows: repositories, files, issues, pull requests, diffs, branches, releases, code search, workflow runs and CI logs, so a failing build can be read and fixed rather than merely described.

**A workshop with a rulebook.** Permissions are their own page: read, write, delete, commands, network, Git, GitHub, browsing, the world outside the workspace and the device itself, each set to allow, ask or block. Deleting and installing ask first by default. Every call lands in an activity log with what was asked, what happened and how long it took, and the log is yours to read or clear.

**Heavy things stay outside.** The installer is not asked to carry a Linux userland, Python, LibreOffice, Node.js, a browser engine, OCR or media tools. They are plugins, listed with their size and licence, downloaded only when you want them, from the project's own servers or a fast mirror, whichever answers first. The assistant can install them too, once it has told you how big they are.

**Sub-agents, for the heavy days.** A switch in Personalisation lets the assistant call for helpers: independent tasks go to sub-agents that work in the background with their own context and report back, so a long investigation no longer eats the conversation you are having.

**Three kinds of memory, and a plan.** This conversation, this project and you are remembered separately. Anything with more than two steps gets a plan you can watch, and skills are written down, so your conventions are learned once.

**On the phone, the workshop reaches the device.** Read the screen, tap, type, swipe, take a screenshot, list and open apps, read notifications, use the clipboard, the flashlight and the sensors. Termux handles real command-line tools, and MCP lets you bolt on anything else.

The Android download is now one universal build for arm64-v8a, armeabi-v7a and x86_64.

---

<details>
<summary>简体中文</summary>

# 第三只手：笔记本变成了一间工作室

Lucent 3.0.0 把助手从一位彬彬有礼的聊天对象，变成了一间工作室的主人：有钥匙，也有把里面每件事都记下来的账本。你原有的笔记与任务没有任何变化，只是那扇门后面丰富了许多。

**工具箱，不是玩具箱。** 助手得到你指定的工作区文件夹和一套真正的工具：列目录、搜索、读取、写入、带差异对比的编辑、复制、移动、删除、打包，以及每次改动前的自动快照，出错随时退回。耗时的工作变成后台任务，可以随时查看和终止；命令带工作目录、超时和完整输出。

**文档，正经地做。** Word、Excel、PowerPoint 现在由 Lucent 本机生成和修改，文件格式由 Lucent 自己写出——不依赖任何 Office 库，不上传，不经过转换服务。Word 有样式、表格、图片、页眉页脚、页码和目录；表格有公式、格式、合并、冻结窗格、筛选、条件颜色和图表；演示文稿有母版、版式、主题、备注和可编辑图表。PDF 可以读取、搜索、拆分、合并并渲染成图片，而渲染出来的页面助手能亲眼看——第七页文字溢出来了，它看得见。

**代码，规规矩矩。** Git 工具齐备：状态、差异、日志、追溯、分支、提交、暂存、合并、变基、补丁、拉取、推送，危险的动作直接拒绝而不是事后道歉。GitHub 亦然：仓库、文件、议题、拉取请求、差异、分支、发布、代码搜索、流水线与构建日志，失败的构建可以读、可以修。

**有规矩的工作室。** 权限自成一面：读取、写入、删除、命令、网络、Git、GitHub、浏览、工作区之外、以及设备本身，每项都可以设为允许、询问或禁止。删除与安装默认先问。每次调用都会写入操作记录，写了什么、结果如何、花了多久，随时可看可清。

**重的东西留在门外。** 安装包不背 Linux 环境、Python、LibreOffice、Node.js、浏览器内核、OCR 和媒体工具。它们是插件，标明体积与许可证，只在你需要时下载，官方地址与国内镜像自动测速取快。助手也可以自己装，前提是先告诉你要下多大。

**子助手，忙的时候用。** 个性化里有一个开关：打开后助手可以召唤帮手，独立的任务交给在后台用自己上下文工作的子助手，完成后汇报，长时间调查不再占用你正在聊的这一段。

**三种记忆，一份计划。** 本次对话、这个项目、以及你本人，分开记住。超过两步的任务会给出可实时查看的计划；技能会被写下来，你的习惯只需要教一次。

**在手机上，工作室延伸到设备。** 读屏、点击、输入、滑动、截图、列出并打开应用、读取通知，以及剪贴板、手电筒和传感器。Termux 负责真正的命令行工具，MCP 还能把别的能力接进来。

安卓版现在是一个通用包，同时支持 arm64-v8a、armeabi-v7a 与 x86_64。

</details>

---

<details>
<summary>日本語</summary>

# 第三の手：ノートが作業場になる

Lucent 3.0.0 は、行儀のよい話し相手だった assistant を、作業場とその鍵、そして中で起きたことをすべて書き留める帳簿を持つ存在に変えます。すでにお使いのメモとタスクに変更はありません。扉の向こうが、ずいぶん広くなっただけです。

**道具箱であって、おもちゃ箱ではありません。** assistant は指定したワークスペースと本物の道具を手にします。一覧、検索、読み取り、書き込み、差分つきの編集、コピー、移動、削除、圧縮、そして変更前のスナップショット。長時間の処理はバックグラウンドの仕事になり、確認も停止もできます。コマンドは作業ディレクトリと制限時間つきで実行されます。

**文書は、きちんと。** Word・Excel・PowerPoint は端末上で Lucent 自身が生成・編集します。Office ライブラリも、アップロードも、変換サービスも不要です。Word にはスタイル、表、画像、ヘッダー、フッター、ページ番号、目次。表計算には数式、書式、結合、ウィンドウ枠の固定、フィルター、条件付き書式、グラフ。プレゼンテーションにはマスター、レイアウト、テーマ、発表者ノート、編集可能なグラフ。PDF は読み取り、検索、分割、結合、画像化ができ、描画したページを assistant が実際に見て確認できます。

**コードは、誠実に。** Git の道具が一通りそろい、危険な操作は後悔ではなく拒否で扱います。GitHub も同様に、リポジトリ、ファイル、イシュー、プルリクエスト、差分、ブランチ、リリース、コード検索、ワークフローと CI ログまで扱えます。

**規約のある作業場。** 権限は専用のページにまとめ、読み取り・書き込み・削除・コマンド・ネットワーク・Git・GitHub・閲覧・ワークスペース外・端末そのものを、許可・確認・禁止から選べます。削除とインストールは既定で確認を求め、すべての呼び出しは操作ログに残ります。

**重いものは外に。** Linux 環境、Python、LibreOffice、Node.js、ブラウザーエンジン、OCR、メディアツールはインストーラーには入りません。プラグインとして大きさとライセンスを明示し、必要なときだけ、公式配布元か高速なミラーから取得します。assistant 自身も、大きさを伝えたうえでなら導入できます。

**忙しい日のためのサブエージェント。** パーソナライズのスイッチを入れると、独立した作業をバックグラウンドの子エージェントに任せ、報告を受け取れます。

**三つの記憶と一つの計画。** この会話、このプロジェクト、あなた自身を別々に覚えます。二段階を超える作業には進捗の見える計画を出し、スキルは書き留められます。

**スマートフォンでは、作業場が端末に届きます。** 画面の読み取り、タップ、入力、スワイプ、スクリーンショット、アプリの一覧と起動、通知の読み取り、クリップボード、ライト、センサー。Termux が本物のコマンドラインツールを担い、MCP でさらに拡張できます。

Android 版は arm64-v8a、armeabi-v7a、x86_64 に対応する単一のユニバーサルビルドになりました。

</details>

---

<details>
<summary>한국어</summary>

# 세 번째 손: 노트가 작업장이 되다

Lucent 3.0.0은 예의 바른 대화 상대였던 assistant를 작업실과 그 열쇠, 그리고 그 안에서 벌어진 모든 일을 기록하는 장부를 가진 존재로 바꿉니다. 이미 쓰고 계신 노트와 할 일은 달라지지 않았습니다. 문 뒤가 훨씬 넓어졌을 뿐입니다.

**도구함이지 장난감 상자가 아닙니다.** assistant는 지정한 작업 공간과 진짜 도구를 얻습니다. 목록, 검색, 읽기, 쓰기, 차이를 보여 주는 편집, 복사, 이동, 삭제, 압축, 그리고 변경 전 스냅숏까지. 오래 걸리는 일은 백그라운드 작업이 되어 확인하고 멈출 수 있으며, 명령은 작업 디렉터리와 제한 시간을 가지고 실행됩니다.

**문서는 제대로.** Word, Excel, PowerPoint 파일을 기기에서 Lucent가 직접 만들고 고칩니다. Office 라이브러리도, 업로드도, 변환 서비스도 없습니다. Word에는 스타일, 표, 이미지, 머리말, 꼬리말, 페이지 번호, 목차가 들어가고, 스프레드시트에는 수식, 서식, 병합, 창 고정, 필터, 조건부 서식, 차트가, 프레젠테이션에는 마스터, 레이아웃, 테마, 발표자 노트, 편집 가능한 차트가 들어갑니다. PDF는 읽기, 검색, 분할, 병합, 이미지 변환이 가능하고, 그려진 페이지를 assistant가 실제로 보며 확인합니다.

**코드는 정직하게.** Git 도구가 모두 갖춰져 있고 위험한 동작은 후회가 아니라 거부로 처리합니다. GitHub도 저장소, 파일, 이슈, 풀 리퀘스트, 차이, 브랜치, 릴리스, 코드 검색, 워크플로와 CI 로그까지 다룹니다.

**규칙이 있는 작업실.** 권한은 전용 페이지에서 읽기, 쓰기, 삭제, 명령, 네트워크, Git, GitHub, 브라우징, 작업 공간 밖, 기기 자체를 허용·확인·차단으로 정합니다. 삭제와 설치는 기본적으로 먼저 묻고, 모든 호출은 작업 기록에 남습니다.

**무거운 것은 밖에.** Linux 환경, Python, LibreOffice, Node.js, 브라우저 엔진, OCR, 미디어 도구는 설치 파일에 들어가지 않습니다. 플러그인으로 크기와 라이선스를 밝히고, 필요할 때만 공식 배포처나 빠른 미러에서 받습니다. assistant도 크기를 알려 준 뒤에야 직접 설치할 수 있습니다.

**바쁜 날을 위한 하위 에이전트.** 개인 설정의 스위치를 켜면 독립적인 작업을 백그라운드에서 자기 맥락으로 처리하는 하위 에이전트에게 맡기고 보고를 받습니다.

**세 가지 기억과 하나의 계획.** 이 대화, 이 프로젝트, 그리고 사용자를 따로 기억합니다. 두 단계를 넘는 작업에는 진행 상황이 보이는 계획을 내놓고, 스킬은 기록해 둡니다.

**휴대폰에서는 작업실이 기기까지 닿습니다.** 화면 읽기, 탭, 입력, 스와이프, 스크린샷, 앱 목록과 실행, 알림 읽기, 클립보드, 손전등, 센서. Termux가 진짜 명령줄 도구를 맡고 MCP로 더 붙일 수 있습니다.

Android 버전은 arm64-v8a, armeabi-v7a, x86_64를 지원하는 단일 유니버설 빌드가 되었습니다.

</details>

---

**Build info**

- Android APK: 58.73 MiB
- Windows installer: 165.37 MiB
- Built from commit `ba4dfd072069b4f7d95978063c327da79dead64e`
- Build log: https://github.com/Yuan0-o/Lucent/actions/runs/36127855604

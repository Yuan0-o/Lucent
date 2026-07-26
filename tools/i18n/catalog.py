# -*- coding: utf-8 -*-
# Lucent translation catalog. ENTRIES: (key_or_signature, en, zh, ja, ko).
# {param} inside templates becomes ${param} in generated Kotlin.
# None => no override => falls back to English.
# A bare string item is a comment line, emitted verbatim (indented) into Tr only.
# NOTE: gen_i18n.py writes ONLY the Android I18n.kt. The desktop file
# (desktop/src/main/kotlin/com/lucent/app/i18n/I18n.kt) is a hand-maintained fork that also
# carries desktop-only strings (Windows Hello, PDF font hints, ...) — sync it by hand.

ENTRIES = [

    # ---- Task 10: exported document labels (PDF / DOCX / XLSX) ----
    # These were hardcoded English inside DocumentExport, so a Chinese user exporting a Chinese
    # note got a PDF whose headings said "Updated" and "Subtasks".
    ("exportDocNotesTitle", "Lucent notes", "Lucent 笔记", "Lucent メモ", "Lucent 노트"),
    ("exportDocTasksTitle", "Lucent tasks", "Lucent 任务", "Lucent タスク", "Lucent 할 일"),
    ("exportDocNoteCount(count: Int)", "{count} notes", "{count} 条笔记", "メモ {count} 件", "노트 {count}개"),
    ("exportDocTaskCount(count: Int)", "{count} tasks", "{count} 个任务", "タスク {count} 件", "할 일 {count}개"),
    ("exportDocExportedAt(time: String)", "exported {time}", "导出于 {time}", "{time} に書き出し", "{time}에 내보냄"),
    ("exportDocNoNotes", "No notes yet.", "还没有笔记。", "メモはまだありません。", "아직 노트가 없습니다."),
    ("exportDocNoTasks", "No tasks yet.", "还没有任务。", "タスクはまだありません。", "아직 할 일이 없습니다."),
    ("exportDocUpdated(time: String)", "Updated {time}", "更新于 {time}", "更新 {time}", "업데이트 {time}"),
    ("exportDocCreated(time: String)", "Created {time}", "创建于 {time}", "作成 {time}", "생성 {time}"),
    ("exportDocDue(time: String)", "Due {time}", "截止 {time}", "期限 {time}", "기한 {time}"),
    ("exportDocPinned", "Pinned", "已置顶", "ピン留め", "고정됨"),
    ("exportDocArchived", "Archived", "已归档", "アーカイブ済み", "보관됨"),
    ("exportDocDone", "Done", "已完成", "完了", "완료"),
    ("exportDocOpen", "Open", "未完成", "未完了", "미완료"),
    ("exportDocPriority(name: String)", "Priority: {name}", "优先级：{name}", "優先度：{name}", "우선순위: {name}"),
    ("exportDocRepeats(rule: String)", "Repeats: {rule}", "重复：{rule}", "繰り返し：{rule}", "반복: {rule}"),
    ("exportDocChecklist", "Checklist", "清单", "チェックリスト", "체크리스트"),
    ("exportDocSubtasks", "Subtasks", "子任务", "サブタスク", "하위 작업"),
    ("exportDocUntitledTask", "Untitled task", "无标题任务", "無題のタスク", "제목 없는 할 일"),
    ("exportDocAttachmentsLine(names: String)", "Attachments: {names}", "附件：{names}", "添付：{names}", "첨부: {names}"),
    ("exportDocAttachmentsNote", "Attachments are listed by name but not embedded. Use the .lcb backup if you need the files themselves.", "附件仅列出文件名，不会嵌入文档。需要附件本身请使用 .lcb 备份。", "添付ファイルは名前のみ記載され、埋め込まれません。ファイル本体が必要な場合は .lcb バックアップをお使いください。", "첨부 파일은 이름만 기재되며 포함되지 않습니다. 파일 자체가 필요하면 .lcb 백업을 사용하세요."),
    # Round R1 - the Markdown writer was the one export format still hardcoded in English; these
    # cover the strings it needed that the other formats did not already have.
    ("exportDocEmptyChecklist", "(empty checklist)", "（空清单）", "（空のチェックリスト）", "(빈 체크리스트)"),
    ("exportDocDoodleLine(names: String)", "Doodles: {names}", "涂鸦：{names}", "落書き：{names}", "낙서: {names}"),
    ("exportDocDoodleCanvases(count: Int)", "{count} doodle canvases", "{count} 块涂鸦画布", "落書きキャンバス {count} 枚", "낙서 캔버스 {count}개"),
    ("exportDocYes", "Yes", "是", "はい", "예"),
    # Spreadsheet column headers
    ("exportColTitle", "Title", "标题", "タイトル", "제목"),
    ("exportColUpdated", "Updated", "更新时间", "更新日時", "업데이트"),
    ("exportColCreated", "Created", "创建时间", "作成日時", "생성"),
    ("exportColTags", "Tags", "标签", "タグ", "태그"),
    ("exportColPinned", "Pinned", "置顶", "ピン留め", "고정"),
    ("exportColArchived", "Archived", "归档", "アーカイブ", "보관"),
    ("exportColContent", "Content", "内容", "内容", "내용"),
    ("exportColAttachments", "Attachments", "附件", "添付", "첨부"),
    ("exportColStatus", "Status", "状态", "状態", "상태"),
    ("exportColDue", "Due", "截止", "期限", "기한"),
    ("exportColPriority", "Priority", "优先级", "優先度", "우선순위"),
    ("exportColRepeat", "Repeat", "重复", "繰り返し", "반복"),
    ("exportColDetails", "Details", "详情", "詳細", "세부"),
    ("exportColSubtasks", "Subtasks", "子任务", "サブタスク", "하위 작업"),
    ("exportPdfMissingCjkFont", "Some characters could not be drawn because no font on this machine covers them. Import a font in Settings, or export as .docx.", "部分字符无法绘制，因为本机没有覆盖这些字形的字体。请在设置里导入字体，或改用 .docx 导出。", "この端末に該当する字形を含むフォントがないため、一部の文字を描画できませんでした。設定でフォントを読み込むか、.docx で書き出してください。", "이 컴퓨터에 해당 글자를 포함한 글꼴이 없어 일부 문자를 그릴 수 없었습니다. 설정에서 글꼴을 가져오거나 .docx로 내보내세요."),

    # ---- Task 7: automatic backup ----
    ("autoBackupTitle", "Automatic backup", "自动备份", "自動バックアップ", "자동 백업"),
    ("autoBackupDesc", "Backs everything up to a folder you choose, on a schedule. Local model files are never included.", "按设定的间隔自动备份到你选择的文件夹。本地模型文件不会包含在内。", "選んだフォルダーへ、設定した間隔で自動的にバックアップします。ローカルモデルのファイルは含まれません。", "선택한 폴더에 설정한 간격으로 자동 백업합니다. 로컬 모델 파일은 포함되지 않습니다."),
    ("autoBackupFolder", "Backup folder", "备份文件夹", "バックアップ先フォルダー", "백업 폴더"),
    ("autoBackupChooseFolder", "Choose folder", "选择文件夹", "フォルダーを選択", "폴더 선택"),
    ("autoBackupInterval", "How often", "备份间隔", "間隔", "주기"),
    ("autoBackupEvery(hours: Int)", "Every {hours} hours", "每 {hours} 小时", "{hours} 時間ごと", "{hours}시간마다"),
    ("autoBackupEveryDay", "Once a day", "每天一次", "1 日 1 回", "하루에 한 번"),
    ("autoBackupEveryWeek", "Once a week", "每周一次", "週 1 回", "일주일에 한 번"),
    ("autoBackupKeep", "Backups to keep", "保留备份份数", "保持するバックアップ数", "보관할 백업 수"),
    ("autoBackupLastRun(time: String)", "Last backup: {time}", "上次备份：{time}", "前回のバックアップ：{time}", "마지막 백업: {time}"),
    ("autoBackupNever", "No automatic backup has run yet", "尚未执行过自动备份", "自動バックアップはまだ実行されていません", "자동 백업이 아직 실행되지 않았습니다"),
    ("autoBackupNeedsFolder", "Choose a folder first", "请先选择文件夹", "先にフォルダーを選択してください", "먼저 폴더를 선택하세요"),
    ("autoBackupWhyNoModels", "Local model files can be several gigabytes and can be downloaded again, so they are left out. Everything else — notes, tasks, chats, settings and attachments — is included.", "本地模型文件可能有数 GB，且可以重新下载，因此不纳入自动备份。其余内容——笔记、任务、聊天、设置和附件——全部包含。", "ローカルモデルのファイルは数ギガバイトになることがあり、再ダウンロードも可能なため除外しています。それ以外——メモ、タスク、チャット、設定、添付ファイル——はすべて含まれます。", "로컬 모델 파일은 수 기가바이트에 이를 수 있고 다시 내려받을 수 있으므로 제외합니다. 그 외 노트, 할 일, 채팅, 설정, 첨부 파일은 모두 포함됩니다."),
    ("autoBackupFailed(reason: String)", "Last automatic backup failed: {reason}", "上次自动备份失败：{reason}", "前回の自動バックアップに失敗しました：{reason}", "마지막 자동 백업 실패: {reason}"),
    # Restored to the catalog (round R1): these were hand-added straight into both I18n.kt files,
    # which is the drift the catalog exists to prevent. Values copied verbatim from those files.
    ("autoBackupRunNow", "Back up now", "立即备份", "今すぐバックアップ", "지금 백업"),
    ("autoBackupOnlyWhileOpen", "Runs while Lucent is open, and catches up the next time it starts.", "在 Lucent 运行期间执行；错过的备份会在下次启动时补上。", "Lucent の起動中に実行され、逃した分は次回起動時に補われます。", "Lucent가 실행 중일 때 동작하며, 놓친 백업은 다음 실행 시 보완됩니다."),


    # ---- Task 16: parallel vs overwrite restore ----
    ("importModeTitle", "If something is already here", "如果本机已有相同内容", "同じ内容が既にある場合", "같은 내용이 이미 있을 때"),
    ("importModeParallel", "Keep both", "平行导入", "両方とも残す", "둘 다 유지"),
    ("importModeParallelDesc", "Adds everything alongside what you have. Nothing existing is changed. Safest — the worst case is a duplicate you can delete.", "把备份里的内容并列添加进来，不修改任何已有数据。最安全——最坏情况只是多出一份可以删掉的副本。", "バックアップの内容を既存データと並べて追加します。既存のものは一切変更されません。最も安全で、最悪でも削除できる重複が増えるだけです。", "백업 내용을 기존 데이터와 나란히 추가합니다. 기존 항목은 전혀 변경되지 않습니다. 가장 안전하며, 최악의 경우 삭제할 수 있는 중복이 생길 뿐입니다."),
    ("importModeOverwrite", "Replace older copies", "覆盖导入", "古い方を置き換える", "오래된 사본 교체"),
    ("importModeOverwriteDesc", "Replaces a note here when the backup's copy is newer, keeps yours when it isn't, and creates anything missing. Use this when the backup is the copy you trust. Replacements cannot be undone.", "当备份里的版本更新时替换本机的笔记，本机更新则保留本机的，缺少的则新建。适用于「以备份为准」的场景。被替换的内容无法撤销。", "バックアップ側が新しい場合はこちらのメモを置き換え、こちらが新しければ残し、無いものは新規作成します。バックアップを正とする場合に使います。置き換えは取り消せません。", "백업 쪽이 더 최신이면 이곳의 노트를 교체하고, 이곳이 더 최신이면 유지하며, 없는 항목은 새로 만듭니다. 백업을 기준으로 삼을 때 사용하세요. 교체된 내용은 되돌릴 수 없습니다."),
    ("importModeTaskNote", "Tasks have no edit timestamp, so \"Replace older copies\" always takes the backup's version of a task it recognises.", "任务没有记录修改时间，因此「覆盖导入」对能识别出的任务一律采用备份中的版本。", "タスクには編集時刻が記録されていないため、「古い方を置き換える」は認識できたタスクについて常にバックアップ側を採用します。", "할 일에는 편집 시각이 기록되지 않으므로 \"오래된 사본 교체\"는 인식된 할 일에 대해 항상 백업 쪽을 사용합니다."),
    ("importReplacedSummary(notes: Int, tasks: Int)", "Replaced {notes} notes and {tasks} tasks", "已覆盖 {notes} 条笔记、{tasks} 个任务", "{notes} 件のメモと {tasks} 件のタスクを置き換えました", "노트 {notes}개, 할 일 {tasks}개를 교체했습니다"),


    # ---- Task 15: twelve further backgrounds ----
    ("paletteAmber", "Amber", "琥珀", "アンバー", "앰버"),
    ("paletteCrimson", "Crimson", "绯红", "クリムゾン", "크림슨"),
    ("paletteIndigo", "Indigo", "靛蓝", "インディゴ", "인디고"),
    ("paletteOlive", "Olive", "橄榄", "オリーブ", "올리브"),
    ("palettePlum", "Plum", "酒紫", "プラム", "플럼"),
    ("paletteGraphite", "Graphite", "石墨", "グラファイト", "그래파이트"),
    ("paletteCitrus", "Citrus", "柑橘", "シトラス", "시트러스"),
    ("paletteGlacier", "Glacier", "冰川", "グレイシャー", "글레이셔"),
    ("paletteNebula", "Nebula", "星云", "ネビュラ", "네뷸러"),
    ("paletteEmberglow", "Emberglow", "余烬", "エンバーグロウ", "엠버글로우"),
    ("paletteMeridian", "Meridian", "子午", "メリディアン", "메리디안"),
    ("paletteOrchid", "Orchid", "兰花", "オーキッド", "오키드"),

    # ---- Task 15: four further appearances ----
    ("themeMonetRose", "Rose", "蔷薇", "ローズ", "로즈"),
    ("themeMonetRoseDesc", "Soft light pink", "柔和的浅粉色", "やわらかな淡いピンク", "부드러운 연분홍"),
    ("themeMonetLagoon", "Lagoon", "浅湖", "ラグーン", "라군"),
    ("themeMonetLagoonDesc", "Pale teal, between green and blue", "淡青绿，介于绿与蓝之间", "緑と青の間の淡いティール", "초록과 파랑 사이의 옅은 청록"),
    ("themeMonetInk", "Ink", "墨青", "インク", "잉크"),
    ("themeMonetInkDesc", "Deep teal after dark", "夜色中的深青绿", "夜の深いティール", "어둠 속 짙은 청록"),
    ("themeMonetGarnet", "Garnet", "石榴", "ガーネット", "가넷"),
    ("themeMonetGarnetDesc", "Deep crimson after dark", "夜色中的深绯红", "夜の深いクリムゾン", "어둠 속 짙은 크림슨"),


    # ---- Task 12: attachment select-all on the export selection screen ----
    ("selectAllAttachments", "Select all attachments", "全选附件", "添付ファイルをすべて選択", "첨부 파일 모두 선택"),
    ("nAttachmentsSelected(count: Int)", "{count} attachments", "已选 {count} 个附件", "添付 {count} 件", "첨부 {count}개"),

    # ---- Task 13: opt-in security-question recovery for the backup password ----
    ("backupRecoveryTitle", "Add a security question to this backup", "为此备份添加密保问题", "このバックアップに秘密の質問を追加", "이 백업에 보안 질문 추가"),
    ("backupRecoveryDesc", "Off by default. Lets you recover this backup's password by answering a question you set now.", "默认关闭。开启后，可通过回答你现在设置的问题来找回此备份的密码。", "初期状態はオフです。今設定する質問に答えることで、このバックアップのパスワードを取り戻せます。", "기본값은 꺼짐입니다. 지금 설정한 질문에 답하여 이 백업의 비밀번호를 되찾을 수 있습니다."),
    ("backupRecoveryWarnBody", "Read this before turning it on. With a security question attached, this backup is only as strong as the WEAKER of your password and your answer. An answer someone could find on your social media does not weaken this backup — it becomes its real password.\n\nChoose a question whose answer nobody else knows and you will not forget. This is set per backup, so you can add it to a file kept in a drawer and leave it off a file going to a shared cloud folder.\n\nBackups exported without this are unchanged: forgetting their password means the file cannot be opened by anyone, including us.", "开启前请先读这段。附带密保问题后，此备份的安全性等于你的密码和你的答案中**较弱的那一个**。如果答案是别人能从你社交媒体上查到的东西，那它不是削弱了这个备份——它直接变成了这个备份的真实密码。\n\n请选择一个别人不知道、而你不会忘记的问题。此选项按备份单独设置，所以你可以给放在抽屉里的文件加上，而给传到共享云盘的文件不加。\n\n不带此选项导出的备份保持原样：忘记密码就意味着任何人都打不开该文件，包括我们。", "オンにする前にお読みください。秘密の質問を添えると、このバックアップの強度はパスワードと答えのうち**弱い方**と同じになります。SNS から調べられるような答えは、このバックアップを弱めるのではなく、そのまま実質的なパスワードになります。\n\n他人が知らず、あなたが忘れない質問を選んでください。この設定はバックアップごとです。引き出しにしまうファイルには付け、共有クラウドに置くファイルには付けない、という使い分けができます。\n\nこれを付けずに書き出したバックアップは従来どおりです。パスワードを忘れれば、私たちを含め誰もそのファイルを開けません。", "켜기 전에 읽어 주세요. 보안 질문을 붙이면 이 백업의 강도는 비밀번호와 답변 중 **더 약한 쪽**과 같아집니다. SNS에서 찾을 수 있는 답변은 이 백업을 약하게 만드는 정도가 아니라, 그 자체가 실질적인 비밀번호가 됩니다.\n\n남들은 모르고 본인은 잊지 않을 질문을 고르세요. 이 설정은 백업마다 따로 적용되므로, 서랍에 보관할 파일에는 붙이고 공유 클라우드에 올릴 파일에는 붙이지 않을 수 있습니다.\n\n이 옵션 없이 내보낸 백업은 그대로입니다. 비밀번호를 잊으면 저희를 포함해 누구도 그 파일을 열 수 없습니다."),
    ("backupRecoveryForgot", "Forgot this backup's password?", "忘记此备份的密码？", "このバックアップのパスワードをお忘れですか？", "이 백업의 비밀번호를 잊으셨나요?"),
    ("backupRecoveryAnswerWrong", "That answer doesn't match", "答案不正确", "答えが一致しません", "답변이 일치하지 않습니다"),


    # ================= C-group tasks =================

    # ---- Task 1: Blackout Mode (the advanced privacy switch) ----
    ("blackoutTitle", "Blackout Mode", "隐迹模式", "ブラックアウトモード", "블랙아웃 모드"),
    ("blackoutSub", "Maximum privacy: no network, no preview, password required", "最高隐私级别：断网、无预览、强制密码", "最高レベルのプライバシー：ネットワーク遮断・プレビューなし・パスワード必須", "최고 수준의 개인정보 보호: 네트워크 차단, 미리보기 없음, 비밀번호 필수"),
    ("blackoutDesc", "Blocks every network request, hides the app from the recents screen, and requires your password each time you return.", "阻止所有网络请求，在最近任务界面隐藏应用内容，每次返回都需要输入密码。", "すべてのネットワーク通信を遮断し、最近使用したアプリの画面で内容を隠し、復帰のたびにパスワードを求めます。", "모든 네트워크 요청을 차단하고 최근 앱 화면에서 내용을 숨기며 돌아올 때마다 비밀번호를 요구합니다."),
    ("blackoutOverridesTitle", "This switch outranks every other setting", "此开关的优先级高于其它所有设定", "このスイッチは他のすべての設定より優先されます", "이 스위치는 다른 모든 설정보다 우선합니다"),
    ("blackoutWarnTitle", "Turn on Blackout Mode?", "要开启隐迹模式吗？", "ブラックアウトモードをオンにしますか？", "블랙아웃 모드를 켤까요?"),
    ("blackoutWarnBody", "What you gain: nothing leaves this device — the cloud assistant, web search and model downloads are all blocked. The app shows nothing in the recents screen and cannot be screenshotted. Your password is required every time you come back.\n\nWhat it costs: the cloud assistant and web search stop working entirely. Lucent disappears from the system share sheet. A password becomes mandatory, and if you forget it without a security question, your data cannot be recovered by anyone — including us.\n\nTurning it off restores your previous settings.", "开启后的好处：所有数据都不会离开本机——云端助手、联网搜索、模型下载全部被阻止。应用在最近任务界面不显示任何内容，也无法被截屏。每次回到应用都需要输入密码。\n\n代价：云端助手和联网搜索将完全停止工作。Lucent 会从系统分享菜单中消失。密码变为强制项，如果你忘记密码且没有设置密保问题，任何人都无法找回你的数据——包括我们。\n\n关闭后会恢复你之前的设定。", "得られるもの：データはこの端末から一切出ません——クラウドアシスタント、ウェブ検索、モデルのダウンロードはすべて遮断されます。最近使用したアプリの画面には何も表示されず、スクリーンショットも撮れません。戻るたびにパスワードが必要です。\n\n代償：クラウドアシスタントとウェブ検索は完全に停止します。Lucent はシステムの共有メニューから消えます。パスワードが必須となり、秘密の質問を設定せずに忘れた場合、誰も——私たちを含めて——データを復元できません。\n\nオフにすると以前の設定に戻ります。", "얻는 것: 데이터가 이 기기를 벗어나지 않습니다 — 클라우드 어시스턴트, 웹 검색, 모델 다운로드가 모두 차단됩니다. 최근 앱 화면에 아무것도 표시되지 않고 스크린샷도 찍을 수 없습니다. 돌아올 때마다 비밀번호가 필요합니다.\n\n대가: 클라우드 어시스턴트와 웹 검색이 완전히 중단됩니다. Lucent가 시스템 공유 메뉴에서 사라집니다. 비밀번호가 필수가 되며, 보안 질문 없이 잊어버리면 저희를 포함해 누구도 데이터를 복구할 수 없습니다.\n\n끄면 이전 설정으로 돌아갑니다."),
    ("blackoutWarnConfirm", "Turn on Blackout", "开启隐迹模式", "ブラックアウトをオンにする", "블랙아웃 켜기"),
    ("blackoutNeedsPassword", "Blackout Mode needs a password. Set one to continue.", "隐迹模式需要密码，请先设置密码。", "ブラックアウトモードにはパスワードが必要です。設定してください。", "블랙아웃 모드에는 비밀번호가 필요합니다. 먼저 설정하세요."),
    ("blackoutFrozenTitle", "Frozen by Blackout Mode", "已被隐迹模式冻结", "ブラックアウトモードにより凍結中", "블랙아웃 모드로 인해 정지됨"),
    ("blackoutFrozenBody", "This needs the network, which Blackout Mode blocks. Turn Blackout off in Settings → Privacy to use it again.", "此功能需要联网，而隐迹模式已阻止所有网络请求。前往 设置 → 隐私 关闭隐迹模式后即可使用。", "この機能にはネットワークが必要ですが、ブラックアウトモードが遮断しています。設定 → プライバシー でオフにすると再び使えます。", "이 기능에는 네트워크가 필요하지만 블랙아웃 모드가 차단하고 있습니다. 설정 → 개인정보에서 끄면 다시 사용할 수 있습니다."),
    ("blackoutOffToast", "Blackout Mode off — previous settings restored", "已关闭隐迹模式，之前的设定已恢复", "ブラックアウトモードをオフにしました。以前の設定に戻りました", "블랙아웃 모드를 껐습니다. 이전 설정이 복원되었습니다"),

    # ---- Task 3: Crash Shield ----
    ("crashShieldTitle", "Crash Shield", "崩溃护盾", "クラッシュシールド", "크래시 실드"),
    ("crashShieldDesc", "Catches errors that would close the app, and keeps it on screen so you can save your work.", "拦截会导致应用关闭的错误，让应用保持在前台，使你有机会保存工作。", "アプリを終了させるエラーを捕捉し、作業を保存できるように画面上に保ち続けます。", "앱을 종료시킬 오류를 가로채 화면에 계속 띄워 작업을 저장할 수 있게 합니다."),
    ("crashShieldLimitsTitle", "What it cannot catch", "它无法拦截的情况", "捕捉できないもの", "막을 수 없는 경우"),
    ("crashShieldLimitsBody", "Crash Shield catches every error inside the app itself. It cannot catch three things: a crash inside the on-device model engine, the system closing Lucent to free memory, or Android killing it for not responding. We would rather say so than promise a guarantee we cannot keep.\n\nA caught error means the work that failed did not finish — a save that crashed did not save. The app stays open so you can retry or export a backup, which a closed app cannot do.\n\nTurning this on also turns diagnostic logging on and keeps it on: an error that is hidden without being recorded is worse than one you can see.", "崩溃护盾能拦截应用自身的所有错误，但有三种情况拦不住：本地模型引擎内部的崩溃、系统为释放内存而关闭 Lucent、以及安卓因应用无响应将其终止。与其承诺做不到的保证，我们选择如实说明。\n\n被拦截的错误意味着那次操作并没有完成——崩溃的保存并没有保存成功。但应用会保持打开，你可以重试或导出备份，而已关闭的应用做不到这些。\n\n开启此功能会同时强制开启日志记录：一个被隐藏且未被记录的错误，比一个你能看见的错误更糟。", "クラッシュシールドはアプリ自身のエラーをすべて捕捉します。ただし三つだけ捕捉できません：端末内モデルエンジン内部のクラッシュ、メモリ確保のためシステムが Lucent を終了する場合、応答なしとみなされて Android に終了される場合です。守れない保証を約束するより、正直にお伝えします。\n\n捕捉されたエラーは、その処理が完了しなかったことを意味します——失敗した保存は保存されていません。それでもアプリは開いたままなので、やり直しやバックアップの書き出しができます。閉じてしまったアプリにはできないことです。\n\nこれをオンにすると診断ログも自動的にオンになり、オンのまま保たれます。記録されずに隠されたエラーは、目に見えるエラーより厄介だからです。", "크래시 실드는 앱 자체의 모든 오류를 잡아냅니다. 다만 세 가지는 잡을 수 없습니다: 기기 내 모델 엔진 내부의 크래시, 메모리 확보를 위해 시스템이 Lucent를 종료하는 경우, 응답 없음으로 Android가 종료하는 경우입니다. 지킬 수 없는 보장을 약속하기보다 사실대로 말씀드립니다.\n\n오류가 잡혔다는 것은 그 작업이 끝나지 않았다는 뜻입니다 — 실패한 저장은 저장되지 않았습니다. 그래도 앱은 열린 채로 남아 다시 시도하거나 백업을 내보낼 수 있습니다. 닫힌 앱은 그럴 수 없습니다.\n\n이 기능을 켜면 진단 로깅도 함께 켜지고 유지됩니다. 기록되지 않은 채 숨겨진 오류는 눈에 보이는 오류보다 나쁘기 때문입니다."),
    ("crashShieldLoggingLocked", "Logging is held on by Crash Shield", "日志记录已被崩溃护盾锁定为开启", "ログ記録はクラッシュシールドによりオンに固定されています", "로깅이 크래시 실드에 의해 켜짐으로 고정되어 있습니다"),
    ("crashShieldNextLaunch", "Takes effect the next time you open Lucent", "下次打开 Lucent 时生效", "次回 Lucent を起動したときに有効になります", "다음에 Lucent를 열 때 적용됩니다"),
    ("crashShieldCaught(count: Int)", "Errors caught this session: {count}", "本次运行已拦截错误：{count}", "今回の起動で捕捉したエラー：{count}", "이번 세션에서 잡은 오류: {count}"),

    # ---- Task 18: unlock attempt limits and self-destruct ----
    ("attemptLimitsTitle", "Unlock attempt limits", "解锁尝试次数限制", "ロック解除の試行回数制限", "잠금 해제 시도 횟수 제한"),
    ("attemptLimitsDesc", "How many wrong passwords are allowed before Lucent pauses, and for how long.", "允许输错多少次密码后 Lucent 会暂停解锁，以及暂停多久。", "何回パスワードを間違えたら Lucent が一時停止するか、その時間の設定です。", "비밀번호를 몇 번 틀리면 Lucent가 일시 중지되는지와 그 시간을 설정합니다."),
    ("attemptFirstRound", "First round attempts", "首轮尝试次数", "最初のラウンドの試行回数", "첫 라운드 시도 횟수"),
    ("attemptLaterRounds", "Attempts per later round", "后续每轮尝试次数", "以降の各ラウンドの試行回数", "이후 각 라운드 시도 횟수"),
    ("attemptLadderNote", "After each round: 30 seconds, then 1, 10, 30 and 60 minutes. A correct password resets everything.", "每轮结束后依次等待：30 秒、1 分钟、10 分钟、30 分钟、60 分钟。输入正确密码后全部重置。", "各ラウンド後の待ち時間：30秒、1分、10分、30分、60分の順です。正しいパスワードを入力するとすべてリセットされます。", "각 라운드 후 대기 시간: 30초, 1분, 10분, 30분, 60분 순입니다. 올바른 비밀번호를 입력하면 모두 초기화됩니다."),
    ("attemptLockedOut(time: String)", "Too many attempts. Try again in {time}", "尝试次数过多，请在 {time} 后重试", "試行回数が多すぎます。{time} 後にもう一度お試しください", "시도 횟수가 너무 많습니다. {time} 후에 다시 시도하세요"),
    ("attemptRemaining(count: Int)", "{count} attempts left", "还剩 {count} 次机会", "残り {count} 回", "{count}번 남았습니다"),
    ("selfDestructTitle", "Erase everything after repeated failures", "多次失败后清除全部数据", "繰り返し失敗した場合にすべて消去", "반복 실패 시 모든 데이터 삭제"),
    ("selfDestructDesc", "Off by default. When on, Lucent permanently deletes all of its data after this many wrong passwords.", "默认关闭。开启后，累计输错达到设定次数时，Lucent 将永久删除全部数据。", "初期状態はオフです。オンにすると、設定した回数だけパスワードを間違えた時点で Lucent はすべてのデータを完全に削除します。", "기본값은 꺼짐입니다. 켜면 설정한 횟수만큼 비밀번호를 틀렸을 때 Lucent가 모든 데이터를 영구히 삭제합니다."),
    ("selfDestructThreshold", "Wrong passwords before erasing", "清除前允许的错误次数", "消去するまでの誤入力回数", "삭제 전 허용 오류 횟수"),
    ("selfDestructWarnTitle", "Erase all data after repeated failures?", "确定要在多次失败后清除全部数据吗？", "繰り返し失敗した場合にすべてのデータを消去しますか？", "반복 실패 시 모든 데이터를 삭제할까요?"),
    ("selfDestructWarnBody", "What you gain: someone who takes your device cannot keep guessing forever. After the limit, everything is gone and there is nothing left to break into.\n\nWhat it costs: this is permanent and it cannot be undone. Not by us, not by a support request, not by anything. If you lock yourself out — a child with your phone, a pocket, a bad week — your notes are gone the same way.\n\nA correct password resets the counter to zero, so ordinary typing mistakes cannot build up over time. Keep an exported backup somewhere else before turning this on.", "开启后的好处：拿到你设备的人无法无限次尝试。达到上限后所有数据即被清除，也就没有什么可被破解的了。\n\n代价：此操作永久生效且无法撤销。我们无法恢复，客服无法恢复，任何方式都无法恢复。如果是你自己被挡在门外——孩子拿了你的手机、装在口袋里误触、状态不好的一周——你的笔记会以同样的方式消失。\n\n输入正确密码会将计数清零，因此日常的输入失误不会长期累积。开启前请先把备份导出并保存到别处。", "得られるもの：端末を持ち去った人が無制限に試行を続けることはできません。上限に達するとすべて消去され、破るべきものが残りません。\n\n代償：この操作は永続的で取り消せません。私たちにも、サポートへの依頼でも、どんな方法でも復元できません。締め出されたのがあなた自身だった場合——お子さんが端末を触った、ポケットの中で誤操作した、調子の悪い一週間だった——メモも同じように消えます。\n\n正しいパスワードを入力するとカウントはゼロに戻るため、日常的な打ち間違いが積み重なることはありません。オンにする前に、バックアップを書き出して別の場所に保管してください。", "얻는 것: 기기를 가져간 사람이 무한정 추측할 수 없습니다. 한도에 도달하면 모든 것이 사라지므로 뚫을 대상도 남지 않습니다.\n\n대가: 이 작업은 영구적이며 되돌릴 수 없습니다. 저희도, 고객 지원 요청으로도, 어떤 방법으로도 복구할 수 없습니다. 막힌 사람이 본인이라면 — 아이가 휴대폰을 만졌거나, 주머니 속에서 눌렸거나, 힘든 한 주였다면 — 노트도 똑같이 사라집니다.\n\n올바른 비밀번호를 입력하면 카운터가 0으로 초기화되므로 일상적인 오타가 쌓이지는 않습니다. 켜기 전에 백업을 내보내 다른 곳에 보관하세요."),
    ("selfDestructConfirmPhrase", "ERASE", "清除", "消去", "삭제"),
    ("selfDestructConfirmHint(phrase: String)", "Type {phrase} to confirm", "输入 {phrase} 以确认", "確認のため {phrase} と入力してください", "확인하려면 {phrase}을(를) 입력하세요"),
    ("selfDestructRemaining(count: Int)", "Warning: {count} more wrong passwords will erase everything", "警告：再输错 {count} 次将清除全部数据", "警告：あと {count} 回間違えるとすべて消去されます", "경고: {count}번 더 틀리면 모든 데이터가 삭제됩니다"),

    # ---- Task 6: open links in the system browser ----
    ("openLinksTitle", "Open web links in your browser", "在浏览器中打开网址", "ウェブリンクをブラウザで開く", "웹 링크를 브라우저에서 열기"),
    ("openLinksDesc", "Makes any web address in a note, task or chat tappable — no special formatting needed.", "让笔记、任务或聊天中的任意网址变为可点击——无需任何特殊格式。", "メモ・タスク・チャット内のウェブアドレスをタップ可能にします。特別な書式は不要です。", "노트, 할 일, 채팅 속 웹 주소를 탭할 수 있게 만듭니다. 특별한 서식이 필요 없습니다."),
    ("openLinksWarnTitle", "Open web links in your browser?", "要在浏览器中打开网址吗？", "ウェブリンクをブラウザで開きますか？", "웹 링크를 브라우저에서 열까요?"),
    ("openLinksWarnBody", "For: a pasted address works like it does everywhere else, with no syntax to learn. The full address stays visible as text, so you can see where a link goes before you tap it.\n\nAgainst: tapping hands the address to another app. Your browser sees it, the destination sees your IP address, and none of that is covered by Lucent's own encryption. A link in a note someone shared with you is then one tap from opening.\n\nOnly http and https addresses are ever opened. Blackout Mode overrides this switch — while it is on, nothing is handed to a browser.", "好处：粘贴的网址会像在其它应用里一样直接可用，无需学习任何语法。完整地址始终以文本形式显示，因此你在点击前就能看清它指向哪里。\n\n代价：点击会把地址交给另一个应用。你的浏览器会看到它，目标网站会看到你的 IP 地址，而这些都不在 Lucent 的加密保护范围内。别人分享给你的笔记里的链接，也就只差一次点击就会被打开。\n\n只有 http 和 https 地址会被打开。隐迹模式的优先级高于此开关——它开启时，任何地址都不会交给浏览器。", "利点：貼り付けたアドレスが他のアプリと同じように使え、覚える書式もありません。完全なアドレスがテキストとして表示されたままなので、タップする前にリンク先を確認できます。\n\n欠点：タップするとアドレスが別のアプリに渡されます。ブラウザはそれを見ますし、接続先はあなたの IP アドレスを知ります。そのどちらも Lucent の暗号化の対象外です。誰かが共有したメモの中のリンクも、タップ一回で開く状態になります。\n\n開かれるのは http と https のアドレスのみです。ブラックアウトモードはこのスイッチより優先され、オンの間はブラウザに何も渡されません。", "장점: 붙여넣은 주소가 다른 앱과 똑같이 동작하며 배울 문법이 없습니다. 전체 주소가 텍스트로 그대로 보이므로 탭하기 전에 어디로 가는지 확인할 수 있습니다.\n\n단점: 탭하면 주소가 다른 앱으로 전달됩니다. 브라우저가 주소를 보고, 목적지는 당신의 IP 주소를 보며, 그 어느 것도 Lucent의 암호화 보호를 받지 않습니다. 누군가 공유한 노트 속 링크도 탭 한 번이면 열립니다.\n\nhttp와 https 주소만 열립니다. 블랙아웃 모드가 이 스위치보다 우선하며, 켜져 있는 동안에는 브라우저로 아무것도 전달되지 않습니다."),
    ("openLinksBlockedByBlackout", "Blackout Mode is on — links are not opened", "隐迹模式已开启，不会打开任何链接", "ブラックアウトモードがオンです。リンクは開きません", "블랙아웃 모드가 켜져 있어 링크를 열지 않습니다"),
    ("openLinksNoBrowser", "No app on this device can open that link", "此设备上没有可以打开该链接的应用", "この端末にはそのリンクを開けるアプリがありません", "이 기기에는 해당 링크를 열 수 있는 앱이 없습니다"),

    # ---- Task 11: internal vs external link colours ----
    ("linkLegendInternal", "Links to another note", "指向其它笔记", "他のメモへのリンク", "다른 노트로 연결"),
    ("linkLegendExternal", "Opens outside Lucent", "在 Lucent 之外打开", "Lucent の外部で開きます", "Lucent 외부에서 열림"),

    # ---- Task 17: at-rest encryption readout ----
    ("encryptionStatusTitle", "Encryption at rest", "静态数据加密", "保存データの暗号化", "저장 데이터 암호화"),
    ("encryptionStatusHealthy", "Your notes, attachments and saved keys are encrypted on this device.", "你的笔记、附件和已保存的密钥在本设备上均已加密。", "メモ・添付ファイル・保存された鍵は、この端末上で暗号化されています。", "노트, 첨부 파일, 저장된 키가 이 기기에서 암호화되어 있습니다."),
    ("encryptionStatusDegraded", "Some data is NOT encrypted on this device right now. Tap for details.", "当前有部分数据未在本设备上加密。点击查看详情。", "現在、一部のデータがこの端末で暗号化されていません。詳細はタップしてください。", "현재 일부 데이터가 이 기기에서 암호화되어 있지 않습니다. 자세한 내용은 탭하세요."),
    ("encryptionStatusLockedOut", "An existing database could not be opened with this device's key. Nothing was deleted — restore from a backup.", "现有数据库无法用本设备的密钥打开。数据没有被删除——请从备份恢复。", "既存のデータベースをこの端末の鍵で開けませんでした。データは削除されていません——バックアップから復元してください。", "기존 데이터베이스를 이 기기의 키로 열 수 없었습니다. 삭제된 것은 없습니다 — 백업에서 복원하세요."),
    ("encryptionRunCheck", "Run encryption check", "运行加密自检", "暗号化チェックを実行", "암호화 검사 실행"),
    ("encryptionCheckPassed", "Check passed — values are sealed and open correctly.", "自检通过——数据可正确加密并解密。", "チェックに合格しました——値は正しく封印され、復号できます。", "검사 통과 — 값이 정상적으로 봉인되고 복호화됩니다."),
    ("encryptionCheckFailed(reason: String)", "Check failed: {reason}", "自检失败：{reason}", "チェックに失敗しました：{reason}", "검사 실패: {reason}"),

    # ---- Tabs / navigation ----
    ("tabTasks", "Tasks", "任务", "タスク", "할 일"),
    ("tabNotes", "Notes", "笔记", "メモ", "노트"),
    ("tabAssistant", "Assistant", "助手", "アシスタント", "어시스턴트"),
    ("tabSettings", "Setting", "设置", "設定", "설정"),
    ("pressBackAgainToExit", "Press back again to exit", "再按一次返回键退出", "もう一度戻るボタンで終了します", "뒤로 버튼을 한 번 더 누르면 종료됩니다"),

    # ---- Common actions ----
    ("actionSave", "Save", "保存", "保存", "저장"),
    ("actionDiscard", "Discard", "放弃", "破棄", "저장 안 함"),
    ("actionCancel", "Cancel", "取消", "キャンセル", "취소"),
    ("actionDelete", "Delete", "删除", "削除", "삭제"),
    ("actionDone", "Done", "完成", "完了", "완료"),
    ("actionClose", "Close", "关闭", "閉じる", "닫기"),
    ("actionBack", "Back", "返回", "戻る", "뒤로"),
    ("actionEdit", "Edit", "编辑", "編集", "편집"),
    ("actionRename", "Rename", "重命名", "名前を変更", "이름 바꾸기"),
    ("actionShare", "Share", "分享", "共有", "공유"),
    ("actionCopy", "Copy", "复制", "コピー", "복사"),
    ("actionRetry", "Retry", "重试", "再試行", "다시 시도"),
    ("actionOk", "OK", "好", "OK", "확인"),
    ("actionSearch", "Search", "搜索", "検索", "검색"),
    ("actionExport", "Export", "导出", "エクスポート", "내보내기"),
    ("actionImport", "Import", "导入", "インポート", "가져오기"),
    ("actionRestore", "Restore", "恢复", "復元", "복원"),
    ("actionAdd", "Add", "添加", "追加", "추가"),
    ("actionOpen", "Open", "打开", "開く", "열기"),
    ("actionRemove", "Remove", "移除", "削除", "제거"),
    ("actionConfirm", "Confirm", "确认", "確認", "확인"),
    ("actionUndo", "Undo", "撤销", "元に戻す", "실행 취소"),
    ("actionDismiss", "Dismiss", "知道了", "閉じる", "닫기"),
    ("untitled", "Untitled", "无标题", "無題", "제목 없음"),

    # ---- Unsaved-changes guard (MainActivity dialog) ----
    ("unsavedChangesTitle", "Unsaved changes", "未保存的更改", "未保存の変更", "저장되지 않은 변경 사항"),
    ("unsavedChangesBody", "You have unsaved changes. Save them before leaving?", "有尚未保存的更改。要在离开前保存吗？", "未保存の変更があります。移動する前に保存しますか？", "저장되지 않은 변경 사항이 있습니다. 나가기 전에 저장할까요?"),

    # ---- Home sections ----
    ("sectionRecent", "Recent", "最近", "最近", "최근"),
    ("sectionToday", "Today", "今天", "今日", "오늘"),
    ("sectionOlder", "Older", "更早", "それ以前", "이전"),
    ("sectionThreeDays", "Last three days", "三天内", "3日以内", "최근 3일"),
    ("historyTitle", "Version history", "历史版本（快闪记录）", "履歴バージョン", "버전 기록"),
    ("historyDesc", "Every meaningful edit is snapshotted, so you can read what a note or task said earlier and put that version back. Snapshots stay on this device and are included in backups.", "每次有实质的修改都会存一份快照，你可以回看一条笔记或任务之前的内容，并把那个版本恢复回来。快照只保存在本设备，并会随备份一起导出。", "意味のある編集ごとにスナップショットを保存し、メモやタスクの以前の内容を読んで戻すことができます。スナップショットはこの端末にのみ保存され、バックアップにも含まれます。", "의미 있는 편집마다 스냅샷을 저장해, 노트나 할 일의 이전 내용을 보고 되돌릴 수 있습니다. 스냅샷은 이 기기에만 저장되며 백업에 포함됩니다."),
    ("historyNotes", "Keep history for notes", "为笔记保留历史版本", "メモの履歴を保存", "노트 기록 유지"),
    ("historyTasks", "Keep history for tasks", "为任务保留历史版本", "タスクの履歴を保存", "할 일 기록 유지"),
    ("historyCapNote(max: Int)", "Up to {max} versions are kept per item. Past that the oldest one is deleted to make room.", "每个条目最多保留 {max} 个版本；超过后会从最早的那一版开始删除。", "1 件あたり最大 {max} 件まで保存し、超えると古いものから削除されます。", "항목당 최대 {max} 개까지 보관하며, 넘으면 가장 오래된 것부터 삭제됩니다."),

    # ---- Sort options ----
    ("sortLastEdited", "Last edited", "最近编辑", "最終編集順", "최근 수정순"),
    ("sortNewestFirst", "Newest first", "最新在前", "新しい順", "최신순"),
    ("sortOldestFirst", "Oldest first", "最早在前", "古い順", "오래된순"),
    ("sortTitleAz", "Title A–Z", "标题 A–Z", "タイトル A–Z", "제목 A–Z"),
    ("sortPriority", "Priority", "优先级", "優先度", "우선순위"),
    ("sortDueDate", "Due date", "截止日期", "期限", "마감일"),
    ("sortByA11y(label: String)", "Sort by {label}", "排序方式：{label}", "並べ替え：{label}", "정렬 기준: {label}"),

    # ---- Theme modes ----
    ("themeSystem", "System default", "跟随系统", "システムに従う", "시스템 기본값"),
    ("themeSystemDesc", "Follow the device's light/dark setting", "跟随设备的浅色/深色设置", "端末のライト／ダーク設定に従います", "기기의 라이트/다크 설정을 따릅니다"),
    ("themeLight", "Light", "浅色", "ライト", "라이트"),
    ("themeLightDesc", "The neutral pale backdrop", "素净的浅色背景", "ニュートラルな淡い背景", "차분한 밝은 배경"),
    ("themeDark", "Dark", "深色", "ダーク", "다크"),
    ("themeDarkDesc", "The near-black backdrop", "接近纯黑的背景", "ほぼ黒に近い背景", "거의 검정에 가까운 배경"),
    ("themeMonetWheat", "Monet wheat", "莫奈·麦田", "モネ・麦畑", "모네 밀밭"),
    ("themeMonetWheatDesc", "Pale straw — a light theme", "浅浅的麦秆色 — 浅色主题", "淡い麦わら色 — ライトテーマ", "옅은 밀짚색 — 라이트 테마"),
    ("themeMonetGarden", "Monet garden", "莫奈·花园", "モネ・庭園", "모네 정원"),
    ("themeMonetGardenDesc", "Pale water-garden green — a light theme", "浅浅的水苑绿 — 浅色主题", "淡い水の庭の緑 — ライトテーマ", "옅은 물의 정원 초록 — 라이트 테마"),
    ("themeMonetMorning", "Monet morning", "莫奈·晨光", "モネ・朝", "모네 아침"),
    ("themeMonetMorningDesc", "Pale morning blue — a light theme", "浅浅的晨曦蓝 — 浅色主题", "淡い朝の青 — ライトテーマ", "옅은 아침 파랑 — 라이트 테마"),
    ("themeMonetWisteria", "Monet wisteria", "莫奈·紫藤", "モネ・藤", "모네 등나무"),
    ("themeMonetWisteriaDesc", "Pale wisteria — a light theme", "浅浅的紫藤色 — 浅色主题", "淡い藤色 — ライトテーマ", "옅은 등나무색 — 라이트 테마"),
    ("themeMonetNight", "Monet nightfall", "莫奈·夜幕", "モネ・宵闇", "모네 야경"),
    ("themeMonetNightDesc", "Deep evening blue — a dark theme", "深邃的傍晚蓝 — 深色主题", "深い宵の青 — ダークテーマ", "깊은 저녁 블루 — 다크 테마"),
    ("themeMonetPine", "Monet deep garden", "莫奈·深园", "モネ・深緑の庭", "모네 깊은 정원"),
    ("themeMonetPineDesc", "Deep garden green — a dark theme", "深邃的花园绿 — 深色主题", "深い庭の緑 — ダークテーマ", "깊은 정원 그린 — 다크 테마"),
    ("themeMonetPlum", "Monet dusk wisteria", "莫奈·暮紫", "モネ・暮れの藤", "모네 황혼 등나무"),
    ("themeMonetPlumDesc", "Deep wisteria plum — a dark theme", "深邃的紫藤紫 — 深色主题", "深い藤紫 — ダークテーマ", "깊은 등나무 자주 — 다크 테마"),
    ("themeMonetEmber", "Monet embers", "莫奈·余烬", "モネ・残り火", "모네 잔불"),
    ("themeMonetEmberDesc", "Deep haystack amber — a dark theme", "深邃的麦垛琥珀 — 深色主题", "深い干し草の琥珀 — ダークテーマ", "깊은 건초 앰버 — 다크 테마"),

    # ---- Note colours ----
    ("colorDefault", "Default", "默认", "デフォルト", "기본"),
    ("colorRed", "Red", "红色", "赤", "빨강"),
    ("colorOrange", "Orange", "橙色", "オレンジ", "주황"),
    ("colorYellow", "Yellow", "黄色", "黄", "노랑"),
    ("colorGreen", "Green", "绿色", "緑", "초록"),
    ("colorTeal", "Teal", "青绿色", "ティール", "청록"),
    ("colorBlue", "Blue", "蓝色", "青", "파랑"),
    ("colorPurple", "Purple", "紫色", "紫", "보라"),
    ("colorPink", "Pink", "粉色", "ピンク", "분홍"),
    ("noteColorA11y(label: String)", "{label} note colour", "{label}笔记颜色", "{label}のメモカラー", "{label} 노트 색상"),
    ("noteWithColorA11y(label: String)", "{label} note", "{label}笔记", "{label}のメモ", "{label} 노트"),

    # ---- Task styling / due dates ----
    ("priorityBadge(label: String)", "{label} priority", "{label}优先级", "優先度：{label}", "{label} 우선순위"),
    ("labelPriority", "Priority", "优先级", "優先度", "우선순위"),
    ("labelRepeat", "Repeat", "重复", "繰り返し", "반복"),
    ("remindAtDueTime", "Remind me at the due time", "在截止时间提醒我", "期限になったら通知する", "마감 시간에 알림 받기"),
    ("setDueDateToEnable", "Set a due date above to enable", "先在上方设置截止日期即可启用", "上で期限を設定すると有効になります", "위에서 마감일을 설정하면 사용할 수 있어요"),
    ("pinToTop", "Pin to top", "置顶", "先頭に固定", "맨 위에 고정"),
    ("unpin", "Unpin", "取消置顶", "固定を解除", "고정 해제"),
    ("pinned", "Pinned", "已置顶", "固定済み", "고정됨"),
    ("dueTodayAt(time: String)", "Today {time}", "今天 {time}", "今日 {time}", "오늘 {time}"),
    ("dueTomorrowAt(time: String)", "Tomorrow {time}", "明天 {time}", "明日 {time}", "내일 {time}"),
    ("dueYesterdayAt(time: String)", "Yesterday {time}", "昨天 {time}", "昨日 {time}", "어제 {time}"),
    ("dueOverdueOn(date: String)", "Overdue · {date}", "已逾期 · {date}", "期限切れ · {date}", "기한 지남 · {date}"),
    ("dueOn(date: String, time: String)", "{date} · {time}", "{date} · {time}", "{date} · {time}", "{date} · {time}"),

    # ---- Checklist ----
    ("checklist", "Checklist", "清单", "チェックリスト", "체크리스트"),
    ("checklistEmptyItem", "(empty)", "（空）", "（空）", "(비어 있음)"),
    ("checklistRemoveA11y(text: String)", "Remove \"{text}\"", "移除“{text}”", "「{text}」を削除", "\"{text}\" 제거"),
    ("checklistEditItem", "Edit item", "编辑项目", "項目を編集", "항목 편집"),
    ("checklistMore(count: Int)", "+{count} more", "还有 {count} 项", "他 {count} 件", "외 {count}개"),

    # ---- Expandable text field ----
    ("expandTextBox", "Expand text box", "展开输入框", "テキスト欄を展開", "입력란 펼치기"),
    ("collapseTextBox", "Collapse text box", "收起输入框", "テキスト欄を折りたたむ", "입력란 접기"),

    # ---- PDF viewer ----
    ("pdfNoPages", "This PDF has no pages to show.", "这个 PDF 没有可显示的页面。", "このPDFには表示できるページがありません。", "이 PDF에는 표시할 페이지가 없습니다."),
    ("pdfPageA11y(page: Int)", "Page {page}", "第 {page} 页", "{page}ページ", "{page}페이지"),
    ("pdfPageOf(page: Int, total: Int)", "Page {page} of {total}", "第 {page} 页，共 {total} 页", "{total}ページ中 {page}ページ", "{total}페이지 중 {page}페이지"),
    ("pdfRenderFailed", "Couldn't render this PDF. Try \"Open with\" instead.", "无法显示这个 PDF，请改用“打开方式”。", "このPDFを表示できませんでした。「他のアプリで開く」をお試しください。", "이 PDF를 표시할 수 없습니다. \"다른 앱으로 열기\"를 사용해 보세요."),

    # ---- Splash ----
    ("tapToSkip", "Tap to skip", "点按跳过", "タップでスキップ", "탭하여 건너뛰기"),
    ("skipAnimation", "Skip", "跳过", "スキップ", "건너뛰기"),

    # ---- Share intake ----
    ("sharedDefaultTitle", "Shared", "分享内容", "共有", "공유됨"),
    ("shareDialogTitle", "Add to Lucent", "添加到 Lucent", "Lucentに追加", "Lucent에 추가"),
    ("shareSaveTextAndFile", "Save the shared text and file as a new:", "将分享的文本和文件保存为新的：", "共有されたテキストとファイルを新規として保存：", "공유된 텍스트와 파일을 새 항목으로 저장:"),
    ("shareSaveTextAs(preview: String)", "Save \"{preview}\" as a new:", "将“{preview}”保存为新的：", "「{preview}」を新規として保存：", "\"{preview}\"을(를) 새 항목으로 저장:"),
    ("shareSaveFile", "Save the shared file as a new:", "将分享的文件保存为新的：", "共有されたファイルを新規として保存：", "공유된 파일을 새 항목으로 저장:"),
    ("newNote", "New note", "新建笔记", "新しいメモ", "새 노트"),
    ("newTask", "New task", "新建任务", "新しいタスク", "새 할 일"),

    # ---- Export selection ----
    ("selectAll", "Select all", "全选", "すべて選択", "모두 선택"),
    ("selectAllMatching", "Select all matching", "全选匹配项", "一致する項目をすべて選択", "일치 항목 모두 선택"),
    ("nSelected(count: Int)", "{count} selected", "已选择 {count} 项", "{count}件を選択中", "{count}개 선택됨"),
    # Note/task body statistics (paragraph & character count, word count, reading time). Singular and
    # plural are separate keys so English reads correctly ("1 paragraph" vs "3 paragraphs"); CJK has no
    # inflection so both forms are the same phrase.
    ("statParagraphsOne", "1 paragraph", "1 段落", "1 段落", "단락 1개"),
    ("statParagraphsN(n: Int)", "{n} paragraphs", "{n} 段落", "{n} 段落", "단락 {n}개"),
    ("statCharactersOne", "1 character", "1 字", "1 文字", "1자"),
    ("statCharactersN(n: Int)", "{n} characters", "{n} 字", "{n} 文字", "{n}자"),
    ("statWordsOne", "1 word", "1 词", "1 単語", "단어 1개"),
    ("statWordsN(n: Int)", "{n} words", "{n} 词", "{n} 単語", "단어 {n}개"),
    ("statMinRead(n: Int)", "{n} min read", "阅读约 {n} 分钟", "約{n}分で読了", "{n}분 분량"),
    ("labelFormat", "Format", "格式", "形式", "형식"),
    ("exportNSelected(count: Int)", "Export {count} selected", "导出所选 {count} 项", "選択した{count}件をエクスポート", "선택한 {count}개 내보내기"),

    # ---- Notifications / reminders ----
    ("notifChannelName", "Task reminders", "任务提醒", "タスクのリマインダー", "할 일 알림"),
    ("notifChannelDesc", "Alerts you when a task with a reminder reaches its due time", "当设置了提醒的任务到达截止时间时通知你", "リマインダー付きのタスクが期限を迎えるとお知らせします", "알림이 설정된 할 일이 마감 시간이 되면 알려줍니다"),
    ("notifTaskDue", "Task due", "任务到期", "タスクの期限です", "할 일 마감"),
    ("notifMarkDone", "Mark as Done", "标记为完成", "完了にする", "완료로 표시"),
    ("untitledTask", "Untitled task", "未命名任务", "無題のタスク", "제목 없는 할 일"),

    # ---- Priority / repeat UI labels ----
    # (TaskPriority.label / RepeatRule.label stay English on purpose: they feed the assistant's
    #  tool results and must stay stable for the model; the UI shows these instead.)
    ("priorityNone", "None", "无", "なし", "없음"),
    ("priorityLow", "Low", "低", "低", "낮음"),
    ("priorityMedium", "Medium", "中", "中", "보통"),
    ("priorityHigh", "High", "高", "高", "높음"),
    ("repeatNone", "Does not repeat", "不重复", "繰り返さない", "반복 안 함"),
    ("repeatDaily", "Daily", "每天", "毎日", "매일"),
    ("repeatWeekly", "Weekly", "每周", "毎週", "매주"),
    ("repeatMonthly", "Monthly", "每月", "毎月", "매월"),
    ("repeatYearly", "Yearly", "每年", "毎年", "매년"),
    ("repeatsEvery(rule: String)", "Repeats {rule}", "重复：{rule}", "繰り返し：{rule}", "반복: {rule}"),

    # ---- Assistant: errors, confirmations, local model ----
    ("networkCantReach", "Couldn't reach the server. Check your internet connection and try again.", "无法连接到服务器。请检查网络连接后重试。", "サーバーに接続できませんでした。インターネット接続を確認して、もう一度お試しください。", "서버에 연결할 수 없습니다. 인터넷 연결을 확인한 후 다시 시도해 주세요."),
    ("noDetails", "no details", "无详细信息", "詳細なし", "자세한 정보 없음"),
    ("confirmMoveTrash", "Move to Trash?", "移到回收站？", "ゴミ箱へ移動しますか？", "휴지통으로 이동할까요?"),
    ("confirmCreate", "Create this?", "要创建吗？", "これを作成しますか？", "이 항목을 만들까요?"),
    ("confirmMarkDone", "Mark as done?", "标记为完成？", "完了にしますか？", "완료로 표시할까요?"),
    ("confirmRemove", "Remove this?", "要移除吗？", "これを削除しますか？", "제거할까요?"),
    ("confirmGeneric", "Confirm this action?", "确认执行此操作？", "この操作を実行しますか？", "이 작업을 실행할까요?"),
    ("confirmRestore", "Restore this?", "要恢复吗？", "これを復元しますか？", "복원할까요?"),
    ("localModelMissing", "Local model mode is on, but no model has been imported yet. Import a GGUF file in Settings.", "已开启本地模型模式，但还没有导入模型。请先在设置中导入 GGUF 文件。", "ローカルモデルモードはオンですが、モデルがまだインポートされていません。設定でGGUFファイルをインポートしてください。", "로컬 모델 모드가 켜져 있지만 아직 모델을 가져오지 않았습니다. 설정에서 GGUF 파일을 가져와 주세요."),
    ("localModelLoadFailed(detail: String)", "Couldn't load the local model. {detail}", "无法加载本地模型。{detail}", "ローカルモデルを読み込めませんでした。{detail}", "로컬 모델을 불러오지 못했습니다. {detail}"),
    ("localModelGenerateFailed", "The local model couldn't produce a reply. Try again, or re-import the model in Settings.", "本地模型未能生成回复。请重试，或在设置中重新导入模型。", "ローカルモデルが応答を生成できませんでした。もう一度試すか、設定でモデルを再インポートしてください。", "로컬 모델이 응답을 생성하지 못했습니다. 다시 시도하거나 설정에서 모델을 다시 가져와 주세요."),
    ("localModelUnsupportedAbi", "This device's processor isn't supported by the local model engine.", "此设备的处理器不受本地模型引擎支持。", "この端末のプロセッサはローカルモデルエンジンに対応していません。", "이 기기의 프로세서는 로컬 모델 엔진에서 지원되지 않습니다."),

    ("localModelLoadFailedDetail", "The file may not be a valid GGUF model, or it may be too large for this device's memory.", "文件可能不是有效的 GGUF 模型，或超出了此设备的可用内存。", "ファイルが有効なGGUFモデルでないか、この端末のメモリには大きすぎる可能性があります。", "파일이 유효한 GGUF 모델이 아니거나 이 기기의 메모리에 비해 너무 클 수 있습니다."),

    # ---- Assistant screen ----
    ("assistantGreeting(name: String)", "Hi there! I'm your assistant {name}. Whether it's learning or expressing your feelings, I will gently accompany you! Feel free to ask me planning questions or share anything happy or unhappy!", "嗨！我是你的助手{name}。无论是学习还是倾诉心情，我都会温柔地陪伴你！欢迎问我任何规划问题，或分享任何开心与不开心的事！", "こんにちは！あなたのアシスタント、{name}です。学びのことでも気持ちのことでも、やさしく寄り添います。計画の相談でも、うれしいことやつらいことでも、気軽に話しかけてくださいね！", "안녕하세요! 저는 당신의 어시스턴트 {name}입니다. 공부든 마음속 이야기든 다정하게 함께할게요! 계획에 대한 질문이든 기쁘거나 속상한 일이든 편하게 이야기해 주세요!"),
    ("deleteConversationTitle", "Delete this conversation?", "删除此对话？", "この会話を削除しますか？", "이 대화를 삭제할까요?"),
    ("deleteConversationBodyAll", "This permanently deletes this conversation and its messages. Your other conversations are kept. This can't be undone.", "这将永久删除此对话及其消息。其他对话会保留。此操作无法撤销。", "この会話とそのメッセージは完全に削除されます。ほかの会話は残ります。この操作は元に戻せません。", "이 대화와 메시지가 영구적으로 삭제됩니다. 다른 대화는 유지됩니다. 이 작업은 되돌릴 수 없습니다."),
    ("deleteConversationBodyNamed(title: String)", "\"{title}\" and all of its messages will be permanently deleted. Your other conversations are kept. This can't be undone.", "“{title}”及其所有消息将被永久删除。其他对话会保留。此操作无法撤销。", "「{title}」とそのすべてのメッセージは完全に削除されます。ほかの会話は残ります。この操作は元に戻せません。", "\"{title}\" 및 모든 메시지가 영구적으로 삭제됩니다. 다른 대화는 유지됩니다. 이 작업은 되돌릴 수 없습니다."),
    ("conversationFallback", "Conversation", "对话", "会話", "대화"),
    ("newConversation", "New conversation", "新对话", "新しい会話", "새 대화"),
    ("renameConversationTitle", "Rename conversation", "重命名对话", "会話の名前を変更", "대화 이름 바꾸기"),
    ("labelName", "Name", "名称", "名前", "이름"),
    ("noMatches", "No matches", "没有匹配项", "一致する項目がありません", "일치 항목 없음"),
    ("noSavedConversations", "No saved conversations yet", "还没有保存的对话", "保存された会話はまだありません", "저장된 대화가 아직 없습니다"),
    ("a11ySwitchConversation", "Switch conversation", "切换对话", "会話を切り替え", "대화 전환"),
    ("a11yConversationOptions(title: String)", "Options for {title}", "“{title}”的选项", "「{title}」のオプション", "{title} 옵션"),
    ("thisConversation", "this conversation", "此对话", "この会話", "이 대화"),
    ("a11yExportChat", "Export chat as zip", "将聊天导出为 zip", "チャットをzipでエクスポート", "채팅을 zip으로 내보내기"),
    ("connectionProblem", "Connection problem", "连接出现问题", "接続に問題があります", "연결 문제"),
    ("a11yAttachment", "Attachment", "附件", "添付ファイル", "첨부 파일"),
    ("imageUnreadable", "[Image unreadable]", "[图片无法读取]", "[画像を読み込めません]", "[이미지를 읽을 수 없음]"),
    ("a11yDownloadFile(name: String)", "Download {name}", "下载 {name}", "{name}をダウンロード", "{name} 다운로드"),
    ("a11yDownloadReplyFiles", "Download files from this reply", "下载此回复中的文件", "この返信のファイルをダウンロード", "이 답장의 파일 다운로드"),
    ("a11yRemoveAttachment", "Remove attachment", "移除附件", "添付ファイルを削除", "첨부 파일 제거"),
    ("a11yAttachFile", "Attach file", "添加附件", "ファイルを添付", "파일 첨부"),
    ("messagePlaceholder", "Message", "输入消息", "メッセージ", "메시지"),
    ("a11yStopGenerating", "Stop generating", "停止生成", "生成を停止", "생성 중지"),
    ("a11ySend", "Send", "发送", "送信", "보내기"),
    ("a11yJumpToLatest", "Jump to latest", "跳到最新", "最新へ移動", "최신으로 이동"),
    ("a11yScrollToTop", "Scroll to top", "滚动到顶部", "一番上へスクロール", "맨 위로 스크롤"),
    ("a11yScrollToBottom", "Scroll to bottom", "滚动到底部", "一番下へスクロール", "맨 아래로 스크롤"),
    ("thinkingIndicator(name: String)", "{name} is thinking", "{name} 正在思考", "{name}が考えています", "{name}이(가) 생각하는 중"),
    ("downloadFilesTitle", "Download files", "下载文件", "ファイルをダウンロード", "파일 다운로드"),
    ("downloadChoose", "Choose which files to download.", "选择要下载的文件。", "ダウンロードするファイルを選んでください。", "다운로드할 파일을 선택하세요."),
    ("downloadReplyTxt", "Reply text (.txt)", "回复文本 (.txt)", "返信テキスト (.txt)", "답장 텍스트 (.txt)"),
    ("downloadNone", "This reply has no files to download.", "此回复没有可下载的文件。", "この返信にはダウンロードできるファイルがありません。", "이 답장에는 다운로드할 파일이 없습니다."),
    ("actionDownload", "Download", "下载", "ダウンロード", "다운로드"),
    ("setupApiFirst", "No assistant is set up yet. Add an API in Settings > Assistant > API, or import a local model (Settings > Assistant > Local model) to chat offline with no API key.", "还没有可用的助手。请在「设置 > 助手 > API」中添加一个 API；或在「设置 > 助手 > 本地模型」中导入本地模型，无需 API 密钥即可离线聊天。", "アシスタントがまだ設定されていません。「設定 > アシスタント > API」でAPIを追加するか、「設定 > アシスタント > ローカルモデル」からモデルをインポートすると、APIキーなしでオフラインで会話できます。", "아직 설정된 어시스턴트가 없습니다. '설정 > 어시스턴트 > API'에서 API를 추가하거나, '설정 > 어시스턴트 > 로컬 모델'에서 모델을 가져오면 API 키 없이 오프라인으로 대화할 수 있습니다."),
    ("exportYou", "You", "你", "あなた", "나"),
    ("inputAttachedFile(name: String)", "[Attached file: {name}]", "[附件：{name}]", "[添付ファイル：{name}]", "[첨부 파일: {name}]"),
    ("inputAttachedFileTooLarge(name: String)", "[Attached file: {name} (too large to read here)]", "[附件：{name}（太大，无法在此读取）]", "[添付ファイル：{name}（大きすぎるためここでは読み込めません）]", "[첨부 파일: {name} (너무 커서 여기서 읽을 수 없음)]"),

    # ---- UiComponents ----
    ("a11yFilterByDate", "Filter by date", "按日期筛选", "日付で絞り込み", "날짜로 필터"),
    ("a11yClearDateFilter", "Clear date filter", "清除日期筛选", "日付の絞り込みを解除", "날짜 필터 지우기"),
    ("a11yHideActions", "Hide actions", "隐藏操作", "操作を隠す", "작업 숨기기"),
    ("a11yShowMoreActions", "Show more actions", "显示更多操作", "その他の操作を表示", "더 많은 작업 보기"),
    ("searchTipsTitle", "Search tips", "搜索技巧", "検索のヒント", "검색 팁"),
    ("searchTipsIntro", "Combine any of these — everything must match.", "可任意组合以下条件——全部条件都必须满足。", "以下は自由に組み合わせられます。すべての条件に一致した項目が表示されます。", "아래 조건들은 자유롭게 조합할 수 있으며, 모든 조건과 일치해야 합니다."),
    ("gotIt", "Got it", "知道了", "OK", "확인"),
    ("brokenLinksHint", "Links to notes that don't exist yet — tap to create", "指向尚不存在的笔记的链接——点按即可创建", "まだ存在しないメモへのリンクです。タップで作成できます", "아직 없는 노트로 연결되는 링크입니다. 탭하여 만들 수 있어요"),

    # ---- Search help meanings (syntax literals stay as-is) ----
    ("helpBothWords", "Both words must appear", "两个词都必须出现", "両方の語を含む", "두 단어가 모두 포함되어야 함"),
    ("helpExactPhrase", "An exact phrase", "完全匹配的短语", "完全一致するフレーズ", "정확히 일치하는 구문"),
    ("helpTag", "Notes carrying that tag", "带有该标签的笔记", "そのタグの付いたメモ", "해당 태그가 있는 노트"),
    ("helpPinned", "Pinned items only", "仅已置顶的条目", "固定した項目のみ", "고정된 항목만"),
    ("helpChecklist", "Checklist notes only", "仅清单笔记", "チェックリストのメモのみ", "체크리스트 노트만"),
    ("helpArchived", "Search inside the archive", "在归档中搜索", "アーカイブ内を検索", "보관함 안에서 검색"),
    ("helpDone", "Completed tasks only", "仅已完成的任务", "完了したタスクのみ", "완료된 할 일만"),
    ("helpOverdue", "Tasks past their due time", "已过截止时间的任务", "期限を過ぎたタスク", "마감 시간이 지난 할 일"),
    ("helpHasAttachment", "Has a file attached", "带有附件", "ファイルが添付されている", "파일이 첨부됨"),
    ("helpHasDue", "Has a due date", "设有截止日期", "期限が設定されている", "마감일이 있음"),
    ("helpHasReminder", "Has a reminder armed", "设有提醒", "リマインダーが設定されている", "알림이 설정됨"),
    ("helpHasSubtasks", "Has subtasks", "包含子任务", "サブタスクがある", "하위 작업이 있음"),
    ("helpPriority", "none / low / medium / high", "none／low／medium／high（无／低／中／高）", "none／low／medium／high（なし／低／中／高）", "none / low / medium / high (없음/낮음/보통/높음)"),
    ("helpDue", "today / tomorrow / week / overdue", "today／tomorrow／week／overdue（今天／明天／本周／逾期）", "today／tomorrow／week／overdue（今日／明日／今週／期限切れ）", "today / tomorrow / week / overdue (오늘/내일/이번 주/기한 지남)"),
    ("helpLink", "Notes linking to [[Recipes]]", "链接到 [[Recipes]] 的笔记", "[[Recipes]] にリンクするメモ", "[[Recipes]]로 연결되는 노트"),

    # ---- Search filter chip labels (shown ON the operator chips; the underlying query token stays
    #      the ASCII literal like is:pinned so typed queries and the parser are unchanged) ----
    ("searchChipTag", "Tag", "标签", "タグ", "태그"),
    ("searchChipPinned", "Pinned", "已置顶", "ピン留め", "고정됨"),
    ("searchChipDone", "Done", "已完成", "完了", "완료"),
    ("searchChipOverdue", "Overdue", "已逾期", "期限切れ", "기한 초과"),
    ("searchChipArchived", "Archived", "已归档", "アーカイブ", "보관됨"),
    ("searchChipChecklist", "Checklist", "清单", "チェックリスト", "체크리스트"),
    ("searchChipAttachment", "Attachment", "有附件", "添付あり", "첨부 있음"),
    ("searchChipDue", "Due date", "有截止日", "期限あり", "기한 있음"),
    ("searchChipReminder", "Reminder", "有提醒", "リマインダー", "알림 있음"),
    ("searchChipSubtasks", "Subtasks", "有子任务", "サブタスク", "하위 작업"),
    ("searchChipPriorityHigh", "High priority", "高优先级", "優先度: 高", "높은 우선순위"),
    ("searchChipDueToday", "Due today", "今天到期", "今日期限", "오늘 마감"),
    ("searchChipDueWeek", "Due this week", "本周到期", "今週期限", "이번 주 마감"),
    ("searchChipLink", "Link", "链接", "リンク", "링크"),

    # ---- Image editor ----
    ("toolDraw", "Draw", "涂鸦", "描く", "그리기"),
    ("toolMosaic", "Mosaic", "马赛克", "モザイク", "모자이크"),
    ("toolCrop", "Crop", "裁剪", "切り抜き", "자르기"),
    ("applyCrop", "Apply crop", "应用裁剪", "切り抜きを適用", "자르기 적용"),
    ("editImageTitle", "Edit image", "编辑图片", "画像を編集", "이미지 편집"),
    ("a11yUndoLastEdit", "Undo last edit", "撤销上一步编辑", "直前の編集を元に戻す", "마지막 편집 실행 취소"),
    ("a11yNothingToUndo", "Nothing to undo", "没有可撤销的操作", "元に戻す操作はありません", "실행 취소할 작업 없음"),
    ("savingEllipsis", "Saving…", "正在保存…", "保存中…", "저장 중…"),
    ("imageOpenFailed", "Couldn't open this image for editing.", "无法打开此图片进行编辑。", "この画像を編集用に開けませんでした。", "이 이미지를 편집용으로 열 수 없습니다."),

    # ---- Attachment chips ----
    ("a11yRemoveNamed(name: String)", "Remove {name}", "移除 {name}", "{name}を削除", "{name} 제거"),
    ("a11yDownloadNamed(name: String)", "Download {name}", "下载 {name}", "{name}をダウンロード", "{name} 다운로드"),
    ("a11yOpenNamed(name: String)", "Open {name}", "打开 {name}", "{name}を開く", "{name} 열기"),

    # ---- Palettes ----
    ("paletteSunset", "Sunset", "日落", "サンセット", "석양"),
    ("paletteOcean", "Ocean", "海洋", "オーシャン", "바다"),
    ("paletteForest", "Forest", "森林", "フォレスト", "숲"),
    ("paletteBerry", "Berry", "浆果", "ベリー", "베리"),
    ("paletteMidnight", "Midnight", "午夜", "ミッドナイト", "자정"),
    ("paletteBlush", "Blush", "绯粉", "ブラッシュ", "블러시"),
    ("paletteLavender", "Lavender", "薰衣草", "ラベンダー", "라벤더"),
    ("paletteSage", "Sage", "鼠尾草", "セージ", "세이지"),
    ("paletteSand", "Sand", "沙丘", "サンド", "모래"),
    ("paletteSlate", "Slate", "石板", "スレート", "슬레이트"),
    ("paletteTerracotta", "Terracotta", "陶土", "テラコッタ", "테라코타"),
    ("paletteTeal", "Teal", "青碧", "ティール", "청록"),
    ("paletteAurora", "Aurora", "极光", "オーロラ", "오로라"),
    ("palettePeachDusk", "Peach Dusk", "蜜桃暮色", "ピーチダスク", "피치 더스크"),
    ("paletteCosmic", "Cosmic", "星穹", "コズミック", "코스믹"),

    # ---- Generation foreground service ----
    ("genChannelName", "Assistant replies", "助手回复", "アシスタントの返信", "어시스턴트 답장"),
    ("genChannelDesc", "Shown briefly while the assistant is generating a reply", "助手生成回复时短暂显示", "アシスタントが返信を生成している間だけ表示されます", "어시스턴트가 답장을 생성하는 동안 잠시 표시됩니다"),
    ("genReplyingTitle(name: String)", "{name} is replying…", "{name} 正在回复…", "{name}が返信しています…", "{name}이(가) 답장하는 중…"),
    ("genReplyingBody", "Finishing your reply in the background", "正在后台完成你的回复", "バックグラウンドで返信を仕上げています", "백그라운드에서 답장을 완성하고 있어요"),

    # ---- Date/time patterns (java.time format patterns per language) ----
    ("patternMonthDay", "MMM d", "M月d日", "M月d日", "M월 d일"),
    ("patternTime", "h:mm a", "HH:mm", "HH:mm", "a h:mm"),
    ("patternDateFull", "MMM d, yyyy", "yyyy年M月d日", "yyyy年M月d日", "yyyy년 M월 d일"),
    ("patternTimestamp", "MMM d, h:mm a", "M月d日 HH:mm", "M月d日 HH:mm", "M월 d일 a h:mm"),
    ("patternDateTimeFull", "MMM d, yyyy · h:mm a", "yyyy年M月d日 · HH:mm", "yyyy年M月d日 · HH:mm", "yyyy년 M월 d일 · a h:mm"),

    # ---- Search screen ----
    ("searchEverything", "Search everything", "全局搜索", "すべてを検索", "전체 검색"),
    ("searchPlaceholder", "Notes, tasks, tags, checklists…", "笔记、任务、标签、清单…", "メモ、タスク、タグ、チェックリスト…", "노트, 할 일, 태그, 체크리스트…"),
    ("searchEmptyHint", "Search across every note and task — including archived, completed, and trashed ones. Tap a filter above to narrow it down.", "搜索所有笔记和任务——包括已归档、已完成和回收站中的内容。点按上方筛选器可缩小范围。", "アーカイブ済み・完了済み・ゴミ箱内も含め、すべてのメモとタスクを検索します。上のフィルターで絞り込めます。", "보관됨·완료됨·휴지통 항목까지 모든 노트와 할 일을 검색합니다. 위의 필터를 탭해 범위를 좁혀 보세요."),
    ("searchingEllipsis", "Searching…", "正在搜索…", "検索中…", "검색 중…"),
    ("searchNoMatch(query: String)", "Nothing matched \"{query}\".", "没有与“{query}”匹配的结果。", "「{query}」に一致するものはありませんでした。", "\"{query}\"와(과) 일치하는 항목이 없습니다."),
    ("nResults(count: Int)", "{count} results", "{count} 条结果", "{count}件の結果", "결과 {count}개"),
    ("oneResult", "1 result", "1 条结果", "1件の結果", "결과 1개"),
    ("statusInTrash", "In trash", "在回收站", "ゴミ箱内", "휴지통에 있음"),
    ("statusArchived", "Archived", "已归档", "アーカイブ済み", "보관됨"),
    ("statusCompleted", "Completed", "已完成", "完了済み", "완료됨"),
    ("duePrefix(due: String)", "Due {due}", "截止 {due}", "期限 {due}", "마감 {due}"),
    ("subtasksDone(done: Int, total: Int)", "{done}/{total} subtasks done", "子任务已完成 {done}/{total}", "サブタスク {done}/{total} 完了", "하위 작업 {done}/{total} 완료"),

    # ---- Attachment viewer ----
    ("cantOpenFile", "Couldn't open this file", "无法打开此文件", "このファイルを開けませんでした", "이 파일을 열 수 없습니다"),
    ("openWith", "Open with", "打开方式", "他のアプリで開く", "다른 앱으로 열기"),
    ("noAppCanOpen", "No app can open this file", "没有可打开此文件的应用", "このファイルを開けるアプリがありません", "이 파일을 열 수 있는 앱이 없습니다"),
    ("cantShareFile", "Couldn't share this file", "无法分享此文件", "このファイルを共有できませんでした", "이 파일을 공유할 수 없습니다"),
    ("shareFileChooser", "Share file", "分享文件", "ファイルを共有", "파일 공유"),
    ("savedToast", "Saved", "已保存", "保存しました", "저장됨"),
    ("cantSaveFile", "Couldn't save this file", "无法保存此文件", "このファイルを保存できませんでした", "이 파일을 저장할 수 없습니다"),
    ("cantLoadImage", "Couldn't load this image", "无法加载此图片", "この画像を読み込めませんでした", "이 이미지를 불러올 수 없습니다"),
    ("cantLoadMedia", "Couldn't load this media", "无法加载此媒体", "このメディアを読み込めませんでした", "이 미디어를 불러올 수 없습니다"),
    ("a11yPlay", "Play", "播放", "再生", "재생"),
    ("a11yPause", "Pause", "暂停", "一時停止", "일시정지"),
    ("noPreviewForType", "No in-app preview for this type. Save it, or open it in another app.", "此类型不支持应用内预览。可以保存后用其他应用打开。", "この形式はアプリ内でプレビューできません。保存するか、他のアプリで開いてください。", "이 형식은 앱 내 미리보기를 지원하지 않습니다. 저장하거나 다른 앱에서 열어 보세요."),

    # ---- Clipboard ----
    ("copiedToast", "Copied", "已复制", "コピーしました", "복사됨"),

    # ---- Misc errors ----
    ("errorUnknown", "Unknown error", "未知错误", "不明なエラー", "알 수 없는 오류"),

    # ---- Completed tasks screen ----
    ("markNotDoneTitle", "Mark as not done?", "标记为未完成？", "未完了にしますか？", "완료 안 함으로 표시할까요?"),
    ("markNotDoneBody(title: String)", "\"{title}\" will move back into your active tasks.", "“{title}”将移回你的进行中任务。", "「{title}」はアクティブなタスクに戻ります。", "\"{title}\"이(가) 진행 중인 할 일로 돌아갑니다."),
    ("markNotDone", "Mark as not done", "标记为未完成", "未完了にする", "완료 안 함으로 표시"),
    ("statTotal", "Total", "总计", "合計", "전체"),
    ("statPast7", "Past 7 days", "过去 7 天", "過去7日間", "지난 7일"),
    ("statPast30", "Past 30 days", "过去 30 天", "過去30日間", "지난 30일"),
    ("searchCompleted", "Search completed", "搜索已完成", "完了済みを検索", "완료 항목 검색"),
    ("filterCompletedOn", "Completed on", "完成于", "完了日", "완료일"),
    ("filterCreatedOn", "Created on", "创建于", "作成日", "생성일"),
    ("completedEmpty", "Nothing here yet. Tasks you finish will appear here.", "这里还没有内容。你完成的任务会显示在这里。", "まだ何もありません。完了したタスクがここに表示されます。", "아직 아무것도 없습니다. 완료한 할 일이 여기에 표시됩니다."),
    ("completedNoMatch", "No completed tasks match your search.", "没有符合搜索条件的已完成任务。", "検索に一致する完了済みタスクはありません。", "검색과 일치하는 완료된 할 일이 없습니다."),

    # ---- Trash (tasks + notes) ----
    ("restoreTaskTitle", "Restore this task?", "恢复此任务？", "このタスクを復元しますか？", "이 할 일을 복원할까요?"),
    ("restoreTaskBody(title: String)", "\"{title}\" will be moved out of Trash and back into your tasks. Any reminder it had is re-armed if its due time is still ahead.", "“{title}”将移出回收站，回到你的任务。若截止时间未过，其提醒会重新启用。", "「{title}」はゴミ箱から戻され、タスクに復元されます。期限がまだ先であれば、設定されていたリマインダーは再設定されます。", "\"{title}\"이(가) 휴지통에서 할 일로 복원됩니다. 마감 시간이 아직 남아 있으면 알림이 다시 설정됩니다."),
    ("restoreNoteTitle", "Restore this note?", "恢复此笔记？", "このメモを復元しますか？", "이 노트를 복원할까요?"),
    ("restoreNoteBody(title: String)", "\"{title}\" will be moved out of Trash and back into your notes.", "“{title}”将移出回收站，回到你的笔记。", "「{title}」はゴミ箱から戻され、メモに復元されます。", "\"{title}\"이(가) 휴지통에서 노트로 복원됩니다."),
    ("restoreNoteArchiveBody(title: String)", "\"{title}\" will move out of the archive and back into your notes.", "“{title}”将移出归档，回到你的笔记。", "「{title}」はアーカイブから戻され、メモに復元されます。", "\"{title}\"이(가) 보관함에서 노트로 복원됩니다."),
    ("deleteForeverTitle", "Delete forever?", "永久删除？", "完全に削除しますか？", "영구 삭제할까요?"),
    ("deleteTaskForeverBody(title: String)", "\"{title}\" will be permanently deleted, along with its attachments. This can't be undone.", "“{title}”及其附件将被永久删除。此操作无法撤销。", "「{title}」は添付ファイルとともに完全に削除されます。元に戻せません。", "\"{title}\"이(가) 첨부 파일과 함께 영구 삭제됩니다. 되돌릴 수 없습니다."),
    ("deleteNoteForeverBody(title: String)", "\"{title}\" will be permanently deleted, along with its attachments and version history. This can't be undone.", "“{title}”及其附件和版本历史将被永久删除。此操作无法撤销。", "「{title}」は添付ファイルとバージョン履歴とともに完全に削除されます。元に戻せません。", "\"{title}\"이(가) 첨부 파일 및 버전 기록과 함께 영구 삭제됩니다. 되돌릴 수 없습니다."),
    ("deleteForever", "Delete forever", "永久删除", "完全に削除", "영구 삭제"),
    ("emptyTrashTitle", "Empty trash?", "清空回收站？", "ゴミ箱を空にしますか？", "휴지통을 비울까요?"),
    ("emptyTrashTasksBody(count: Int)", "All {count} tasks in Trash will be permanently deleted. This can't be undone.", "回收站中的全部 {count} 个任务将被永久删除。此操作无法撤销。", "ゴミ箱内の{count}件のタスクがすべて完全に削除されます。元に戻せません。", "휴지통에 있는 할 일 {count}개가 모두 영구 삭제됩니다. 되돌릴 수 없습니다."),
    ("emptyTrashTasksBodyOne", "The 1 task in Trash will be permanently deleted. This can't be undone.", "回收站中的 1 个任务将被永久删除。此操作无法撤销。", "ゴミ箱内の1件のタスクが完全に削除されます。元に戻せません。", "휴지통에 있는 할 일 1개가 영구 삭제됩니다. 되돌릴 수 없습니다."),
    ("emptyTrashNotesBody(count: Int)", "All {count} notes in Trash will be permanently deleted. This can't be undone.", "回收站中的全部 {count} 条笔记将被永久删除。此操作无法撤销。", "ゴミ箱内の{count}件のメモがすべて完全に削除されます。元に戻せません。", "휴지통에 있는 노트 {count}개가 모두 영구 삭제됩니다. 되돌릴 수 없습니다."),
    ("emptyTrashNotesBodyOne", "The 1 note in Trash will be permanently deleted. This can't be undone.", "回收站中的 1 条笔记将被永久删除。此操作无法撤销。", "ゴミ箱内の1件のメモが完全に削除されます。元に戻せません。", "휴지통에 있는 노트 1개가 영구 삭제됩니다. 되돌릴 수 없습니다."),
    ("emptyTrash", "Empty trash", "清空回收站", "ゴミ箱を空にする", "휴지통 비우기"),
    ("trashRetention(days: Int)", "Kept for {days} days, then deleted automatically.", "保留 {days} 天后自动删除。", "{days}日間保持され、その後自動的に削除されます。", "{days}일간 보관된 후 자동으로 삭제됩니다."),
    ("untitledNote", "Untitled note", "无标题笔记", "無題のメモ", "제목 없는 노트"),

    # ---- Archived notes screen ----
    ("untaggedLabel", "Untagged", "未加标签", "タグなし", "태그 없음"),
    ("searchArchive", "Search archive", "搜索归档", "アーカイブを検索", "보관함 검색"),
    ("filterTime", "Time", "时间", "期間", "기간"),
    ("filterTag", "Tag", "标签", "タグ", "태그"),
    ("archivedEmpty", "Nothing archived yet. Notes you archive will appear here.", "还没有归档内容。你归档的笔记会显示在这里。", "まだアーカイブはありません。アーカイブしたメモがここに表示されます。", "아직 보관된 항목이 없습니다. 보관한 노트가 여기에 표시됩니다."),
    ("archivedNoMatch", "No archived notes match your search.", "没有符合搜索条件的归档笔记。", "検索に一致するアーカイブ済みメモはありません。", "검색과 일치하는 보관된 노트가 없습니다."),
    ("checklistDoneCount(done: Int, total: Int)", "Checklist \u00b7 {done}/{total} done", "清单 \u00b7 已完成 {done}/{total}", "チェックリスト \u00b7 {done}/{total} 完了", "체크리스트 \u00b7 {done}/{total} 완료"),

    # ---- Note history screen ----
    ("restoreVersionTitle", "Restore this version?", "恢复此版本？", "このバージョンを復元しますか？", "이 버전을 복원할까요?"),
    ("restoreVersionBody(at: String)", "The note will go back to how it read on {at}. The current text is saved to history first, so you can undo this too.", "笔记将恢复到 {at} 时的内容。当前文本会先保存到历史记录，因此此操作也可撤销。", "メモは{at}時点の内容に戻ります。現在のテキストは先に履歴へ保存されるため、この操作も元に戻せます。", "노트가 {at} 시점의 내용으로 돌아갑니다. 현재 텍스트는 먼저 기록에 저장되므로 이 작업도 되돌릴 수 있습니다."),
    ("restoreThisVersion", "Restore this version", "恢复此版本", "このバージョンを復元", "이 버전 복원"),
    ("historyItemsHeader", "Items", "项目", "項目", "항목"),
    ("historyIntro(title: String)", "Earlier versions of \"{title}\", saved on this device each time you change this note.", "“{title}”的早期版本，每次修改此笔记时都会保存在此设备上。", "「{title}」の以前のバージョン。このメモを変更するたびに、この端末に保存されます。", "\"{title}\"의 이전 버전으로, 이 노트를 변경할 때마다 이 기기에 저장됩니다."),
    ("historyEmpty", "No earlier versions yet. One is saved automatically the first time you change this note's text.", "还没有早期版本。第一次修改此笔记时会自动保存一个。", "以前のバージョンはまだありません。このメモを初めて変更したときに自動的に保存されます。", "아직 이전 버전이 없습니다. 이 노트를 처음 변경할 때 자동으로 저장됩니다."),
    ("emptyChecklistParen", "(empty checklist)", "（空清单）", "（空のチェックリスト）", "(빈 체크리스트)"),

    # ---- App lock screen ----
    ("lockPassword", "Password", "密码", "パスワード", "비밀번호"),
    ("lockWrongPassword", "Wrong password. Try again.", "密码错误，请重试。", "パスワードが違います。もう一度お試しください。", "비밀번호가 틀렸습니다. 다시 시도하세요."),
    ("dangerAuthTitle", "Confirm with your password", "输入密码以确认", "パスワードで確認", "비밀번호로 확인"),
    ("dangerAuthBody", "App Lock is on. Enter your App Lock password to run this action.", "应用锁已开启。执行此操作前，请输入应用锁密码进行确认。", "アプリロックが有効です。この操作を実行するには、アプリロックのパスワードを入力してください。", "앱 잠금이 켜져 있습니다. 이 작업을 실행하려면 앱 잠금 비밀번호를 입력하세요."),
    ("lockUnlock", "Unlock", "解锁", "ロック解除", "잠금 해제"),
    ("biometricUnlockTitle", "Fingerprint unlock", "指纹解锁", "指紋でロック解除", "지문 잠금 해제"),
    ("biometricUnlockDesc", "Unlock the app with your fingerprint instead of typing the password. The password stays as a fallback.", "用指纹解锁应用，无需输入密码。密码仍作为兜底方式保留。", "パスワードを入力する代わりに、指紋でアプリのロックを解除します。パスワードは予備として残ります。", "비밀번호를 입력하는 대신 지문으로 앱 잠금을 해제합니다. 비밀번호는 예비 수단으로 유지됩니다."),
    ("biometricUse", "Use fingerprint", "使用指纹", "指紋を使う", "지문 사용"),
    ("biometricPromptTitle", "Unlock Lucent", "解锁 Lucent", "Lucent のロックを解除", "Lucent 잠금 해제"),
    ("biometricPromptSubtitle", "Verify your identity to unlock", "验证身份以解锁", "本人確認してロックを解除します", "본인 확인 후 잠금을 해제하세요"),
    ("biometricUsePassword", "Use password", "使用密码", "パスワードを使う", "비밀번호 사용"),
    ("biometricFailed", "Fingerprint authentication failed", "指纹验证失败", "指紋認証に失敗しました", "지문 인증에 실패했습니다"),
    ("lockForgotPassword", "Forgot password?", "忘记密码？", "パスワードをお忘れですか？", "비밀번호를 잊으셨나요?"),
    ("lockNoSecurityQuestion", "No security question was set for this lock, so the password can't be recovered on this device.", "此锁未设置安全问题，因此无法在此设备上找回密码。", "このロックにはセキュリティの質問が設定されていないため、この端末ではパスワードを復元できません。", "이 잠금에는 보안 질문이 설정되어 있지 않아 이 기기에서는 비밀번호를 복구할 수 없습니다."),
    ("lockAnswerToReset", "Answer your security question to set a new password.", "回答安全问题以设置新密码。", "セキュリティの質問に答えて新しいパスワードを設定してください。", "보안 질문에 답하여 새 비밀번호를 설정하세요."),
    ("lockSecurityQuestionFallback", "Security question", "安全问题", "セキュリティの質問", "보안 질문"),
    ("lockAnswer", "Answer", "答案", "回答", "답변"),
    ("lockAnswerMismatch", "That answer doesn't match.", "答案不匹配。", "回答が一致しません。", "답변이 일치하지 않습니다."),
    ("lockContinue", "Continue", "继续", "続行", "계속"),
    ("lockBackToPassword", "Back to password", "返回密码", "パスワードに戻る", "비밀번호로 돌아가기"),
    ("lockNewPassword", "New password", "新密码", "新しいパスワード", "새 비밀번호"),
    ("lockConfirmNewPassword", "Confirm new password", "确认新密码", "新しいパスワードを確認", "새 비밀번호 확인"),
    ("lockPasswordsDontMatch", "The passwords don't match.", "两次输入的密码不一致。", "パスワードが一致しません。", "비밀번호가 일치하지 않습니다."),
    ("lockCouldntUpdate", "Couldn't update the password. Try again.", "无法更新密码，请重试。", "パスワードを更新できませんでした。もう一度お試しください。", "비밀번호를 업데이트할 수 없습니다. 다시 시도하세요."),

    # ---- Screen titles ----
    ("screenCompletedTasks", "Completed tasks", "已完成任务", "完了したタスク", "완료된 할 일"),
    ("screenTrash", "Trash", "回收站", "ゴミ箱", "휴지통"),
    ("screenArchivedNotes", "Archived notes", "已归档笔记", "アーカイブしたメモ", "보관된 노트"),
    ("screenVersionHistory", "Version history", "版本历史", "バージョン履歴", "버전 기록"),

    ("searchTrash", "Search trash", "搜索回收站", "ゴミ箱を検索", "휴지통 검색"),
    ("a11yRestore", "Restore", "恢复", "復元", "복원"),
    ("a11yDeleteForever", "Delete forever", "永久删除", "完全に削除", "영구 삭제"),

    # ---- Archived (extra) ----
    ("groupBy", "Group by", "分组方式", "グループ化", "그룹화 기준"),
    ("archivedOn(at: String)", "Archived {at}", "归档于 {at}", "アーカイブ日 {at}", "보관일 {at}"),

    # ---- Note history (extra) ----
    ("screenVersion", "Version", "版本", "バージョン", "버전"),
    ("historyAsOf(at: String)", "As of {at}", "截至 {at}", "{at}時点", "{at} 기준"),
    ("emptyParen", "(empty)", "（空）", "（空）", "(비어 있음)"),

    # ---- App lock (extra) ----
    ("lockIsLocked", "Lucent is locked", "Lucent 已锁定", "Lucentはロックされています", "Lucent이 잠겨 있습니다"),
    ("lockChooseNewPassword", "Choose a new password.", "设置一个新密码。", "新しいパスワードを設定してください。", "새 비밀번호를 설정하세요."),
    ("lockSetPasswordUnlock", "Set password & unlock", "设置密码并解锁", "パスワードを設定して解除", "비밀번호 설정 후 잠금 해제"),

    # ---- Notes & Tasks editors (shared + specific) ----
    ("noteSaved", "Note saved", "笔记已保存", "メモを保存しました", "노트가 저장되었습니다"),
    ("taskSaved", "Task saved", "任务已保存", "タスクを保存しました", "할 일이 저장되었습니다"),
    ("moveToTrashTitle", "Move to trash?", "移到回收站？", "ゴミ箱に移動しますか？", "휴지통으로 옮길까요?"),
    ("moveToTrash", "Move to trash", "移到回收站", "ゴミ箱に移動", "휴지통으로 이동"),
    ("moveNoteTrashBody(title: String, days: Int)", "\"{title}\" will be moved to Trash. You can restore it from there within {days} days.", "“{title}”将移到回收站。你可以在 {days} 天内从那里恢复。", "「{title}」はゴミ箱に移動します。{days}日以内なら復元できます。", "\"{title}\"이(가) 휴지통으로 이동합니다. {days}일 이내에 복원할 수 있습니다."),
    ("moveTaskTrashBody(title: String, days: Int)", "\"{title}\" will be moved to Trash. You can restore it from there within {days} days.", "“{title}”将移到回收站。你可以在 {days} 天内从那里恢复。", "「{title}」はゴミ箱に移動します。{days}日以内なら復元できます。", "\"{title}\"이(가) 휴지통으로 이동합니다. {days}일 이내에 복원할 수 있습니다."),
    ("moveNNotesTrashBody(count: Int, days: Int)", "{count} notes will be moved to Trash. You can restore them within {days} days.", "{count} 条笔记将移到回收站。你可以在 {days} 天内恢复。", "{count}件のメモがゴミ箱に移動します。{days}日以内なら復元できます。", "노트 {count}개가 휴지통으로 이동합니다. {days}일 이내에 복원할 수 있습니다."),
    ("moveOneNoteTrashBody(days: Int)", "1 note will be moved to Trash. You can restore it within {days} days.", "1 条笔记将移到回收站。你可以在 {days} 天内恢复。", "1件のメモがゴミ箱に移動します。{days}日以内なら復元できます。", "노트 1개가 휴지통으로 이동합니다. {days}일 이내에 복원할 수 있습니다."),
    ("moveNTasksTrashBody(count: Int, days: Int)", "{count} tasks will be moved to Trash. You can restore them within {days} days.", "{count} 个任务将移到回收站。你可以在 {days} 天内恢复。", "{count}件のタスクがゴミ箱に移動します。{days}日以内なら復元できます。", "할 일 {count}개가 휴지통으로 이동합니다. {days}일 이내에 복원할 수 있습니다."),
    ("moveOneTaskTrashBody(days: Int)", "1 task will be moved to Trash. You can restore it within {days} days.", "1 个任务将移到回收站。你可以在 {days} 天内恢复。", "1件のタスクがゴミ箱に移動します。{days}日以内なら復元できます。", "할 일 1개가 휴지통으로 이동합니다. {days}일 이내에 복원할 수 있습니다."),

    # ---- Archive / pin toggles (notes) ----
    ("archiveNoteTitle", "Archive this note?", "归档此笔记？", "このメモをアーカイブしますか？", "이 노트를 보관할까요?"),
    ("unarchiveNoteTitle", "Unarchive this note?", "取消归档此笔记？", "このメモのアーカイブを解除しますか？", "이 노트의 보관을 해제할까요?"),
    ("archiveNoteBody(title: String)", "\"{title}\" will move out of your notes and into the archive.", "“{title}”将移出你的笔记，进入归档。", "「{title}」はメモから外され、アーカイブに移動します。", "\"{title}\"이(가) 노트에서 보관함으로 이동합니다."),
    ("unarchiveNoteBody(title: String)", "\"{title}\" will move back into your notes.", "“{title}”将移回你的笔记。", "「{title}」はメモに戻ります。", "\"{title}\"이(가) 노트로 돌아갑니다."),
    ("archive", "Archive", "归档", "アーカイブ", "보관"),
    ("unarchive", "Unarchive", "取消归档", "アーカイブ解除", "보관 해제"),
    ("pinNoteTitle", "Pin this note?", "置顶此笔记？", "このメモを固定しますか？", "이 노트를 고정할까요?"),
    ("unpinNoteTitle", "Unpin this note?", "取消置顶此笔记？", "このメモの固定を解除しますか？", "이 노트의 고정을 해제할까요?"),
    ("pinTaskTitle", "Pin this task?", "置顶此任务？", "このタスクを固定しますか？", "이 할 일을 고정할까요?"),
    ("unpinTaskTitle", "Unpin this task?", "取消置顶此任务？", "このタスクの固定を解除しますか？", "이 할 일의 고정을 해제할까요?"),
    ("pinNoteBody(title: String)", "\"{title}\" will be pinned to the top of your notes.", "“{title}”将被置顶到你的笔记顶部。", "「{title}」はメモの先頭に固定されます。", "\"{title}\"이(가) 노트 맨 위에 고정됩니다."),
    ("unpinNoteBody(title: String)", "\"{title}\" will no longer be pinned to the top.", "“{title}”将不再置顶。", "「{title}」の先頭固定が解除されます。", "\"{title}\"이(가) 더 이상 맨 위에 고정되지 않습니다."),
    ("pinTaskBody(title: String)", "\"{title}\" will be pinned to the top of your tasks.", "“{title}”将被置顶到你的任务顶部。", "「{title}」はタスクの先頭に固定されます。", "\"{title}\"이(가) 할 일 맨 위에 고정됩니다."),
    ("unpinTaskBody(title: String)", "\"{title}\" will no longer be pinned to the top.", "“{title}”将不再置顶。", "「{title}」の先頭固定が解除されます。", "\"{title}\"이(가) 더 이상 맨 위에 고정되지 않습니다."),
    ("actionPin", "Pin", "置顶", "固定", "고정"),
    ("actionUnpin", "Unpin", "取消置顶", "固定解除", "고정 해제"),

    # ---- Complete task / not done (tasks) ----
    ("completeTaskTitle", "Complete this task?", "完成此任务？", "このタスクを完了しますか？", "이 할 일을 완료할까요?"),
    ("completeTaskBody(title: String)", "Mark \"{title}\" as done? It'll move to your completed tasks.", "将“{title}”标记为完成？它将移到已完成任务。", "「{title}」を完了にしますか？完了したタスクに移動します。", "\"{title}\"을(를) 완료로 표시할까요? 완료된 할 일로 이동합니다."),
    ("notDoneTaskBody(title: String)", "\"{title}\" will move back to your active tasks.", "“{title}”将移回你的进行中任务。", "「{title}」はアクティブなタスクに戻ります。", "\"{title}\"이(가) 진행 중인 할 일로 돌아갑니다."),

    # ---- Editor fields ----
    ("startFromTemplate", "Start from a template", "从模板开始", "テンプレートから作成", "템플릿으로 시작"),
    ("fieldTitle", "Title", "标题", "タイトル", "제목"),
    ("checklistNote", "Checklist note", "清单笔记", "チェックリストメモ", "체크리스트 노트"),
    ("labelColour", "Colour", "颜色", "色", "색상"),
    ("labelTags", "Tags", "标签", "タグ", "태그"),
    ("newTag", "New tag", "新标签", "新しいタグ", "새 태그"),
    ("a11yAddTag", "Add tag", "添加标签", "タグを追加", "태그 추가"),
    ("attachFile", "Attach file", "添加附件", "ファイルを添付", "파일 첨부"),
    ("attachFileLeading", " Attach file", " 添加附件", " ファイルを添付", " 파일 첨부"),
    ("labelSubtasks", "Subtasks", "子任务", "サブタスク", "하위 작업"),
    ("screenNote", "Note", "笔记", "メモ", "노트"),
    ("screenTask", "Task", "任务", "タスク", "할 일"),
    ("a11yVersionHistory(count: Int)", "Version history ({count})", "版本历史（{count}）", "バージョン履歴（{count}）", "버전 기록 ({count})"),
    ("createdOn(at: String)", "Created {at}", "创建于 {at}", "作成 {at}", "생성 {at}"),
    ("completedOn(at: String)", "Completed {at}", "完成于 {at}", "完了 {at}", "완료 {at}"),
    # Japanese does not put spaces between words; the old value had a stray space in the middle
    # ("リマインダー オン"), which rendered as an odd gap inside the label.
    ("reminderOn", "Reminder on", "提醒已开启", "リマインダーオン", "알림 켜짐"),

    # ---- Overflow menus / selection ----
    ("selectNotes", "Select notes", "选择笔记", "メモを選択", "노트 선택"),
    ("selectTasks", "Select tasks", "选择任务", "タスクを選択", "할 일 선택"),
    ("a11yCancelSelection", "Cancel selection", "取消选择", "選択を解除", "선택 취소"),
    ("a11yDeleteSelected", "Delete selected", "删除所选", "選択項目を削除", "선택 항목 삭제"),
    ("a11yMoreOptions", "More options", "更多选项", "その他のオプション", "옵션 더 보기"),
    ("a11yRepeats", "Repeats", "重复", "繰り返し", "반복"),
    ("nSubtasks(done: Int, total: Int)", "{done}/{total} subtasks", "子任务 {done}/{total}", "サブタスク {done}/{total}", "하위 작업 {done}/{total}"),
    ("a11yClearDueDate", "Clear due date", "清除截止日期", "期限をクリア", "마감일 지우기"),

    ("noteArchivedToast", "Note archived", "笔记已归档", "メモをアーカイブしました", "노트를 보관했습니다"),
    ("noteRestoredToast", "Note restored", "笔记已恢复", "メモを復元しました", "노트를 복원했습니다"),

    # =====================================================================================
    # Settings — root navigation cards
    # =====================================================================================
    ("settingsAppearanceTitle", "Appearance", "外观", "外観", "모양"),
    ("settingsAppearanceSub", "Theme, background palette, and font", "主题、背景配色与字体", "テーマ・背景パレット・フォント", "테마, 배경 팔레트, 글꼴"),
    ("settingsLanguageTitle", "Language", "语言", "言語", "언어"),
    ("settingsLanguageSub", "Interface language for the whole app", "整个应用的界面语言", "アプリ全体の表示言語", "앱 전체의 인터페이스 언어"),
    ("settingsAssistantTitle", "Assistant", "助手", "アシスタント", "어시스턴트"),
    ("settingsAssistantSub", "Name, style, API, memory, and web", "名称、风格、API、记忆与联网", "名前・スタイル・API・記憶・ウェブ", "이름, 스타일, API, 메모리, 웹"),
    ("settingsEditorTitle", "Editor", "编辑器", "エディター", "편집기"),
    ("settingsEditorSub", "Markdown formatting and links", "Markdown 格式与链接", "Markdown書式とリンク", "마크다운 서식과 링크"),
    ("settingsSecurityTitle", "Security", "安全", "セキュリティ", "보안"),
    ("settingsSecuritySub", "App lock", "应用锁", "アプリロック", "앱 잠금"),
    ("settingsPrivacyTitle", "Privacy", "隐私", "プライバシー", "개인정보"),
    ("settingsPrivacySub", "System integration and local logging", "系统集成与本地日志", "システム連携とローカルログ", "시스템 연동 및 로컬 로그"),
    ("settingsDataTitle", "Data", "数据", "データ", "데이터"),
    ("settingsDataSub", "Backup, restore, and clear all data", "备份、恢复与清除所有数据", "バックアップ・復元・全データ消去", "백업, 복원, 전체 데이터 삭제"),

    # =====================================================================================
    # Settings — Language page
    # =====================================================================================
    ("langPageHint", "Applies immediately to the whole interface. Your notes, tasks, and the assistant's replies are never translated.", "立即应用于整个界面。你的笔记、任务以及助手的回复不会被翻译。", "インターフェース全体に即時適用されます。メモ・タスク・アシスタントの返信は翻訳されません。", "인터페이스 전체에 즉시 적용됩니다. 노트, 할 일, 어시스턴트의 답변은 번역되지 않습니다."),
    ("langSystem", "Follow system", "跟随系统", "システムに従う", "시스템 설정 따르기"),
    ("langSystemDetail(resolved: String)", "Currently: {resolved}", "当前：{resolved}", "現在：{resolved}", "현재: {resolved}"),

    # =====================================================================================
    # Settings — Assistant sub-cards
    # =====================================================================================
    ("settingsPersonalizationTitle", "Personalization", "个性化", "パーソナライズ", "개인화"),
    ("settingsPersonalizationSub", "Assistant name and chat style", "助手名称与聊天风格", "アシスタントの名前とチャットスタイル", "어시스턴트 이름과 대화 스타일"),
    ("settingsApiTitle", "API", "API", "API", "API"),
    ("settingsApiSub(active: String)", "Selection and connection · active: {active}", "选择与连接 · 当前：{active}", "選択と接続 · 使用中：{active}", "선택 및 연결 · 사용 중: {active}"),
    # Shown in place of settingsApiSub while the local model is on: naming the "active" profile
    # there would advertise a connection the app is deliberately not using (the API page itself
    # says "Cloud API frozen"), so the card states the freeze instead. The title above it is
    # already "API", so the subtitle doesn't repeat the word.
    ("settingsApiSubFrozen", "Frozen while the local model is on", "已冻结 · 本地模型运行中", "凍結中 · ローカルモデル実行中", "동결됨 · 로컬 모델 실행 중"),
    ("settingsMemoryWebTitle", "Memory & web", "记忆与联网", "記憶とウェブ", "메모리 및 웹"),
    ("settingsMemoryWebSub", "How much it remembers · web search", "记住多少内容 · 网络搜索", "どこまで覚えるか · ウェブ検索", "기억 범위 · 웹 검색"),
    ("settingsMemoryTitle", "Memory", "记忆", "記憶", "메모리"),
    ("settingsMemorySub", "How much past conversation it remembers", "记住多少历史对话", "どこまで会話を覚えるか", "얼마나 많은 대화를 기억하는지"),
    ("settingsNetworkTitle", "Networking", "联网", "ネットワーク", "네트워크"),
    ("settingsNetworkSub", "Web search for the cloud assistant", "云端助手的联网搜索", "クラウドアシスタントのウェブ検索", "클라우드 어시스턴트 웹 검색"),
    ("settingsLocalModelTitle", "Local model (experimental)", "本地模型（实验性）", "ローカルモデル（実験的）", "로컬 모델(실험적)"),
    ("settingsLocalModelSub", "Run the assistant on-device, no network needed", "在设备上本地运行助手，无需网络", "端末上でアシスタントを実行、ネット接続不要", "기기에서 어시스턴트 실행, 네트워크 불필요"),
    ("lmExperimentalNote", "Experimental — on-device inference is new and can be slow or unstable on some phones.", "实验性功能——端侧推理尚在早期，在部分手机上可能较慢或不稳定。", "実験的機能 — 端末上での推論はまだ新しく、一部の端末では遅かったり不安定な場合があります。", "실험적 기능 — 온디바이스 추론은 아직 초기 단계로 일부 기기에서는 느리거나 불안정할 수 있습니다."),

    # =====================================================================================
    # Settings — Local model (GGUF) page
    # =====================================================================================
    ("lmPageIntro", "Import a GGUF model file and the assistant answers entirely on this device — no internet, no API key, no configuration. A .zip containing a .gguf is unpacked automatically. Memory is freed when you leave the app.", "导入一个 GGUF 模型文件后，助手将完全在本设备上作答——无需网络、无需 API 密钥、无需任何配置。包含 .gguf 的 .zip 会自动解压。退出应用时自动释放内存。", "GGUFモデルファイルをインポートすると、アシスタントはこの端末上だけで応答します。ネット接続もAPIキーも設定も不要です。.ggufを含む.zipは自動的に展開されます。アプリ終了時にメモリは自動解放されます。", "GGUF 모델 파일을 가져오면 어시스턴트가 이 기기에서만 답변합니다. 인터넷도, API 키도, 설정도 필요 없습니다. .gguf가 든 .zip은 자동으로 풀립니다. 앱을 종료하면 메모리가 자동 해제됩니다."),
    ("lmUseLocalToggle", "Use local model", "使用本地模型", "ローカルモデルを使用", "로컬 모델 사용"),
    ("lmUseLocalToggleDesc", "When on, the assistant replies with the imported model instead of a cloud API — fully offline. Web search and cross-conversation memory stay off in this mode. Turn this on first; importing a model and the tool/GPU options appear below once it is on.", "开启后，助手将使用已导入的模型作答，而非云端 API，完全离线。此模式下网络搜索与跨会话记忆保持关闭。请先开启此开关；开启后，下方才会出现导入模型以及工具、GPU 等选项。", "オンにすると、アシスタントはクラウドAPIではなくインポート済みモデルで応答します（完全オフライン）。このモードではウェブ検索と会話をまたぐ記憶はオフのままです。まずこのスイッチをオンにしてください。オンにすると、モデルのインポートやツール・GPUの設定が下に表示されます。", "켜면 어시스턴트가 클라우드 API 대신 가져온 모델로 답변합니다(완전 오프라인). 이 모드에서는 웹 검색과 대화 간 메모리가 꺼진 상태로 유지됩니다. 먼저 이 스위치를 켜세요. 켜면 모델 가져오기와 도구·GPU 옵션이 아래에 나타납니다."),
    ("lmToolsToggle", "Allow tools", "允许使用工具", "ツールの使用を許可", "도구 사용 허용"),
    ("lmToolsToggleDesc", "Off by default. When on, the on-device assistant can create and edit your notes and tasks. It adds a little processing to each reply, so it may be slower on older phones.", "默认关闭。开启后，本地助手可新建和编辑你的笔记与任务。每次回复会多一点计算，旧手机上可能稍慢。", "デフォルトはオフ。オンにすると、オンデバイスのアシスタントがメモやタスクを作成・編集できます。返信ごとに少し処理が増えるため、古い端末では遅くなることがあります。", "기본값은 꺼짐. 켜면 온디바이스 어시스턴트가 메모와 할 일을 만들고 편집할 수 있습니다. 답변마다 처리가 조금 늘어 오래된 기기에서는 느려질 수 있습니다."),
    ("localToolsOffHint", "Local tools are off — the assistant can chat, but can't see or change your notes and tasks. Turn on Settings > Assistant > Local model > Allow tools to let it.", "本地助手的工具权限未开启——它可以聊天，但无法查看或更改你的笔记和任务。到 设置 > 助手 > 本地模型 > 允许使用工具 开启后即可。", "ローカルアシスタントのツールはオフです — 会話はできますが、メモやタスクの閲覧・変更はできません。設定 > アシスタント > ローカルモデル > ツールの使用を許可 をオンにすると使えるようになります。", "로컬 어시스턴트의 도구가 꺼져 있습니다 — 대화는 할 수 있지만 메모와 할 일을 보거나 변경할 수 없습니다. 설정 > 어시스턴트 > 로컬 모델 > 도구 사용 허용을 켜면 사용할 수 있습니다."),
    ("lmToolsWarnTitle", "Allow the local model to use tools?", "允许本地模型使用工具？", "ローカルモデルにツールの使用を許可しますか？", "로컬 모델이 도구를 사용하도록 허용할까요?"),
    ("lmToolsWarnBody", "The on-device assistant will be able to create and edit your notes and tasks. This adds extra processing to each reply, so it can be slower on older phones, and very small models may not follow it reliably. You can turn it off any time.", "本地助手将能够新建和编辑你的笔记与任务。这会给每次回复增加额外计算，旧手机上可能变慢，非常小的模型也可能无法稳定遵循。你可以随时关闭。", "オンデバイスのアシスタントがメモやタスクを作成・編集できるようになります。返信ごとに処理が増えるため古い端末では遅くなることがあり、とても小さいモデルでは正しく従えない場合があります。いつでもオフにできます。", "온디바이스 어시스턴트가 메모와 할 일을 만들고 편집할 수 있게 됩니다. 답변마다 처리가 늘어 오래된 기기에서는 느려질 수 있고, 아주 작은 모델은 안정적으로 따르지 못할 수 있습니다. 언제든지 끌 수 있습니다."),
    ("lmGpuToggle", "Use the GPU", "使用 GPU", "GPUを使用", "GPU 사용"),
    ("lmGpuNeedsModel", "Import a model first to choose the GPU.", "请先导入模型，才能选择使用 GPU。", "GPUを選ぶには、まずモデルをインポートしてください。", "GPU를 선택하려면 먼저 모델을 가져오세요."),
    ("lmGpuToggleDesc", "Off by default — the CPU is used, which runs on every device and is the most stable. The GPU can be faster on some devices but may be unstable.", "默认关闭——使用 CPU，兼容所有设备且最稳定。GPU 在部分设备上更快，但可能不稳定。", "デフォルトはオフ——CPUを使用し、あらゆる端末で動作し最も安定します。GPUは一部の端末で高速ですが、不安定な場合があります。", "기본값은 꺼짐 — CPU를 사용하며 모든 기기에서 작동하고 가장 안정적입니다. GPU는 일부 기기에서 더 빠르지만 불안정할 수 있습니다."),
    ("lmGpuWarnTitle", "Switch the local model to the GPU?", "将本地模型切换到 GPU？", "ローカルモデルをGPUに切り替えますか？", "로컬 모델을 GPU로 전환할까요?"),
    ("lmGpuWarnBody", "GPU (Vulkan) acceleration can be faster on some devices, but graphics drivers vary and it may be less stable on others. If your device can't run it, the model automatically falls back to the CPU. The CPU option is the safest and works everywhere. You can switch back any time.", "GPU（Vulkan）加速在部分设备上更快，但显卡驱动差异大，在另一些设备上可能不太稳定。如果你的设备无法运行，模型会自动回退到 CPU。CPU 最稳、处处可用。你可以随时切回。", "GPU（Vulkan）アクセラレーションは一部の端末で高速ですが、グラフィックスドライバーは端末ごとに異なり、安定しない場合があります。実行できない端末では自動的にCPUに戻ります。CPUが最も安全で、どの端末でも動作します。いつでも戻せます。", "GPU(Vulkan) 가속은 일부 기기에서 더 빠르지만, 그래픽 드라이버가 기기마다 달라 덜 안정적일 수 있습니다. 기기가 실행할 수 없으면 모델이 자동으로 CPU로 되돌아갑니다. CPU가 가장 안전하고 모든 기기에서 작동합니다. 언제든지 되돌릴 수 있습니다."),
    ("lmWarnEnableAnyway", "Turn on", "开启", "オンにする", "켜기"),
    ("lmImportButton", "Import GGUF model…", "导入 GGUF 模型…", "GGUFモデルをインポート…", "GGUF 모델 가져오기…"),
    ("lmReplaceButton", "Replace model…", "更换模型…", "モデルを差し替え…", "모델 교체…"),
    ("lmImporting", "Importing model… this can take a while for large files.", "正在导入模型……大文件可能需要一段时间。", "モデルをインポートしています… 大きなファイルでは時間がかかることがあります。", "모델을 가져오는 중… 큰 파일은 시간이 걸릴 수 있습니다."),
    ("lmImportedToast", "Model imported.", "模型已导入。", "モデルをインポートしました。", "모델을 가져왔습니다."),
    ("lmImportFailedNotGguf", "That file isn't a GGUF model. Pick a .gguf file, or a .zip that contains one.", "该文件不是 GGUF 模型。请选择 .gguf 文件，或包含它的 .zip。", "そのファイルはGGUFモデルではありません。.ggufファイルか、それを含む.zipを選んでください。", "해당 파일은 GGUF 모델이 아닙니다. .gguf 파일 또는 이를 포함한 .zip을 선택해 주세요."),
    ("lmImportFailedNoGgufInZip", "No .gguf file was found inside that zip.", "该 zip 中未找到 .gguf 文件。", "そのzipの中に.ggufファイルが見つかりませんでした。", "해당 zip 안에서 .gguf 파일을 찾지 못했습니다."),
    ("lmImportFailedGeneric(detail: String)", "Couldn't import the model. {detail}", "无法导入模型。{detail}", "モデルをインポートできませんでした。{detail}", "모델을 가져오지 못했습니다. {detail}"),
    ("lmCurrentModel(name: String, size: String)", "Imported: {name} · {size}", "已导入：{name} · {size}", "インポート済み：{name} · {size}", "가져옴: {name} · {size}"),
    ("lmNoModelYet", "No model imported yet.", "尚未导入模型。", "モデルはまだインポートされていません。", "아직 가져온 모델이 없습니다."),
    ("lmDeleteButton", "Delete model", "删除模型", "モデルを削除", "모델 삭제"),
    ("lmDeleteTitle", "Delete the local model?", "删除本地模型？", "ローカルモデルを削除しますか？", "로컬 모델을 삭제할까요?"),
    ("lmDeleteBody(name: String)", "\"{name}\" will be removed from this device and its memory freed. The assistant will need a cloud API again until you import another model.", "“{name}”将从此设备中移除并释放其内存。在导入其他模型之前，助手将重新需要云端 API。", "「{name}」はこの端末から削除され、メモリが解放されます。別のモデルをインポートするまで、アシスタントは再びクラウドAPIが必要になります。", "\"{name}\"이(가) 이 기기에서 제거되고 메모리가 해제됩니다. 다른 모델을 가져올 때까지 어시스턴트는 다시 클라우드 API가 필요합니다."),
    ("lmDeletedToast", "Model deleted.", "模型已删除。", "モデルを削除しました。", "모델을 삭제했습니다."),
    ("lmUnsupportedAbiNote", "This device's processor isn't supported by the local model engine, so this feature is unavailable here.", "此设备的处理器不受本地模型引擎支持，因此该功能在此设备上不可用。", "この端末のプロセッサはローカルモデルエンジンに対応していないため、この機能は利用できません。", "이 기기의 프로세서는 로컬 모델 엔진에서 지원되지 않아 이 기능을 사용할 수 없습니다."),
    ("lmSizeHint", "Tip: on most phones, models around 1–4 GB (Q4 quantization) give the best balance of speed and quality.", "提示：在大多数手机上，约 1–4 GB（Q4 量化）的模型在速度与质量之间平衡最佳。", "ヒント：多くのスマートフォンでは、約1〜4 GB（Q4量子化）のモデルが速度と品質のバランスに優れています。", "팁: 대부분의 휴대폰에서는 약 1–4GB(Q4 양자화) 모델이 속도와 품질의 균형이 가장 좋습니다."),

    # ---- Local model: multiple models, custom names, active selection (task requirement) ----
    ("lmModelsTitle", "Local models", "本地模型", "ローカルモデル", "로컬 모델"),
    ("lmActiveTag", "Active", "使用中", "使用中", "사용 중"),
    ("lmRenameA11y", "Rename model", "重命名模型", "モデルの名前を変更", "모델 이름 바꾸기"),
    ("lmDeleteA11y", "Delete model", "删除模型", "モデルを削除", "모델 삭제"),
    ("lmSlotsFullHint(max: Int)", "You've imported the maximum of {max} models. Delete one to add another.", "已导入上限 {max} 个模型。删除一个后才能再添加。", "モデルは上限の{max}個までインポート済みです。追加するには1つ削除してください。", "모델을 최대 {max}개까지 가져왔습니다. 추가하려면 하나를 삭제하세요."),
    ("lmImportFailedTooMany(max: Int)", "You can keep at most {max} models. Delete one before importing another.", "最多只能保存 {max} 个模型。请先删除一个再导入。", "モデルは最大{max}個まで保存できます。別のものをインポートする前に1つ削除してください。", "모델은 최대 {max}개까지 보관할 수 있습니다. 다른 모델을 가져오기 전에 하나를 삭제하세요."),
    ("lmNameModelTitle", "Name this model", "为该模型命名", "このモデルに名前を付ける", "이 모델 이름 지정"),
    ("lmNameModelBody", "Give this model a name so you can tell it apart from your other local models.", "给它起个名字，方便与你的其他本地模型区分。", "他のローカルモデルと区別できるよう、名前を付けてください。", "다른 로컬 모델과 구분할 수 있도록 이름을 지정하세요."),
    ("lmModelNameField", "Model name", "模型名称", "モデル名", "모델 이름"),
    ("lmImportConfirm", "Import", "导入", "インポート", "가져오기"),
    ("lmRenameTitle", "Rename model", "重命名模型", "モデルの名前を変更", "모델 이름 바꾸기"),
    ("lmLoadingIndicator", "Loading the local model…", "正在加载本地模型……", "ローカルモデルを読み込み中…", "로컬 모델을 불러오는 중…"),

    # ---- Local model: warn before turning the whole feature on (freezes the API; heavy on RAM) ----
    ("lmUseLocalWarnTitle", "Turn on the local model?", "开启本地模型？", "ローカルモデルをオンにしますか？", "로컬 모델을 켤까요?"),
    ("lmUseLocalWarnBody", "While the local model is on, the cloud API is frozen and won't be called — the assistant answers entirely on this device. Running a model uses a lot of memory (RAM), so it's best not to close the app while it's replying: quitting interrupts the reply. Closing the app also frees that memory. You can turn this off any time to go back to the cloud API.", "开启本地模型后，云端 API 将被冻结、不再调用——助手完全在本设备上作答。运行模型会占用大量运行内存（RAM），因此在它回复期间最好不要退出应用：退出会中断回复。退出应用也会释放这部分内存。你可以随时关闭以切回云端 API。", "ローカルモデルがオンの間、クラウドAPIは凍結され呼び出されません。アシスタントはこの端末上だけで応答します。モデルの実行は多くのメモリ（RAM）を使うため、応答中はアプリを閉じないことをおすすめします。終了すると応答が中断されます。アプリを閉じるとそのメモリも解放されます。いつでもオフにしてクラウドAPIに戻せます。", "로컬 모델이 켜져 있는 동안 클라우드 API는 동결되어 호출되지 않습니다. 어시스턴트는 이 기기에서만 답변합니다. 모델 실행은 많은 메모리(RAM)를 사용하므로 답변 중에는 앱을 닫지 않는 것이 좋습니다. 종료하면 답변이 중단됩니다. 앱을 닫으면 그 메모리도 해제됩니다. 언제든지 꺼서 클라우드 API로 돌아갈 수 있습니다."),

    # ---- API page: frozen banner while local model mode is on ----
    ("apiFrozenTitle", "Cloud API frozen", "云端 API 已冻结", "クラウドAPIは凍結中", "클라우드 API 동결됨"),
    ("apiFrozenBody", "Local model mode is on, so the assistant answers on-device and the cloud API is not used. Saving and fetching models are disabled here until you turn the local model off.", "本地模型模式已开启，助手将在本设备上作答，云端 API 不会被使用。在关闭本地模型前，此页的保存和获取模型已禁用。", "ローカルモデルモードがオンのため、アシスタントは端末上で応答し、クラウドAPIは使用されません。ローカルモデルをオフにするまで、このページの保存とモデル取得は無効です。", "로컬 모델 모드가 켜져 있어 어시스턴트가 기기에서 답변하며 클라우드 API는 사용되지 않습니다. 로컬 모델을 끌 때까지 이 페이지의 저장과 모델 가져오기는 비활성화됩니다."),
    ("apiFrozenManage", "Local model settings", "本地模型设置", "ローカルモデル設定", "로컬 모델 설정"),

    # =====================================================================================
    # Settings — Personalization page
    # =====================================================================================
    ("fieldAssistantName", "Assistant name", "助手名称", "アシスタントの名前", "어시스턴트 이름"),
    ("fieldChatStyle", "Chat style", "聊天风格", "チャットスタイル", "대화 스타일"),
    ("typingHapticsTitle", "Typing haptics", "打字触感", "入力時の振動", "타이핑 햅틱"),
    ("typingHapticsDesc", "A faint vibration as each character of a reply appears, and a single firmer pulse when the reply finishes.", "回复的每个字符出现时轻微震动，回复结束时给出一次更明显的震动。", "返信の文字が表示されるたびにかすかに振動し、返信が完了すると一度だけはっきり振動します。", "답변의 글자가 나타날 때마다 약하게 진동하고, 답변이 끝나면 한 번 더 뚜렷하게 진동합니다."),
    ("settingsUnsavedBody", "You have unsaved changes to your assistant settings. Save them before leaving?", "你的助手设置有尚未保存的更改。要在离开前保存吗？", "アシスタント設定に未保存の変更があります。移動する前に保存しますか？", "어시스턴트 설정에 저장되지 않은 변경 사항이 있습니다. 나가기 전에 저장할까요?"),
    ("apiSavedToast", "API saved", "API 已保存", "APIを保存しました", "API가 저장되었습니다"),

    # =====================================================================================
    # Settings — Memory & web page
    # =====================================================================================
    ("memoryCostTitle", "Memory & cost", "记忆与成本", "記憶とコスト", "메모리와 비용"),
    ("memoryCostDesc", "How much past conversation is sent with each message. More memory gives better continuity but costs more tokens per reply. Changing this never deletes anything — your messages are always saved.", "决定每条消息随附多少历史对话。记忆越多，连贯性越好，但每次回复消耗的 token 也越多。更改此设置不会删除任何内容——你的消息始终会被保存。", "各メッセージと一緒に送る過去の会話量です。多いほど文脈は保たれますが、返信ごとのトークン消費も増えます。この設定を変えても何も削除されません。メッセージは常に保存されます。", "각 메시지와 함께 보내는 과거 대화의 양입니다. 많을수록 맥락은 좋아지지만 답변당 토큰 비용이 늘어납니다. 이 설정을 바꿔도 아무것도 삭제되지 않으며 메시지는 항상 저장됩니다."),
    ("memoryLowTitle", "Low · single message", "低 · 单条消息", "低 · 1メッセージのみ", "낮음 · 단일 메시지"),
    ("memoryLowDesc", "Only your latest message is sent. Cheapest, but the assistant won't remember earlier turns.", "只发送你的最新一条消息。最省，但助手不会记得之前的对话。", "最新のメッセージだけを送ります。最も安価ですが、以前のやり取りは覚えていません。", "가장 최근 메시지만 보냅니다. 가장 저렴하지만 이전 대화는 기억하지 못합니다."),
    ("memoryMediumTitle", "Medium · this conversation", "中 · 当前会话", "中 · この会話全体", "중간 · 현재 대화"),
    ("memoryMediumDesc", "The whole current conversation is sent. Balanced — good continuity at a moderate cost.", "发送当前完整会话。较为均衡——连贯性好，成本适中。", "現在の会話全体を送ります。バランス型で、適度なコストで良い連続性が得られます。", "현재 대화 전체를 보냅니다. 균형형으로, 적당한 비용에 좋은 연속성을 제공합니다."),
    ("memoryHighTitle", "High · across conversations", "高 · 跨会话", "高 · 会話をまたぐ", "높음 · 대화 간"),
    ("memoryHighDesc", "Also mixes in recent context from your other chats. Most context, highest cost per reply.", "还会混入你其他会话的近期上下文。上下文最多，每次回复成本最高。", "他のチャットの最近の文脈も加えます。文脈は最も多く、返信ごとのコストも最大です。", "다른 대화의 최근 맥락도 함께 넣습니다. 맥락은 가장 많지만 답변당 비용도 가장 큽니다."),
    ("webSearchTitle", "Web search", "网络搜索", "ウェブ検索", "웹 검색"),
    ("webSearchDesc", "Let the assistant look things up on the web when you ask about current or factual topics. When off, it answers from what it already knows.", "当你询问时事或事实类问题时，允许助手上网查询。关闭后，它只依据已有知识作答。", "最新の話題や事実に関する質問のとき、アシスタントがウェブで調べられるようにします。オフの場合は既知の知識だけで答えます。", "최신 정보나 사실 관련 질문에 어시스턴트가 웹에서 찾아볼 수 있게 합니다. 끄면 이미 아는 지식으로만 답합니다."),

    # =====================================================================================
    # Settings — API page
    # =====================================================================================
    ("apiSelectionTitle", "API selection", "API 选择", "APIの選択", "API 선택"),
    ("apiSelectionDesc(max: Int)", "Choose which saved API the assistant uses. You can keep up to {max}.", "选择助手使用哪个已保存的 API。最多可保存 {max} 个。", "アシスタントが使う保存済みAPIを選びます。最大{max}件まで保存できます。", "어시스턴트가 사용할 저장된 API를 선택합니다. 최대 {max}개까지 저장할 수 있습니다."),
    ("apiNoModel", "no model", "未选模型", "モデル未選択", "모델 없음"),
    ("apiAddButton", "Add API", "添加 API", "APIを追加", "API 추가"),
    ("apiDeleteA11y", "Delete this API", "删除此 API", "このAPIを削除", "이 API 삭제"),
    ("apiEditTitle", "Edit selected API", "编辑所选 API", "選択中のAPIを編集", "선택한 API 편집"),
    ("fieldName", "Name", "名称", "名前", "이름"),
    ("fieldApiKey", "API key", "API 密钥", "APIキー", "API 키"),
    ("a11yToggleKeyVisibility", "Toggle key visibility", "切换密钥可见性", "キーの表示を切り替え", "키 표시 전환"),
    ("apiSpecTitle", "API specification", "API 规范", "API仕様", "API 사양"),
    ("apiSpecOpenAi", "OpenAI-compatible", "OpenAI 兼容", "OpenAI互換", "OpenAI 호환"),
    ("apiSpecAnthropic", "Anthropic-compatible", "Anthropic 兼容", "Anthropic互換", "Anthropic 호환"),
    ("apiSpecGoogle", "Google-compatible", "Google 兼容", "Google互換", "Google 호환"),
    ("apiConnectionTitle", "API connection", "API 连接", "API接続", "API 연결"),
    ("fieldBaseUrl", "Base URL", "基础 URL", "ベースURL", "기본 URL"),
    ("fetchModels", "Fetch available models", "获取可用模型", "利用可能なモデルを取得", "사용 가능한 모델 가져오기"),
    ("apiUrlRequired", "Enter the API address first.", "请先填写 API 地址。", "先に API アドレスを入力してください。", "먼저 API 주소를 입력하세요."),
    ("fieldModel", "Model", "模型", "モデル", "모델"),
    ("chooseModel", "Choose a model", "选择模型", "モデルを選択", "모델 선택"),
    ("currentModelHint(model: String)", "Currently: {model}. Fetch models to change it.", "当前：{model}。获取模型列表后可更改。", "現在：{model}。変更するにはモデル一覧を取得してください。", "현재: {model}. 변경하려면 모델 목록을 가져오세요."),
    ("saveApi", "Save API", "保存 API", "APIを保存", "API 저장"),
    ("apiDeleteConfirmTitle", "Delete this API?", "删除此 API？", "このAPIを削除しますか？", "이 API를 삭제할까요?"),
    ("apiDeleteConfirmBody(name: String)", "This removes \"{name}\", including its saved key, from this device. It can't be undone. If the key isn't saved anywhere else you'll need to paste it in again to use this API.", "这将从此设备移除“{name}”，包括其已保存的密钥，且无法撤销。若密钥没有保存在其他地方，再次使用此 API 时需要重新粘贴。", "「{name}」は保存済みキーを含めてこの端末から削除され、元に戻せません。キーを他に保存していない場合、このAPIを使うには再入力が必要です。", "\"{name}\"이(가) 저장된 키와 함께 이 기기에서 제거되며 되돌릴 수 없습니다. 키를 다른 곳에 저장하지 않았다면 이 API를 쓰려면 다시 붙여넣어야 합니다."),
    ("apiFallbackName(n: Int)", "API {n}", "API {n}", "API {n}", "API {n}"),
    ("thisApiFallback", "this API", "此 API", "このAPI", "이 API"),
    ("apiUrlExampleOpenAi", "e.g. https://api.openai.com/v1", "例如 https://api.openai.com/v1", "例：https://api.openai.com/v1", "예: https://api.openai.com/v1"),
    ("apiUrlExampleAnthropic", "e.g. https://api.anthropic.com/v1", "例如 https://api.anthropic.com/v1", "例：https://api.anthropic.com/v1", "예: https://api.anthropic.com/v1"),
    ("apiUrlExampleGoogle", "e.g. https://generativelanguage.googleapis.com/v1beta", "例如 https://generativelanguage.googleapis.com/v1beta", "例：https://generativelanguage.googleapis.com/v1beta", "예: https://generativelanguage.googleapis.com/v1beta"),
    ("errorWithDetail(kind: String, detail: String)", "{kind}: {detail}", "{kind}：{detail}", "{kind}：{detail}", "{kind}: {detail}"),

    # =====================================================================================
    # Settings — Theme / Background / Font pages
    # =====================================================================================
    ("settingsThemeTitle", "Theme", "主题", "テーマ", "테마"),
    ("settingsThemeSub", "Light, dark, the system, or a Monet tint", "浅色、深色、跟随系统或莫奈色调", "ライト・ダーク・システム・モネ調", "라이트, 다크, 시스템 또는 모네 톤"),
    ("settingsBackgroundTitle", "Background", "背景", "背景", "배경"),
    ("settingsBackgroundSub", "Colour palette behind the glass", "玻璃背后的配色", "ガラス越しのカラーパレット", "글래스 뒤의 색상 팔레트"),
    ("backgroundAnimationTitle", "Drifting background", "背景浮动效果", "背景のドリフト", "배경 흐름 효과"),
    ("backgroundAnimationDesc", "Let the colour blobs drift and merge. Turn off for a still, flat theme colour.", "让色块漂浮融合。关闭则为静态的纯主题色。", "色のブロブが漂って溶け合います。オフにすると静止した単色の背景になります。", "색 덩어리가 흐르며 어우러집니다. 끄면 정지된 단색 배경이 됩니다."),
    ("backgroundPaletteDisabledHint", "Drifting background is off — turn it on to choose colours.", "未开启背景浮动效果，无法选择配色", "背景のドリフトがオフのため、配色を選択できません", "배경 흐름 효과가 꺼져 있어 색상을 선택할 수 없습니다"),
    ("settingsFontTitle", "Font", "字体", "フォント", "글꼴"),
    ("fontSystemLabel", "System", "系统", "システム", "시스템"),
    ("fontNoneImportedHint", "No fonts imported yet — the app is using your device's font.", "尚未导入任何字体——应用正在使用设备系统字体。", "まだフォントがインポートされていません。アプリは端末のシステムフォントを使用しています。", "아직 가져온 글꼴이 없습니다. 앱은 기기의 시스템 글꼴을 사용 중입니다."),
    ("fontImportButton", "Import font…", "导入字体…", "フォントをインポート…", "글꼴 가져오기…"),
    ("fontImporting", "Importing font…", "正在导入字体…", "フォントをインポートしています…", "글꼴을 가져오는 중…"),
    ("fontImportedToast", "Font imported.", "字体已导入。", "フォントをインポートしました。", "글꼴을 가져왔습니다."),
    ("fontNameTitle", "Name this font", "为该字体命名", "このフォントに名前を付ける", "이 글꼴 이름 지정"),
    ("fontNameBody", "Give this font a name so you can tell it apart in the list.", "给它起个名字，方便在列表中辨认。", "一覧で見分けられるよう、名前を付けてください。", "목록에서 구분할 수 있도록 이름을 지정하세요."),
    ("fontNameField", "Font name", "字体名称", "フォント名", "글꼴 이름"),
    ("fontDeleteTitle", "Delete this font?", "删除此字体？", "このフォントを削除しますか？", "이 글꼴을 삭제할까요?"),
    ("fontDeleteBody(name: String)", "\"{name}\" will be removed from this device. Anything set in it falls back to the system font.", "“{name}”将从此设备中移除。使用它的文本将改用系统字体显示。", "「{name}」はこの端末から削除されます。使用中のテキストはシステムフォントで表示されます。", "\"{name}\"이(가) 이 기기에서 제거됩니다. 이 글꼴을 사용하던 텍스트는 시스템 글꼴로 표시됩니다."),
    ("fontDeleteA11y", "Delete font", "删除字体", "フォントを削除", "글꼴 삭제"),
    ("fontSlotsFullHint(max: Int)", "You've imported the maximum of {max} fonts. Delete one to add another.", "已导入上限 {max} 个字体。删除一个后才能再添加。", "フォントは上限の{max}個までインポート済みです。追加するには1つ削除してください。", "글꼴을 최대 {max}개까지 가져왔습니다. 추가하려면 하나를 삭제하세요."),
    ("fontImportFailedNotFont", "That file isn't a usable font. Pick a .ttf, .otf, or .ttc file.", "该文件不是可用的字体。请选择 .ttf、.otf 或 .ttc 文件。", "そのファイルは使用できるフォントではありません。.ttf / .otf / .ttc ファイルを選んでください。", "해당 파일은 사용할 수 있는 글꼴이 아닙니다. .ttf, .otf 또는 .ttc 파일을 선택해 주세요."),
    ("fontImportFailedTooMany(max: Int)", "You can keep at most {max} fonts. Delete one before importing another.", "最多只能保存 {max} 个字体。请先删除一个再导入。", "フォントは最大{max}個まで保存できます。別のものをインポートする前に1つ削除してください。", "글꼴은 최대 {max}개까지 보관할 수 있습니다. 다른 글꼴을 가져오기 전에 하나를 삭제하세요."),
    ("fontImportFailedGeneric(detail: String)", "Couldn't import the font. {detail}", "无法导入字体。{detail}", "フォントをインポートできませんでした。{detail}", "글꼴을 가져오지 못했습니다. {detail}"),
    ("settingsFontSub", "Typeface used across the app", "整个应用使用的字体", "アプリ全体で使う書体", "앱 전체에서 사용하는 서체"),
    ("paletteCycleAuto", "Cycle (auto)", "循环（自动）", "サイクル（自動）", "순환(자동)"),
    ("paletteGroupSolid", "Solid", "纯色", "単色", "단색"),
    ("paletteGroupGradient", "Gradient", "渐变", "グラデーション", "그라데이션"),
    ("paletteGroupClassic", "Classic", "经典", "クラシック", "클래식"),

    # =====================================================================================
    # Settings — Editor page
    # =====================================================================================
    ("markdownFormattingTitle", "Markdown formatting", "Markdown 格式", "Markdown書式", "마크다운 서식"),
    ("markdownFormattingDesc", "When on, note bodies are rendered as Markdown — # headings, **bold**, *italic*, `code`, and lists — and the composer shows a formatting hint. When off, notes are shown exactly as typed, with no styling and no hint. Off by default.", "开启后，笔记正文将按 Markdown 渲染——# 标题、**加粗**、*斜体*、`代码` 和列表——编辑框也会显示格式提示。关闭后，笔记原样显示，无任何样式与提示。默认关闭。", "オンにすると、メモ本文はMarkdownとして表示されます（# 見出し、**太字**、*斜体*、`コード`、リスト）。入力欄にも書式ヒントが出ます。オフでは入力したままの見た目で、装飾もヒントもありません。既定はオフです。", "켜면 노트 본문이 마크다운으로 표시됩니다(# 제목, **굵게**, *기울임*, `코드`, 목록). 입력창에도 서식 힌트가 표시됩니다. 끄면 입력한 그대로 표시되며 스타일과 힌트가 없습니다. 기본은 꺼짐입니다."),
    ("linksTitle", "Links", "链接", "リンク", "링크"),
    ("linksDesc", "Links come in two kinds. Internal links use double brackets around a note's title, like [[Shopping list]]: they become tappable and jump straight to that note, and the note you link to shows a \"Linked from\" reference back. If the title doesn't exist yet the link shows in red and tapping it creates that note. External links use the standard Markdown form [text](https://example.com) and open in your browser. When this is off, both are shown as plain text and do nothing. This works with or without Markdown formatting — with Markdown off, your text is shown exactly as typed and links still work.", "链接分两种。内部链接用双中括号包住笔记标题，如 [[购物清单]]：可以点按并直接跳转到该笔记，被链接的笔记也会显示“被链接自”的反向引用。若标题尚不存在，链接显示为红色，点按即可创建该笔记。外部链接使用标准 Markdown 形式 [文字](https://example.com)，在浏览器中打开。关闭后，两者都只作为纯文本显示且不可点按。此功能与 Markdown 格式相互独立——即使关闭 Markdown，文本原样显示时链接仍然有效。", "リンクは2種類あります。内部リンクはメモのタイトルを二重角括弧で囲みます（例：[[買い物リスト]]）。タップするとそのメモへ直接ジャンプし、リンク先のメモには「リンク元」の参照が表示されます。タイトルがまだ存在しない場合は赤く表示され、タップするとそのメモが作成されます。外部リンクは標準のMarkdown形式 [テキスト](https://example.com) で、ブラウザーで開きます。オフのときは、どちらもただのテキストとして表示され、何も起こりません。Markdown書式のオン・オフとは独立して動作します。Markdownがオフでも、テキストは入力どおりに表示されつつリンクは機能します。", "링크는 두 가지입니다. 내부 링크는 노트 제목을 이중 대괄호로 감쌉니다(예: [[쇼핑 목록]]). 탭하면 해당 노트로 바로 이동하며, 링크된 노트에는 \"링크됨\" 역참조가 표시됩니다. 제목이 아직 없으면 빨간색으로 표시되고 탭하면 그 노트가 생성됩니다. 외부 링크는 표준 마크다운 형식 [텍스트](https://example.com)를 사용하며 브라우저에서 열립니다. 끄면 둘 다 일반 텍스트로만 표시되고 동작하지 않습니다. 마크다운 서식과는 독립적으로 작동합니다. 마크다운이 꺼져 있어도 텍스트는 입력한 그대로 표시되면서 링크는 계속 작동합니다."),

    # =====================================================================================
    # Settings — Security page (app lock)
    # =====================================================================================
    ("appLockTitle", "App lock", "应用锁", "アプリロック", "앱 잠금"),
    ("appLockDesc", "Require a password each time Lucent is opened from closed. You can add an optional security question to reset the password if you forget it. Neither the password nor the answer is stored — only a salted hash.", "每次从关闭状态打开 Lucent 时都需要输入密码。可以选择添加一个安全问题，以便忘记密码时重置。密码和答案本身都不会被存储——只保存加盐哈希。", "Lucentを閉じた状態から開くたびにパスワードを要求します。忘れたときに備えて、任意でセキュリティの質問を設定してパスワードをリセットできます。パスワードも答えも保存されません。保存されるのはソルト付きハッシュだけです。", "Lucent를 완전히 종료한 상태에서 열 때마다 비밀번호를 요구합니다. 잊어버렸을 때 재설정할 수 있도록 보안 질문을 선택적으로 추가할 수 있습니다. 비밀번호와 답변 자체는 저장되지 않으며 솔트가 적용된 해시만 저장됩니다."),
    ("appLockSetupTitle", "Set up app lock", "设置应用锁", "アプリロックの設定", "앱 잠금 설정"),
    ("appLockSetupBody", "Choose a password you'll enter each time Lucent opens. The security question is optional, but it is the only way to reset the password if you forget it. Neither the password nor the answer is stored — only a salted hash — so if you forget BOTH, the only way back in is to clear all data.", "设置一个每次打开 Lucent 时输入的密码。安全问题为可选项，但它是忘记密码时唯一的重置途径。密码和答案都不会被存储——只保存加盐哈希——因此如果两者都忘记，唯一的办法就是清除所有数据。", "Lucentを開くたびに入力するパスワードを決めてください。セキュリティの質問は任意ですが、パスワードを忘れたときの唯一のリセット手段です。パスワードも答えも保存されず、ソルト付きハッシュのみが保存されます。両方とも忘れた場合、戻る方法は全データの消去だけです。", "Lucent를 열 때마다 입력할 비밀번호를 정하세요. 보안 질문은 선택 사항이지만, 비밀번호를 잊었을 때 재설정할 수 있는 유일한 방법입니다. 비밀번호와 답변은 저장되지 않고 솔트 해시만 저장되므로, 둘 다 잊으면 되돌아갈 방법은 전체 데이터 삭제뿐입니다."),
    ("fieldConfirmPassword", "Confirm password", "确认密码", "パスワードを確認", "비밀번호 확인"),
    ("fieldSecurityQuestionOptional", "Security question (optional)", "安全问题（可选）", "セキュリティの質問（任意）", "보안 질문(선택)"),
    ("fieldAnswerOptional", "Answer (optional)", "答案（可选）", "答え（任意）", "답변(선택)"),
    ("lockErrTooShort", "Use a password of at least 4 characters.", "请使用至少 4 个字符的密码。", "4文字以上のパスワードを設定してください。", "4자 이상의 비밀번호를 사용하세요."),
    ("lockErrMismatch", "The passwords don't match.", "两次输入的密码不一致。", "パスワードが一致しません。", "비밀번호가 일치하지 않습니다."),
    ("lockErrNeedAnswer", "Enter an answer to your security question.", "请输入安全问题的答案。", "セキュリティの質問の答えを入力してください。", "보안 질문의 답변을 입력해 주세요."),
    ("lockErrNeedQuestion", "Enter the question this answer belongs to.", "请输入该答案对应的问题。", "この答えに対応する質問を入力してください。", "이 답변에 해당하는 질문을 입력해 주세요."),
    ("turnOn", "Turn on", "开启", "オンにする", "켜기"),
    ("turnOff", "Turn off", "关闭", "オフにする", "끄기"),
    ("appLockOnToast", "App lock is on.", "应用锁已开启。", "アプリロックをオンにしました。", "앱 잠금이 켜졌습니다."),
    ("appLockOffToast", "App lock is off.", "应用锁已关闭。", "アプリロックをオフにしました。", "앱 잠금이 꺼졌습니다."),
    ("appLockDisableTitle", "Turn off app lock?", "关闭应用锁？", "アプリロックをオフにしますか？", "앱 잠금을 끄시겠어요?"),
    ("appLockDisableBody", "Without the lock, anyone who opens Lucent can read your notes and tasks — no password required. Enter your current password to confirm.", "关闭后，任何人打开 Lucent 都能无需密码查看你的笔记和任务。请输入当前密码以确认。", "ロックを解除すると、Lucent を開いた人は誰でもパスワードなしでメモやタスクを見られます。確認のため現在のパスワードを入力してください。", "잠금을 끄면 Lucent를 여는 누구나 비밀번호 없이 메모와 할 일을 볼 수 있습니다. 확인을 위해 현재 비밀번호를 입력하세요."),
    ("noRecoveryTitle", "Turn on without a way to reset it?", "在没有重置途径的情况下开启？", "リセット手段なしでオンにしますか？", "재설정 방법 없이 켤까요?"),
    ("noRecoveryBody", "Without a security question there is no password reset. If you forget this password, the only way back into Lucent is to clear all data — every note, task, attachment and conversation on this device, permanently. Nobody, including the app itself, can recover it for you, because the password is never stored anywhere.", "没有安全问题就无法重置密码。如果忘记此密码，唯一能重新进入 Lucent 的方式就是清除所有数据——此设备上的每条笔记、任务、附件和会话都将被永久删除。包括应用本身在内，没有任何人能为你找回密码，因为它从未被存储在任何地方。", "セキュリティの質問がないと、パスワードのリセット手段はありません。このパスワードを忘れた場合、Lucentに戻る唯一の方法は全データの消去です。この端末上のすべてのメモ・タスク・添付ファイル・会話が完全に削除されます。パスワードはどこにも保存されないため、アプリ自身を含め誰も復元できません。", "보안 질문이 없으면 비밀번호를 재설정할 수 없습니다. 이 비밀번호를 잊으면 Lucent로 돌아갈 유일한 방법은 전체 데이터 삭제뿐입니다. 이 기기의 모든 노트, 할 일, 첨부 파일, 대화가 영구적으로 삭제됩니다. 비밀번호는 어디에도 저장되지 않으므로 앱 자체를 포함해 누구도 복구해 줄 수 없습니다."),
    ("turnOnAnyway", "Turn on anyway", "仍然开启", "それでもオンにする", "그래도 켜기"),
    ("addAQuestion", "Add a question", "添加问题", "質問を追加", "질문 추가"),

    # =====================================================================================
    # Settings — Privacy page
    # =====================================================================================
    ("systemIntegrationTitle", "System integration", "系统集成", "システム連携", "시스템 연동"),
    ("systemIntegrationDesc", "Let Lucent appear in the Android share sheet so you can send text or files from other apps straight into a new note or task. Off by default. Turning it on makes Lucent visible to other apps as a share target.", "让 Lucent 出现在 Android 分享面板中，以便从其他应用直接将文字或文件发送为新的笔记或任务。默认关闭。开启后，Lucent 将作为分享目标对其他应用可见。", "Androidの共有シートにLucentを表示し、他のアプリからテキストやファイルを直接新しいメモやタスクとして送れるようにします。既定はオフです。オンにすると、Lucentは共有先として他のアプリから見えるようになります。", "Android 공유 시트에 Lucent가 표시되어 다른 앱의 텍스트나 파일을 바로 새 노트나 할 일로 보낼 수 있습니다. 기본은 꺼짐입니다. 켜면 Lucent가 공유 대상으로 다른 앱에 표시됩니다."),
    ("shareWarnTitle", "Make Lucent a share target?", "将 Lucent 设为分享目标？", "Lucentを共有先にしますか？", "Lucent를 공유 대상으로 만들까요?"),
    ("shareWarnBody", "This makes Lucent appear in other apps' share sheets so you can send text and files into it. It's the one place Lucent becomes visible to other apps. Anything you choose to share INTO Lucent is copied into your encrypted database like any other note or task; Lucent still sends nothing out on its own. You can turn this off again at any time, and it's off until you confirm.", "这会让 Lucent 出现在其他应用的分享面板中，以便向其发送文字和文件。这是 Lucent 对其他应用可见的唯一场合。你选择分享进 Lucent 的内容会像普通笔记或任务一样复制进加密数据库；Lucent 自身仍然不会向外发送任何内容。你可以随时再次关闭，且在确认之前保持关闭。", "他のアプリの共有シートにLucentが表示され、テキストやファイルを送り込めるようになります。Lucentが他のアプリから見えるのはこの場面だけです。Lucentへ共有した内容は、他のメモやタスクと同様に暗号化データベースへコピーされます。Lucent自身が外へ何かを送ることはありません。いつでも再びオフにでき、確認するまではオフのままです。", "다른 앱의 공유 시트에 Lucent가 표시되어 텍스트와 파일을 보낼 수 있게 됩니다. Lucent가 다른 앱에 보이는 것은 이 경우뿐입니다. Lucent로 공유한 내용은 다른 노트나 할 일처럼 암호화된 데이터베이스에 복사되며, Lucent 스스로 밖으로 내보내는 것은 없습니다. 언제든 다시 끌 수 있으며, 확인하기 전까지는 꺼져 있습니다."),
    ("systemIntegrationOnToast", "System integration is on.", "系统集成已开启。", "システム連携をオンにしました。", "시스템 연동이 켜졌습니다."),
    ("systemIntegrationOffToast", "System integration is off.", "系统集成已关闭。", "システム連携をオフにしました。", "시스템 연동이 꺼졌습니다."),
    ("startupLoggingTitle", "Diagnostic logging", "诊断日志", "診断ログ", "진단 로그"),
    ("startupLoggingDesc", "Record diagnostic events — including errors and the on-device model engine's own output — to a local file for troubleshooting. These logs stay on this device and are never sent anywhere; the only way they leave is if you export them yourself below.", "将诊断事件——包括错误和本地模型引擎自身的输出——记录到本地文件以便排查问题。这些日志只保存在此设备上，绝不会被发送到任何地方——除非你在下方自行导出。", "トラブルシューティング用に、エラーや端末内モデルエンジン自身の出力を含む診断イベントをローカルファイルへ記録します。ログはこの端末に留まり、どこへも送信されません。外に出るのは、下であなた自身がエクスポートしたときだけです。", "문제 해결을 위해 오류와 온디바이스 모델 엔진 자체 출력 등 진단 이벤트를 로컬 파일에 기록합니다. 이 로그는 이 기기에만 저장되며 어디로도 전송되지 않습니다. 아래에서 직접 내보낼 때만 밖으로 나갑니다."),
    ("loggingConsentTitle", "Enable diagnostic logging?", "开启诊断日志？", "診断ログを有効にしますか？", "진단 로그를 사용할까요?"),
    ("loggingConsentBody", "This records technical events to a file on this device only — including errors, the model engine's output, and details like model and device. It may include text you type to the assistant. Nothing is ever sent anywhere: you export it yourself, only if you choose to share it for troubleshooting. You can turn it off and clear it at any time.", "这会把技术事件仅记录到本机的一个文件里——包括错误、模型引擎的输出，以及模型和设备等信息。其中可能包含你输入给助手的文字。任何内容都不会被发送到任何地方：只有你自己选择导出，才能用于排查问题的分享。你可以随时关闭并清除。", "エラー、モデルエンジンの出力、モデルや端末などの情報を含む技術的なイベントを、この端末内のファイルにのみ記録します。アシスタントに入力した文字が含まれることがあります。どこにも送信されません。共有する場合のみ、ご自身でエクスポートします。いつでもオフにして消去できます。", "오류, 모델 엔진 출력, 모델·기기 정보 등 기술적 이벤트를 이 기기 내 파일에만 기록합니다. 어시스턴트에 입력한 텍스트가 포함될 수 있습니다. 어디로도 전송되지 않으며, 공유하려는 경우에만 직접 내보냅니다. 언제든 끄고 삭제할 수 있습니다."),
    ("loggingConsentConfirm", "Enable", "开启", "有効にする", "사용"),
    ("loggingEnabledEvent", "Logging enabled from Settings", "已在设置中开启日志", "設定でログを有効化", "설정에서 로그 사용 설정됨"),
    ("exportLogs", "Export logs", "导出日志", "ログをエクスポート", "로그 내보내기"),
    ("clearLogs", "Clear logs", "清除日志", "ログを消去", "로그 지우기"),
    ("logsClearedToast", "Logs cleared.", "日志已清除。", "ログを消去しました。", "로그를 지웠습니다."),
    ("logsExported", "Logs exported.", "日志已导出。", "ログをエクスポートしました。", "로그를 내보냈습니다."),
    ("logsExportFailed", "Couldn't export the logs.", "无法导出日志。", "ログをエクスポートできませんでした。", "로그를 내보내지 못했습니다."),

    # =====================================================================================
    # Settings — Data page: backup & restore
    # =====================================================================================
    ("backupRestoreTitle", "Backup & restore", "备份与恢复", "バックアップと復元", "백업 및 복원"),
    ("backupRestoreDesc", "One encrypted .lcb file holds everything: notes (archived ones included), tasks, note version history, chats, every attachment, and your settings. The whole file is encrypted, not just your API key. By default it's locked with Lucent's built-in key so it restores on any device with just the file; you can add your own password for stronger protection. Importing shows you what's inside before it changes anything. Only .lcb files exported by this app can be restored.", "一个加密的 .lcb 文件包含全部内容：笔记（含已归档）、任务、笔记版本历史、聊天、所有附件以及你的设置。整个文件都被加密，而不只是 API 密钥。默认使用 Lucent 内置密钥加锁，只要有文件就能在任何设备上恢复；你也可以设置自己的密码以获得更强保护。导入前会先显示文件内容，再做任何更改。只有本应用导出的 .lcb 文件才能恢复。", "暗号化された1つの.lcbファイルにすべてが入ります：メモ（アーカイブ済みを含む）、タスク、メモのバージョン履歴、チャット、すべての添付ファイル、そして設定。APIキーだけでなくファイル全体が暗号化されます。既定ではLucent内蔵キーでロックされ、ファイルさえあればどの端末でも復元できます。より強い保護のために独自のパスワードも設定できます。インポート時は、変更を加える前に中身を表示します。復元できるのは本アプリが書き出した.lcbファイルだけです。", "암호화된 .lcb 파일 하나에 모든 것이 담깁니다: 노트(보관된 것 포함), 할 일, 노트 버전 기록, 대화, 모든 첨부 파일, 그리고 설정. API 키만이 아니라 파일 전체가 암호화됩니다. 기본적으로 Lucent 내장 키로 잠기므로 파일만 있으면 어떤 기기에서든 복원됩니다. 더 강한 보호를 위해 직접 비밀번호를 추가할 수도 있습니다. 가져오기 전에 내용물을 먼저 보여준 뒤에 변경합니다. 이 앱이 내보낸 .lcb 파일만 복원할 수 있습니다."),
    ("exportBackup", "Export backup", "导出备份", "バックアップをエクスポート", "백업 내보내기"),
    ("importBackup", "Import backup", "导入备份", "バックアップをインポート", "백업 가져오기"),
    ("exportBackupTitle", "Export backup", "导出备份", "バックアップのエクスポート", "백업 내보내기"),
    ("exportBackupBody", "Everything goes into one encrypted .lcb file — notes, tasks, version history, chats, attachments, and settings. By default it's locked with Lucent's built-in key, which means it restores on ANY device with nothing but the file. Leave the password blank for that.", "所有内容都会打包进一个加密的 .lcb 文件——笔记、任务、版本历史、聊天、附件和设置。默认使用 Lucent 内置密钥加锁，这意味着只要有文件，就能在任何设备上恢复。若要如此，请将密码留空。", "すべてが1つの暗号化.lcbファイルにまとまります：メモ、タスク、バージョン履歴、チャット、添付ファイル、設定。既定ではLucent内蔵キーでロックされ、ファイルさえあればどの端末でも復元できます。その場合はパスワードを空欄のままにしてください。", "모든 것이 암호화된 .lcb 파일 하나로 들어갑니다 — 노트, 할 일, 버전 기록, 대화, 첨부 파일, 설정. 기본적으로 Lucent 내장 키로 잠기므로 파일만 있으면 어떤 기기에서도 복원됩니다. 그렇게 하려면 비밀번호를 비워 두세요."),
    ("addPasswordOptional", "Add a password (optional)", "添加密码（可选）", "パスワードを追加（任意）", "비밀번호 추가(선택)"),
    ("exportPasswordExplain", "A password gives stronger protection: the key is derived from it and exists nowhere else, so not even someone holding the app can open the file. The trade-off is that you must type the SAME password to restore it on another device — it isn't saved anywhere else, and a forgotten one can't be recovered. Leave this blank unless you want that.", "密码能提供更强的保护：密钥由它派生且不存在于任何其他地方，即使拿到应用的人也无法打开文件。代价是在其他设备上恢复时必须输入完全相同的密码——它不会保存在任何其他地方，忘记后也无法找回。除非你确实需要，否则请留空。", "パスワードを設定するとより強固に保護されます。鍵はパスワードから導出され、他のどこにも存在しないため、アプリを持っている人でもファイルを開けません。その代わり、別の端末で復元するにはまったく同じパスワードの入力が必要です。パスワードはどこにも保存されず、忘れると復元できません。必要な場合以外は空欄のままにしてください。", "비밀번호는 더 강한 보호를 제공합니다. 키가 비밀번호에서 파생되고 다른 어디에도 존재하지 않으므로, 앱을 가진 사람이라도 파일을 열 수 없습니다. 대신 다른 기기에서 복원하려면 똑같은 비밀번호를 입력해야 합니다. 어디에도 저장되지 않으며 잊으면 복구할 수 없습니다. 꼭 필요한 경우가 아니라면 비워 두세요."),
    ("fieldPasswordOptional", "Password (optional)", "密码（可选）", "パスワード（任意）", "비밀번호(선택)"),
    ("hidePassword", "Hide password", "隐藏密码", "パスワードを隠す", "비밀번호 숨기기"),
    ("showPassword", "Show password", "显示密码", "パスワードを表示", "비밀번호 표시"),
    ("backupSavedBuiltIn", "Encrypted backup saved, using Lucent's built-in key.", "已保存加密备份，使用 Lucent 内置密钥。", "暗号化バックアップを保存しました（Lucent内蔵キー使用）。", "암호화된 백업을 저장했습니다(Lucent 내장 키 사용)."),
    ("backupSavedPassword", "Encrypted backup saved, protected by your password. Don't lose it.", "已保存加密备份，由你的密码保护。请勿遗失。", "暗号化バックアップを保存しました。あなたのパスワードで保護されています。忘れないでください。", "암호화된 백업을 저장했습니다. 비밀번호로 보호됩니다. 잊지 마세요."),
    ("backupWriteFailed", "Couldn't write to that file.", "无法写入该文件。", "そのファイルに書き込めませんでした。", "해당 파일에 쓸 수 없습니다."),
    ("exportFailed(detail: String)", "Export failed: {detail}", "导出失败：{detail}", "エクスポートに失敗しました：{detail}", "내보내기 실패: {detail}"),
    ("importFailed(detail: String)", "Import failed: {detail}", "导入失败：{detail}", "インポートに失敗しました：{detail}", "가져오기 실패: {detail}"),
    ("couldNotReadThatFile", "Could not read that file.", "无法读取该文件。", "そのファイルを読み取れませんでした。", "해당 파일을 읽을 수 없습니다."),
    ("backupPasswordTitle", "Backup password", "备份密码", "バックアップのパスワード", "백업 비밀번호"),
    ("backupPasswordBody", "This backup was protected with a password when it was exported. Enter it to see what's inside.", "此备份在导出时设置了密码保护。输入密码即可查看其中内容。", "このバックアップはエクスポート時にパスワードで保護されました。中身を見るには入力してください。", "이 백업은 내보낼 때 비밀번호로 보호되었습니다. 내용을 보려면 입력하세요."),
    ("wrongPassword", "Wrong password", "密码错误", "パスワードが違います", "잘못된 비밀번호"),
    ("restoreBackupTitle", "Restore this backup?", "恢复此备份？", "このバックアップを復元しますか？", "이 백업을 복원할까요?"),
    "// Titles for the bottom sheet that announces the outcome the moment a restore finishes.",
    ("restoreDoneTitle", "Restore complete", "恢复完成", "復元が完了しました", "복원 완료"),
    ("restoreFailedTitle", "Restore failed", "恢复失败", "復元に失敗しました", "복원 실패"),
    ("exportingBackup", "Exporting backup…", "正在导出备份…", "バックアップをエクスポート中…", "백업 내보내는 중…"),
    ("importingBackup", "Importing backup…", "正在导入备份…", "バックアップをインポート中…", "백업 가져오는 중…"),
    ("backupBusyBody", "This can take a while for a large backup. Other actions are disabled until it finishes.", "较大的备份可能需要一些时间。完成前其它操作已被禁用。", "大きなバックアップには時間がかかることがあります。完了するまで他の操作は無効になります。", "백업이 크면 시간이 걸릴 수 있습니다. 완료될 때까지 다른 작업은 비활성화됩니다."),
    ("exportCancelledStatus", "Export cancelled.", "已取消导出。", "エクスポートをキャンセルしました。", "내보내기를 취소했습니다."),
    ("importCancelledStatus", "Import cancelled.", "已取消导入。", "インポートをキャンセルしました。", "가져오기를 취소했습니다."),
    ("exportedWhen(at: String)", "Exported {at}", "导出于 {at}", "エクスポート {at}", "내보냄 {at}"),
    ("protectedByPassword", "Protected with your password.", "由你的密码保护。", "あなたのパスワードで保護されています。", "비밀번호로 보호됩니다."),
    ("protectedBuiltIn", "Encrypted with Lucent's built-in key.", "使用 Lucent 内置密钥加密。", "Lucent内蔵キーで暗号化されています。", "Lucent 내장 키로 암호화되어 있습니다."),
    ("backupEmpty", "This backup appears to be empty.", "此备份似乎是空的。", "このバックアップは空のようです。", "이 백업은 비어 있는 것 같습니다."),
    ("backupContains", "It contains:", "其中包含：", "内容：", "포함된 항목:"),
    ("bkNotes", "Notes", "笔记", "メモ", "노트"),
    ("bkTasks", "Tasks", "任务", "タスク", "할 일"),
    ("bkNoteVersions", "Note versions", "笔记版本", "メモのバージョン", "노트 버전"),
    ("bkConversations", "Conversations", "会话", "会話", "대화"),
    ("bkChatMessages", "Chat messages", "聊天消息", "チャットメッセージ", "채팅 메시지"),
    ("bkAttachments", "Attachments", "附件", "添付ファイル", "첨부 파일"),
    ("bkSettings", "Settings", "设置", "設定", "설정"),
    ("bkIncludingApiKeys", "including your API keys", "包含你的 API 密钥", "APIキーを含む", "API 키 포함"),
    ("bkNArchived(count: Int)", "{count} archived", "{count} 项已归档", "{count}件アーカイブ済み", "{count}개 보관됨"),
    ("bkNInTrash(count: Int)", "{count} in trash", "{count} 项在回收站", "{count}件ゴミ箱", "{count}개 휴지통"),
    ("bkNCompleted(count: Int)", "{count} completed", "{count} 项已完成", "{count}件完了", "{count}개 완료됨"),
    ("restoreMergeNote", "Restoring adds these to what you already have — nothing currently on this device is deleted. Anything identical is skipped rather than duplicated.", "恢复会将这些内容添加到你现有的数据中——此设备上当前的内容不会被删除。完全相同的条目会被跳过而不是重复导入。", "復元すると、これらは今ある内容に追加されます。この端末の既存データは一切削除されません。完全に同一の項目は重複せずスキップされます。", "복원하면 이 항목들이 기존 데이터에 추가됩니다. 이 기기의 현재 내용은 삭제되지 않습니다. 완전히 동일한 항목은 중복되지 않고 건너뜁니다."),

    # =====================================================================================
    # Settings — Data page: selective export + danger zone
    # =====================================================================================
    ("exportNotesTasksTitle", "Export notes & tasks", "导出笔记与任务", "メモとタスクのエクスポート", "노트 및 할 일 내보내기"),
    ("exportNotesTasksDesc", "Write your notes or tasks to a single file you can keep or open anywhere — choose Markdown, Word, PDF, or Excel on the next screen. Pick exactly which items to include (with a search box and Select-All). These files are NOT encrypted: that is the entire point of them. Once you tick items, you can also tick their individual attachments to bundle the actual files alongside — the export is then saved as a .zip. A doodle note's canvases are offered in that same list and each ticked canvas is written out as its own PDF.", "将你的笔记或任务写入一个可随处保存和打开的文件——在下一屏选择 Markdown、Word、PDF 或 Excel。可精确挑选要包含的条目（带搜索框和全选）。这些文件不加密：这正是它们的用途所在。勾选条目后，还可以逐个勾选它的附件，把附件文件本身一并打包——此时导出会保存为 .zip 压缩包。涂鸦笔记的每块画布也会出现在同一份列表里，勾选后会各自导出为一份 PDF。", "メモやタスクを、どこでも保存・閲覧できる1つのファイルに書き出します。次の画面でMarkdown・Word・PDF・Excelを選べます。含める項目は検索ボックスと全選択で正確に選べます。これらのファイルは暗号化されません。それこそが目的だからです。項目を選ぶと、その添付ファイルを個別に選んで実ファイルも一緒に同梱できます。その場合、エクスポートは .zip として保存されます。落書きメモの各キャンバスも同じ一覧に並び、選んだキャンバスはそれぞれ個別の PDF として書き出されます。", "노트나 할 일을 어디서든 보관하고 열 수 있는 파일 하나로 내보냅니다. 다음 화면에서 마크다운, Word, PDF, Excel을 선택하세요. 포함할 항목은 검색창과 전체 선택으로 정확히 고를 수 있습니다. 이 파일들은 암호화되지 않습니다. 그것이 바로 이 기능의 목적입니다. 항목을 선택하면 해당 첨부 파일을 개별적으로 선택해 실제 파일까지 함께 묶을 수 있으며, 이 경우 내보내기는 .zip으로 저장됩니다. 낙서 노트의 캔버스도 같은 목록에 표시되며, 선택한 캔버스는 각각 별도의 PDF로 저장됩니다."),
    ("exportAttachmentsHint", "Tick a file to include it; tap its name to preview.", "勾选文件以包含它；点击名称可预览。", "同梱するファイルを選択。名前をタップでプレビュー。", "포함할 파일을 선택하세요. 이름을 누르면 미리보기."),
    # Round R1 - per-item select-all (task 4) and doodle-as-attachment (task 3).
    ("exportSelectAllHere", "Select all files in this one", "全选本条的附件", "この項目のファイルをすべて選択", "이 항목의 파일 모두 선택"),
    ("exportDoodleCanvas(index: Int)", "Doodle canvas {index}", "涂鸦画布 {index}", "落書きキャンバス {index}", "낙서 캔버스 {index}"),
    ("exportDoodleHint", "A doodle canvas counts as an attachment: tick it to export that canvas as its own PDF.", "涂鸦画布与附件同级：勾选后会把该画布单独导出为 PDF。", "落書きキャンバスは添付ファイルと同等です。選択するとそのキャンバスを個別の PDF として書き出します。", "낙서 캔버스는 첨부 파일과 동등합니다. 선택하면 해당 캔버스를 별도의 PDF로 내보냅니다."),
    ("chooseTasksToExport", "Choose tasks to export", "选择要导出的任务", "エクスポートするタスクを選択", "내보낼 할 일 선택"),
    ("chooseNotesToExport", "Choose notes to export", "选择要导出的笔记", "エクスポートするメモを選択", "내보낼 노트 선택"),
    ("exportNotesScreenTitle", "Export notes", "导出笔记", "メモをエクスポート", "노트 내보내기"),
    ("exportTasksScreenTitle", "Export tasks", "导出任务", "タスクをエクスポート", "할 일 내보내기"),
    ("exportedSelected", "Exported selected items.", "已导出所选条目。", "選択した項目をエクスポートしました。", "선택한 항목을 내보냈습니다."),
    ("doneSuffix", " · done", " · 已完成", " · 完了", " · 완료"),
    ("dangerZone", "Danger zone", "危险操作", "危険な操作", "위험 구역"),
    ("clearNotesBtn", "Clear notes", "清除笔记", "メモを消去", "노트 삭제"),
    ("clearTasksBtn", "Clear tasks", "清除任务", "タスクを消去", "할 일 삭제"),
    ("clearChatsBtn", "Clear chat history", "清除聊天记录", "チャット履歴を消去", "채팅 기록 삭제"),
    ("clearAllDataBtn", "Clear all data", "清除所有数据", "全データを消去", "전체 데이터 삭제"),
    ("clearAllDataTitle", "Clear all data?", "清除所有数据？", "全データを消去しますか？", "전체 데이터를 삭제할까요?"),
    ("clearAllDataBody", "This permanently deletes every note, task, and chat message, and resets all settings (including your API key) to their defaults. This can't be undone.", "这将永久删除所有笔记、任务和聊天消息，并将所有设置（包括你的 API 密钥）恢复为默认值。此操作无法撤销。", "すべてのメモ・タスク・チャットメッセージが完全に削除され、すべての設定（APIキーを含む）が既定値に戻ります。元に戻せません。", "모든 노트, 할 일, 채팅 메시지가 영구 삭제되고 모든 설정(API 키 포함)이 기본값으로 초기화됩니다. 되돌릴 수 없습니다."),
    ("deleteEverything", "Delete everything", "全部删除", "すべて削除", "모두 삭제"),
    ("allDataClearedToast", "All data cleared", "所有数据已清除", "全データを消去しました", "전체 데이터가 삭제되었습니다"),
    ("clearNotesTitle", "Clear all notes?", "清除所有笔记？", "すべてのメモを消去しますか？", "모든 노트를 삭제할까요?"),
    ("clearNotesBody", "This permanently deletes every note and its attachments. Your tasks and chats are kept. This can't be undone.", "这将永久删除所有笔记及其附件。任务和聊天会被保留。此操作无法撤销。", "すべてのメモとその添付ファイルが完全に削除されます。タスクとチャットは残ります。元に戻せません。", "모든 노트와 첨부 파일이 영구 삭제됩니다. 할 일과 대화는 유지됩니다. 되돌릴 수 없습니다."),
    ("deleteNotesBtn", "Delete notes", "删除笔记", "メモを削除", "노트 삭제"),
    ("notesClearedToast", "Notes cleared", "笔记已清除", "メモを消去しました", "노트가 삭제되었습니다"),
    ("clearTasksTitle", "Clear all tasks?", "清除所有任务？", "すべてのタスクを消去しますか？", "모든 할 일을 삭제할까요?"),
    ("clearTasksBody", "This permanently deletes every task (active and completed) and its attachments. Your notes and chats are kept. This can't be undone.", "这将永久删除所有任务（进行中和已完成）及其附件。笔记和聊天会被保留。此操作无法撤销。", "すべてのタスク（進行中・完了済み）とその添付ファイルが完全に削除されます。メモとチャットは残ります。元に戻せません。", "모든 할 일(진행 중 및 완료)과 첨부 파일이 영구 삭제됩니다. 노트와 대화는 유지됩니다. 되돌릴 수 없습니다."),
    ("deleteTasksBtn", "Delete tasks", "删除任务", "タスクを削除", "할 일 삭제"),
    ("tasksClearedToast", "Tasks cleared", "任务已清除", "タスクを消去しました", "할 일이 삭제되었습니다"),
    ("clearChatsTitle", "Clear all chat history?", "清除所有聊天记录？", "すべてのチャット履歴を消去しますか？", "모든 채팅 기록을 삭제할까요?"),
    ("clearChatsBody", "This permanently deletes every assistant conversation and message. Your notes and tasks are kept. This can't be undone.", "这将永久删除所有助手会话及消息。笔记和任务会被保留。此操作无法撤销。", "アシスタントの会話とメッセージがすべて完全に削除されます。メモとタスクは残ります。元に戻せません。", "어시스턴트의 모든 대화와 메시지가 영구 삭제됩니다. 노트와 할 일은 유지됩니다. 되돌릴 수 없습니다."),
    ("deleteChatsBtn", "Delete chat history", "删除聊天记录", "チャット履歴を削除", "채팅 기록 삭제"),
    ("chatsClearedToast", "Chat history cleared", "聊天记录已清除", "チャット履歴を消去しました", "채팅 기록이 삭제되었습니다"),
    ("lockedNoticeTitle", "Your notes couldn't be unlocked", "无法解锁你的笔记", "メモのロックを解除できませんでした", "노트의 잠금을 해제할 수 없습니다"),
    ("lockedNoticeBody(fileName: String)", "Your notes database could not be decrypted on this launch, so it was set aside as \"{fileName}\" and a new empty one was created. Nothing has been deleted. Import your most recent backup to restore your notes and tasks.", "本次启动时无法解密你的笔记数据库，因此已将其另存为“{fileName}”并创建了一个新的空数据库。没有任何内容被删除。请导入最近的备份以恢复你的笔记和任务。", "今回の起動でメモのデータベースを復号できなかったため、「{fileName}」として退避し、新しい空のデータベースを作成しました。何も削除されていません。最新のバックアップをインポートして、メモとタスクを復元してください。", "이번 실행에서 노트 데이터베이스를 해독할 수 없어 \"{fileName}\"(으)로 따로 보관하고 새 빈 데이터베이스를 만들었습니다. 삭제된 것은 없습니다. 가장 최근 백업을 가져와 노트와 할 일을 복원하세요."),

    # =====================================================================================
    # Screens — leftover call sites (tasks / notes / trash / archive / history / lock)
    # =====================================================================================
    ("editTask", "Edit task", "编辑任务", "タスクを編集", "할 일 편집"),
    ("editNote", "Edit note", "编辑笔记", "メモを編集", "노트 편집"),
    ("addTaskBtn", "Add task", "添加任务", "タスクを追加", "할 일 추가"),
    ("addNoteBtn", "Add note", "添加笔记", "メモを追加", "노트 추가"),
    ("saveChanges", "Save changes", "保存更改", "変更を保存", "변경 사항 저장"),
    ("addSubtask", "Add subtask", "添加子任务", "サブタスクを追加", "하위 작업 추가"),
    ("addItem", "Add item", "添加项目", "項目を追加", "항목 추가"),
    ("detailsPlaceholder", "Details", "详情", "詳細", "세부 정보"),
    ("detailsMarkdown", "Details — Markdown supported", "详情——支持 Markdown", "詳細 — Markdown対応", "세부 정보 — 마크다운 지원"),
    ("detailsLinks", "Details — [[links]] supported", "详情——支持 [[链接]]", "詳細 — [[リンク]]対応", "세부 정보 — [[링크]] 지원"),
    ("detailsMarkdownLinks", "Details — Markdown and [[links]] supported", "详情——支持 Markdown 与 [[链接]]", "詳細 — Markdownと[[リンク]]対応", "세부 정보 — 마크다운 및 [[링크]] 지원"),
    ("shareTaskChooser", "Share task", "分享任务", "タスクを共有", "할 일 공유"),
    ("shareNoteChooser", "Share note", "分享笔记", "メモを共有", "노트 공유"),
    ("clearAllSelection", "Clear all", "取消全选", "すべて解除", "전체 해제"),
    ("a11ySelected", "Selected", "已选中", "選択済み", "선택됨"),
    ("a11yNotSelected", "Not selected", "未选中", "未選択", "선택 안 됨"),
    ("a11ySearchTasks", "Search tasks", "搜索任务", "タスクを検索", "할 일 검색"),
    ("a11ySearchNotes", "Search notes", "搜索笔记", "メモを検索", "노트 검색"),
    ("markDone", "Mark as done", "标记为完成", "完了にする", "완료로 표시"),
    ("dueWhen(at: String)", "Due {at}", "截止 {at}", "期限 {at}", "마감 {at}"),
    ("setADueDate", "Set a due date", "设置截止日期", "期限を設定", "마감일 설정"),
    ("emptyTasksHint", "No tasks yet. Tap + to add one, or ask the assistant.", "还没有任务。点按 + 添加，或让助手代劳。", "タスクはまだありません。＋をタップして追加するか、アシスタントに頼んでください。", "아직 할 일이 없습니다. +를 탭해 추가하거나 어시스턴트에게 부탁하세요."),
    ("noTasksMatchSearch", "No tasks match that search.", "没有任务符合该搜索。", "その検索に一致するタスクはありません。", "검색과 일치하는 할 일이 없습니다."),
    ("emptyNotesHint", "No notes yet. Tap + to write one, or ask the assistant.", "还没有笔记。点按 + 撰写，或让助手代劳。", "メモはまだありません。＋をタップして書くか、アシスタントに頼んでください。", "아직 노트가 없습니다. +를 탭해 작성하거나 어시스턴트에게 부탁하세요."),
    ("noNotesMatchSearch", "No notes match that search.", "没有笔记符合该搜索。", "その検索に一致するメモはありません。", "검색과 일치하는 노트가 없습니다."),
    ("notifPermissionRationale", "Reminders need notification permission to alert you. You can grant it in system settings.", "提醒需要通知权限才能提示你。你可以在系统设置中授予该权限。", "リマインダーで通知するには通知の権限が必要です。システム設定で許可できます。", "알림으로 알려드리려면 알림 권한이 필요합니다. 시스템 설정에서 허용할 수 있습니다."),
    ("couldNotReadOneFile", "Couldn't read one of the files.", "无法读取其中一个文件。", "ファイルの1つを読み取れませんでした。", "파일 중 하나를 읽을 수 없습니다."),
    ("unsavedNoteExistingBody", "You have unsaved changes to this note. Save them before leaving?", "此笔记有尚未保存的更改。要在离开前保存吗？", "このメモに未保存の変更があります。移動する前に保存しますか？", "이 노트에 저장되지 않은 변경 사항이 있습니다. 나가기 전에 저장할까요?"),
    ("unsavedNoteNewBody", "This note hasn't been saved yet. Save it before leaving?", "此笔记尚未保存。要在离开前保存吗？", "このメモはまだ保存されていません。移動する前に保存しますか？", "이 노트는 아직 저장되지 않았습니다. 나가기 전에 저장할까요?"),
    ("unsavedTaskExistingBody", "You have unsaved changes to this task. Save them before leaving?", "此任务有尚未保存的更改。要在离开前保存吗？", "このタスクに未保存の変更があります。移動する前に保存しますか？", "이 할 일에 저장되지 않은 변경 사항이 있습니다. 나가기 전에 저장할까요?"),
    ("unsavedTaskNewBody", "This task hasn't been saved yet. Save it before leaving?", "此任务尚未保存。要在离开前保存吗？", "このタスクはまだ保存されていません。移動する前に保存しますか？", "이 할 일은 아직 저장되지 않았습니다. 나가기 전에 저장할까요?"),
    ("linksToHeader", "Links to", "链接到", "リンク先", "연결 대상"),
    ("linkedFromHeader", "Linked from", "被链接自", "リンク元", "링크됨"),
    ("trashEmpty", "Trash is empty.", "回收站是空的。", "ゴミ箱は空です。", "휴지통이 비어 있습니다."),
    ("noTrashedNotesMatch", "No trashed notes match that search.", "回收站中没有笔记符合该搜索。", "その検索に一致するゴミ箱内のメモはありません。", "검색과 일치하는 휴지통 노트가 없습니다."),
    ("noTrashedTasksMatch", "No trashed tasks match that search.", "回收站中没有任务符合该搜索。", "その検索に一致するゴミ箱内のタスクはありません。", "검색과 일치하는 휴지통 할 일이 없습니다."),
    ("trashedOn(at: String)", "Trashed {at}", "移入回收站于 {at}", "ゴミ箱へ移動 {at}", "휴지통으로 이동 {at}"),
    ("a11yMarkActive", "Mark as active", "标记为进行中", "進行中に戻す", "진행 중으로 표시"),
    ("a11yRestoreToNotes", "Restore to notes", "恢复到笔记", "メモに復元", "노트로 복원"),

    # =====================================================================================
    # Assistant — action confirmation sheet (describeToolCall)
    # =====================================================================================
    ("ccCreateNote(title: String)", "Create a note titled \"{title}\"", "创建标题为“{title}”的笔记", "「{title}」というメモを作成", "\"{title}\" 제목의 노트 만들기"),
    ("ccEditNote(title: String)", "Edit the note \"{title}\"", "编辑笔记“{title}”", "メモ「{title}」を編集", "노트 \"{title}\" 편집"),
    ("ccDeleteNote(title: String)", "Move the note \"{title}\" to Trash", "将笔记“{title}”移入回收站", "メモ「{title}」をゴミ箱へ移動", "노트 \"{title}\"을(를) 휴지통으로 이동"),
    ("ccPinNote(title: String)", "Pin the note \"{title}\"", "置顶笔记“{title}”", "メモ「{title}」を固定", "노트 \"{title}\" 고정"),
    ("ccUnpinNote(title: String)", "Unpin the note \"{title}\"", "取消置顶笔记“{title}”", "メモ「{title}」の固定を解除", "노트 \"{title}\" 고정 해제"),
    ("ccSaveFileOnNote(file: String, title: String)", "Save the file \"{file}\" onto the note \"{title}\"", "将文件“{file}”保存到笔记“{title}”", "ファイル「{file}」をメモ「{title}」に保存", "파일 \"{file}\"을(를) 노트 \"{title}\"에 저장"),
    ("ccRemoveFileFromNote(file: String, title: String)", "Remove the file \"{file}\" from the note \"{title}\"", "从笔记“{title}”移除文件“{file}”", "メモ「{title}」からファイル「{file}」を削除", "노트 \"{title}\"에서 파일 \"{file}\" 제거"),
    ("ccAttachUploadToNote(title: String)", "Attach your uploaded file to the note \"{title}\"", "将你上传的文件附加到笔记“{title}”", "アップロードしたファイルをメモ「{title}」に添付", "업로드한 파일을 노트 \"{title}\"에 첨부"),
    ("ccCreateTask(title: String)", "Create a task titled \"{title}\"", "创建标题为“{title}”的任务", "「{title}」というタスクを作成", "\"{title}\" 제목의 할 일 만들기"),
    ("ccCompleteTask(title: String)", "Mark the task \"{title}\" as done", "将任务“{title}”标记为完成", "タスク「{title}」を完了にする", "할 일 \"{title}\"을(를) 완료로 표시"),
    ("ccEditTask(title: String)", "Edit the task \"{title}\"", "编辑任务“{title}”", "タスク「{title}」を編集", "할 일 \"{title}\" 편집"),
    ("ccDeleteTask(title: String)", "Move the task \"{title}\" to Trash", "将任务“{title}”移入回收站", "タスク「{title}」をゴミ箱へ移動", "할 일 \"{title}\"을(를) 휴지통으로 이동"),
    ("ccPinTask(title: String)", "Pin the task \"{title}\"", "置顶任务“{title}”", "タスク「{title}」を固定", "할 일 \"{title}\" 고정"),
    ("ccUnpinTask(title: String)", "Unpin the task \"{title}\"", "取消置顶任务“{title}”", "タスク「{title}」の固定を解除", "할 일 \"{title}\" 고정 해제"),
    ("ccSetPriority(title: String, priority: String)", "Set the priority of \"{title}\" to {priority}", "将“{title}”的优先级设为 {priority}", "「{title}」の優先度を{priority}に設定", "\"{title}\"의 우선순위를 {priority}(으)로 설정"),
    ("ccSetDueDate(title: String, due: String)", "Set the due date of \"{title}\" to {due}", "将“{title}”的截止日期设为 {due}", "「{title}」の期限を{due}に設定", "\"{title}\"의 마감일을 {due}(으)로 설정"),
    ("ccAddSubtask(item: String, title: String)", "Add the subtask \"{item}\" to \"{title}\"", "向“{title}”添加子任务“{item}”", "「{title}」にサブタスク「{item}」を追加", "\"{title}\"에 하위 작업 \"{item}\" 추가"),
    ("ccCheckSubtask(item: String, title: String)", "Check off the subtask \"{item}\" on \"{title}\"", "勾选“{title}”的子任务“{item}”", "「{title}」のサブタスク「{item}」にチェック", "\"{title}\"의 하위 작업 \"{item}\" 체크"),
    ("ccRemoveSubtask(item: String, title: String)", "Remove the subtask \"{item}\" from \"{title}\"", "从“{title}”移除子任务“{item}”", "「{title}」からサブタスク「{item}」を削除", "\"{title}\"에서 하위 작업 \"{item}\" 제거"),
    ("ccEditSubtask(item: String, title: String)", "Reword the subtask \"{item}\" on \"{title}\"", "修改“{title}”的子任务“{item}”的文本", "「{title}」のサブタスク「{item}」の文言を変更", "\"{title}\"의 하위 작업 \"{item}\" 문구 변경"),
    ("ccReopenTask(title: String)", "Mark the task \"{title}\" as not done", "将任务“{title}”标记为未完成", "タスク「{title}」を未完了に戻す", "작업 \"{title}\"을(를) 미완료로 되돌리기"),
    ("widgetTaskDoneToast(title: String)", "\"{title}\" marked as done.", "已将“{title}”标记为完成。", "「{title}」を完了にしました。", "\"{title}\"을(를) 완료로 표시했습니다."),
    ("widgetTaskReopenedToast(title: String)", "\"{title}\" reopened.", "已重新打开“{title}”。", "「{title}」を未完了に戻しました。", "\"{title}\"을(를) 다시 열었습니다."),
    ("widgetTaskGone", "That task no longer exists.", "该任务已不存在。", "そのタスクはもう存在しません。", "해당 작업이 더 이상 존재하지 않습니다."),
    ("ccArchiveNote(title: String)", "Archive the note \"{title}\"", "归档笔记“{title}”", "メモ「{title}」をアーカイブ", "노트 \"{title}\" 보관"),
    ("ccUnarchiveNote(title: String)", "Unarchive the note \"{title}\"", "取消归档笔记“{title}”", "メモ「{title}」のアーカイブを解除", "노트 \"{title}\" 보관 해제"),
    ("ccSetNoteColor(title: String, color: String)", "Set the colour of \"{title}\" to {color}", "将“{title}”的颜色设为 {color}", "「{title}」の色を {color} に設定", "\"{title}\"의 색상을 {color}(으)로 설정"),
    ("ccAddNoteItem(item: String, title: String)", "Add the item \"{item}\" to the note \"{title}\"", "向笔记“{title}”添加清单项“{item}”", "メモ「{title}」に項目「{item}」を追加", "노트 \"{title}\"에 항목 \"{item}\" 추가"),
    ("ccCheckNoteItem(item: String, title: String)", "Check off the item \"{item}\" on the note \"{title}\"", "勾选笔记“{title}”的清单项“{item}”", "メモ「{title}」の項目「{item}」にチェック", "노트 \"{title}\"의 항목 \"{item}\" 체크"),
    ("ccEditNoteItem(item: String, title: String)", "Reword the item \"{item}\" on the note \"{title}\"", "修改笔记“{title}”的清单项“{item}”的文本", "メモ「{title}」の項目「{item}」の文言を変更", "노트 \"{title}\"의 항목 \"{item}\" 문구 변경"),
    ("ccRemoveNoteItem(item: String, title: String)", "Remove the item \"{item}\" from the note \"{title}\"", "从笔记“{title}”移除清单项“{item}”", "メモ「{title}」から項目「{item}」を削除", "노트 \"{title}\"에서 항목 \"{item}\" 제거"),
    ("ccSaveFileOnTask(file: String, title: String)", "Save the file \"{file}\" onto the task \"{title}\"", "将文件“{file}”保存到任务“{title}”", "ファイル「{file}」をタスク「{title}」に保存", "파일 \"{file}\"을(를) 할 일 \"{title}\"에 저장"),
    ("ccRemoveFileFromTask(file: String, title: String)", "Remove the file \"{file}\" from the task \"{title}\"", "从任务“{title}”移除文件“{file}”", "タスク「{title}」からファイル「{file}」を削除", "할 일 \"{title}\"에서 파일 \"{file}\" 제거"),
    ("ccAttachUploadToTask(title: String)", "Attach your uploaded file to the task \"{title}\"", "将你上传的文件附加到任务“{title}”", "アップロードしたファイルをタスク「{title}」に添付", "업로드한 파일을 할 일 \"{title}\"에 첨부"),
    ("ccNoteToChecklist(title: String)", "Switch the note \"{title}\" to checklist mode", "将笔记“{title}”切换为清单模式", "メモ「{title}」をチェックリスト表示に切り替え", "노트 \"{title}\"을(를) 체크리스트 모드로 전환"),
    ("ccNoteToText(title: String)", "Switch the note \"{title}\" back to plain text", "将笔记“{title}”切换回普通文本", "メモ「{title}」を通常のテキストに戻す", "노트 \"{title}\"을(를) 일반 텍스트로 되돌리기"),
    ("ccRestoreNoteVersion(title: String, version: String)", "Restore the note \"{title}\" to saved version {version}", "将笔记“{title}”恢复到已保存的版本 {version}", "メモ「{title}」を保存済みバージョン{version}に復元", "노트 \"{title}\"을(를) 저장된 버전 {version}(으)로 복원"),
    ("ccRestoreNoteFromTrash(title: String)", "Restore the note \"{title}\" from the Trash", "从回收站恢复笔记“{title}”", "ゴミ箱からメモ「{title}」を復元", "휴지통에서 노트 \"{title}\" 복원"),
    ("ccRestoreTaskFromTrash(title: String)", "Restore the task \"{title}\" from the Trash", "从回收站恢复任务“{title}”", "ゴミ箱からタスク「{title}」を復元", "휴지통에서 할 일 \"{title}\" 복원"),
    ("ccRunGeneric(name: String)", "Run \"{name}\"", "执行“{name}”", "「{name}」を実行", "\"{name}\" 실행"),
    ("ccRenameSuffix(newTitle: String)", " (rename to \"{newTitle}\")", "（重命名为“{newTitle}”）", "（「{newTitle}」に名称変更）", "(\"{newTitle}\"(으)로 이름 변경)"),
    ("ccDueSuffix(due: String)", " due {due}", "，截止 {due}", "（期限 {due}）", " (마감 {due})"),

    # =====================================================================================
    # Note templates (composer chips + generated scaffold text)
    # =====================================================================================
    ("tplJournal", "Journal entry", "日记", "日記", "일기"),
    ("tplMeeting", "Meeting notes", "会议记录", "会議メモ", "회의록"),
    ("tplIdea", "Project idea", "项目想法", "プロジェクト案", "프로젝트 아이디어"),
    ("tplChecklist", "Checklist", "清单", "チェックリスト", "체크리스트"),
    ("tplLongDatePattern", "EEEE, d MMMM yyyy", "yyyy年M月d日 EEEE", "yyyy年M月d日 EEEE", "yyyy년 M월 d일 EEEE"),
    ("tplShortDatePattern", "d MMM yyyy", "yyyy年M月d日", "yyyy年M月d日", "yyyy년 M월 d일"),
    ("tplMeetingTitle(date: String)", "Meeting — {date}", "会议 — {date}", "会議 — {date}", "회의 — {date}"),
    ("tplJournalBody", "## How today went\n\n\n## What I'm grateful for\n\n\n## Tomorrow\n", "## 今天过得如何\n\n\n## 我要感恩的事\n\n\n## 明天\n", "## 今日はどうだったか\n\n\n## 感謝していること\n\n\n## 明日\n", "## 오늘 하루는 어땠나\n\n\n## 감사한 일\n\n\n## 내일\n"),
    ("tplMeetingBody(date: String)", "**Attendees:** \n**Date:** {date}\n\n## Discussion\n- \n\n## Decisions\n- \n\n## Action items\n- \n", "**参会人：** \n**日期：** {date}\n\n## 讨论\n- \n\n## 决定\n- \n\n## 行动项\n- \n", "**参加者：** \n**日付：** {date}\n\n## 議論\n- \n\n## 決定事項\n- \n\n## アクション\n- \n", "**참석자:** \n**날짜:** {date}\n\n## 논의\n- \n\n## 결정 사항\n- \n\n## 실행 항목\n- \n"),
    ("tplIdeaBody", "## The idea\n\n\n## Why it's worth doing\n\n\n## First step\n\n\n## Open questions\n- \n", "## 想法\n\n\n## 为什么值得做\n\n\n## 第一步\n\n\n## 待解决的问题\n- \n", "## アイデア\n\n\n## なぜやる価値があるか\n\n\n## 最初の一歩\n\n\n## 未解決の疑問\n- \n", "## 아이디어\n\n\n## 할 만한 이유\n\n\n## 첫걸음\n\n\n## 남은 질문\n- \n"),

    # =====================================================================================
    # Default tag suggestions (editor chips)
    # =====================================================================================
    ("tagStudy", "Study", "学习", "勉強", "공부"),
    ("tagWork", "Work", "工作", "仕事", "업무"),
    ("tagGame", "Game", "游戏", "ゲーム", "게임"),
    ("tagSports", "Sports", "运动", "スポーツ", "운동"),
    ("tagOther", "Other", "其他", "その他", "기타"),

    # =====================================================================================
    # Data-layer user-visible messages
    # =====================================================================================
    ("notLcbBackup", "That isn't a Lucent .lcb backup. Only .lcb files exported by this app can be restored.", "这不是 Lucent 的 .lcb 备份。只有本应用导出的 .lcb 文件才能恢复。", "これはLucentの.lcbバックアップではありません。復元できるのは本アプリが書き出した.lcbファイルだけです。", "이 파일은 Lucent의 .lcb 백업이 아닙니다. 이 앱이 내보낸 .lcb 파일만 복원할 수 있습니다."),
    ("importedConversationTitle", "Imported conversation", "导入的会话", "インポートした会話", "가져온 대화"),
    ("importSummary(notes: Int, tasks: Int, chats: Int)", "Imported {notes} notes, {tasks} tasks, {chats} chat messages.", "已导入 {notes} 条笔记、{tasks} 个任务、{chats} 条聊天消息。", "メモ{notes}件、タスク{tasks}件、チャットメッセージ{chats}件をインポートしました。", "노트 {notes}개, 할 일 {tasks}개, 채팅 메시지 {chats}개를 가져왔습니다."),
    ("importSettingsRestored", " Settings restored.", " 设置已恢复。", " 設定を復元しました。", " 설정이 복원되었습니다."),
    ("importVersionsRestored(count: Int)", " ({count} note versions restored.)", "（已恢复 {count} 个笔记版本。）", "（メモのバージョン{count}件を復元。）", " (노트 버전 {count}개 복원됨.)"),
    ("importDuplicatesSkipped(count: Int)", " ({count} duplicate entries skipped.)", "（已跳过 {count} 条重复条目。）", "（重複{count}件をスキップ。）", " (중복 항목 {count}개 건너뜀.)"),
    ("attachmentTooLarge(size: String, limit: String)", "That file is {size}, over the {limit} limit for a single attachment. It wasn't added.", "该文件为 {size}，超过了单个附件 {limit} 的上限，未被添加。", "そのファイルは{size}で、添付1件あたりの上限{limit}を超えています。追加されませんでした。", "해당 파일은 {size}(으)로 첨부 파일당 {limit} 제한을 초과하여 추가되지 않았습니다."),

    # =====================================================================================
    # Round: local-assistant gating, floating capsule, backup coverage, localized search
    # =====================================================================================
    "// TODO(local-multimodal): this note describes a TEMPORARY gap, not a permanent design decision.",
    "// The on-device engine currently loads text-only GGUF models, so images, PDFs and audio are not",
    "// passed to it. Multimodal on-device support (an mmproj/vision projector alongside the model,",
    "// and an attachment path into LocalLlm.generate) is planned for a future release. When it lands,",
    "// rewrite this string in all four languages and remove the \"for now\" framing — leaving a",
    "// temporary limitation described as permanent is how a shipped feature stays hidden.",
    ("lmTextOnlyNote", "Text only, for now: the local assistant reads and writes text, and cannot yet see images, PDFs, audio or other attachments. This is a current limitation of on-device mode rather than a permanent one — multimodal support for local models is planned for a future version. Until then, attach files to the cloud assistant instead.", "目前仅支持纯文本：本地助手只能读写文字，暂时无法识别图片、PDF、音频或其他附件。这是本地模式现阶段的限制，并非永久如此——本地模型的多模态支持已列入后续版本计划。在此之前，需要发送附件请改用云端助手。", "現在はテキスト専用：ローカルアシスタントは文字の読み書きのみ行え、画像・PDF・音声などの添付はまだ認識できません。これはローカルモードの現時点での制限であり、恒久的なものではありません。ローカルモデルのマルチモーダル対応は今後のバージョンで予定しています。それまでは添付が必要な場合クラウドアシスタントをご利用ください。", "현재는 텍스트 전용: 로컬 어시스턴트는 글만 읽고 쓰며, 이미지·PDF·오디오 등 첨부 파일은 아직 인식하지 못합니다. 이것은 로컬 모드의 현재 제약일 뿐 영구적인 것은 아니며, 로컬 모델의 멀티모달 지원은 향후 버전에 추가될 예정입니다. 그전까지 첨부가 필요하면 클라우드 어시스턴트를 사용하세요."),
    ("lmSubTogglesResetNote", "Tools and GPU always start off each time you turn the local assistant on, even if you had them on last time — so a heavy option can never be inherited silently.", "每次开启本地助手时，「允许使用工具」与「使用 GPU」都会自动回到关闭状态，即使上一次开启过也是如此——避免高开销选项被悄悄继承。", "ローカルアシスタントをオンにするたび、「ツールの使用」と「GPU」は前回オンにしていても必ずオフから始まります。負荷の高い設定が知らないうちに引き継がれることはありません。", "로컬 어시스턴트를 켤 때마다 '도구 사용'과 'GPU'는 지난번에 켜 두었더라도 항상 꺼진 상태로 시작합니다. 부담이 큰 옵션이 조용히 이어지지 않도록 하기 위함입니다."),
    ("lmEnableToConfigureNote", "Turn on the switch above to import a model and configure the local assistant.", "开启上方的开关后，即可导入模型并配置本地助手。", "上のスイッチをオンにすると、モデルのインポートとローカルアシスタントの設定ができます。", "위 스위치를 켜면 모델을 가져오고 로컬 어시스턴트를 설정할 수 있습니다."),
    ("lmNeedModelNotice", "The local assistant is on but no model is imported yet. Import a GGUF model below — until then the assistant has nothing to answer with.", "本地助手已开启，但尚未导入模型。请在下方导入一个 GGUF 模型——在此之前助手无法作答。", "ローカルアシスタントはオンですが、モデルがまだインポートされていません。下でGGUFモデルをインポートしてください。それまではアシスタントは応答できません。", "로컬 어시스턴트는 켜져 있지만 아직 가져온 모델이 없습니다. 아래에서 GGUF 모델을 가져오세요. 그전까지는 어시스턴트가 답변할 수 없습니다."),
    ("lmBackgroundToggle", "Keep replying in the background", "后台继续回复", "バックグラウンドでも応答を続ける", "백그라운드에서도 계속 답변"),
    ("lmBackgroundToggleDesc", "Off by default: leaving Lucent stops the current reply and frees the model's memory. Turn this on to let a reply finish while Lucent is in the background.", "默认关闭：离开 Lucent 时会中止当前回复并释放模型占用的内存。开启后，Lucent 退到后台时回复可以继续完成。", "デフォルトはオフ：Lucentを離れると現在の応答を中止し、モデルのメモリを解放します。オンにすると、Lucentがバックグラウンドにある間も応答を最後まで続けます。", "기본값은 꺼짐: Lucent를 벗어나면 현재 답변을 중단하고 모델 메모리를 해제합니다. 켜면 Lucent가 백그라운드에 있어도 답변이 끝까지 진행됩니다."),
    ("lmBackgroundWarnTitle", "Keep the model running in the background?", "让模型在后台继续运行？", "バックグラウンドでモデルを動かし続けますか？", "백그라운드에서 모델을 계속 실행할까요?"),
    ("lmBackgroundWarnBody", "The reply will keep generating after you leave Lucent, so a model several gigabytes in size stays in memory the whole time. That can make your phone feel slow and use more battery. Leave this off unless you really need long replies to finish while you're elsewhere.", "开启后，离开 Lucent 后回复仍会继续生成，因此数 GB 大小的模型会一直驻留在内存中。这可能让手机变卡、耗电更快。除非确实需要长回复在你离开时继续完成，否则建议保持关闭。", "オンにすると、Lucentを離れた後も応答の生成が続くため、数ギガバイトのモデルがずっとメモリに残ります。端末の動作が重くなったり、電池の消費が増えたりすることがあります。他の画面にいる間に長い応答を終わらせる必要がなければ、オフのままをおすすめします。", "켜면 Lucent를 벗어난 뒤에도 답변 생성이 계속되므로 수 기가바이트 크기의 모델이 계속 메모리에 남아 있습니다. 휴대폰이 느려지거나 배터리 소모가 늘 수 있습니다. 다른 곳에 있는 동안 긴 답변을 꼭 끝내야 하는 것이 아니라면 꺼 두는 것을 권장합니다."),
    ("lmExitWhileReplyingTitle", "The assistant is still replying", "助手仍在回复中", "アシスタントはまだ応答中です", "어시스턴트가 아직 답변 중입니다"),
    ("lmExitWhileReplyingBody", "Leaving now stops the reply and frees the memory the local model is using. Whatever has already been written is kept in the conversation, marked as stopped.", "现在退出会中止回复，并释放本地模型占用的运行内存。已经生成的内容会保留在会话中，并标记为已中止。", "いま終了すると応答を中止し、ローカルモデルが使用しているメモリを解放します。すでに生成された内容は「中止」と表示されたうえで会話に残ります。", "지금 나가면 답변을 중단하고 로컬 모델이 사용 중인 메모리를 해제합니다. 이미 작성된 내용은 '중단됨'으로 표시되어 대화에 남습니다."),
    ("lmExitAnyway", "Stop and exit", "中止并退出", "中止して終了", "중단하고 종료"),
    ("lmKeepWaiting", "Keep waiting", "继续等待", "待機を続ける", "계속 기다리기"),
    ("replyStopped", "Reply stopped.", "回复已中止。", "応答を中止しました。", "답변이 중단되었습니다."),
    ("replyStoppedBackground", "Reply stopped because Lucent went to the background. You can turn on background replies in Settings > Assistant > Local model.", "因 Lucent 退到后台，回复已中止。可在「设置 > 助手 > 本地模型」中开启后台回复。", "Lucentがバックグラウンドに移ったため応答を中止しました。「設定 > アシスタント > ローカルモデル」でバックグラウンド応答をオンにできます。", "Lucent가 백그라운드로 전환되어 답변이 중단되었습니다. '설정 > 어시스턴트 > 로컬 모델'에서 백그라운드 답변을 켤 수 있습니다."),
    # Toast copy. Android 12+ hard-caps a system Toast at TWO lines and ellipsizes the rest,
    # so these must stay short: one sentence, the unavailability and its subject, nothing more.
    # The full why-and-what-comes-back explanation lives on the settings pages themselves
    # (memoryTierLocalNote / the dimmed row descriptions), where there is room for it.
    ("webSearchLocalDisabledHint", "Web search is unavailable while the local assistant is on.", "本地助手开启时无法使用网络搜索。", "ローカルアシスタントがオンの間はウェブ検索を使用できません。", "로컬 어시스턴트 사용 중에는 웹 검색을 쓸 수 없습니다."),
    ("memoryHighLocalDisabledHint", "High memory is unavailable while the local assistant is on.", "本地助手开启时无法使用高档记忆。", "ローカルアシスタントがオンの間は記憶「高」を使用できません。", "로컬 어시스턴트 사용 중에는 높은 메모리를 쓸 수 없습니다."),
    ("memoryLocalTierNote", "The local assistant is on: memory is set to low and the high tier is unavailable, because an on-device model works best with a short prompt. Your previous choice comes back when you turn the local assistant off.", "本地助手已开启：记忆已设为低档，且高档不可用——端侧模型在较短上下文下表现最好。关闭本地助手后会恢复你之前的选择。", "ローカルアシスタントがオンです：記憶は「低」に設定され、「高」は選べません。端末上のモデルは短いプロンプトで最もよく動作します。オフにすると以前の選択に戻ります。", "로컬 어시스턴트가 켜져 있습니다: 메모리는 '낮음'으로 설정되고 '높음'은 사용할 수 없습니다. 온디바이스 모델은 짧은 프롬프트에서 가장 잘 작동합니다. 끄면 이전 선택으로 돌아갑니다."),
    ("apiNoneTitle", "No API saved", "尚无已保存的 API", "保存されたAPIがありません", "저장된 API 없음"),
    ("apiNoneBody", "You've deleted every saved API. Add one to use the cloud assistant, or import a local model to chat offline.", "你已删除全部已保存的 API。添加一个即可使用云端助手，或导入本地模型离线聊天。", "保存済みのAPIをすべて削除しました。クラウドアシスタントを使うには1つ追加するか、ローカルモデルをインポートしてオフラインで会話してください。", "저장된 API를 모두 삭제했습니다. 클라우드 어시스턴트를 사용하려면 하나를 추가하거나, 로컬 모델을 가져와 오프라인으로 대화하세요."),
    ("helpLocalizedFilters", "Filters also work in your own language — type 完成 / 完了 / 완료 instead of is:done. Wrap a word in quotes to search for it literally.", "筛选词也支持用你自己的语言输入——可以直接输入「已完成」，无需 is:done。用引号括起来则按字面搜索。", "フィルターは日本語でも使えます。is:done の代わりに「完了」と入力できます。引用符で囲むと、その語をそのまま検索します。", "필터는 한국어로도 사용할 수 있습니다. is:done 대신 '완료'라고 입력하면 됩니다. 따옴표로 묶으면 글자 그대로 검색합니다."),
    "",
    "// ---- Editable tool confirmations, declined actions, and modular backup ----",
    ("assistantDeclinedReply(details: String)", "You said no, so I didn’t do it — {details}. Nothing was changed. Tell me if you’d like it done differently.", "你拒绝了这个操作，所以我没有执行——{details}。什么都没有改变。如果想换个方式，告诉我就行。", "ご承認いただけなかったので実行していません——{details}。何も変更されていません。別の形でご希望でしたら教えてください。", "거절하셔서 실행하지 않았습니다 — {details}. 변경된 것은 없습니다. 다른 방식을 원하시면 말씀해 주세요."),
    ("confirmEditTitleLabel", "Title", "标题", "タイトル", "제목"),
    ("confirmEditNewTitleLabel", "New title", "新标题", "新しいタイトル", "새 제목"),
    ("confirmEditItemLabel", "Item", "条目", "項目", "항목"),
    ("confirmEditNewTextLabel", "New text", "新文本", "新しいテキスト", "새 텍스트"),
    ("confirmEditHint", "You can edit this before confirming.", "确认前可以先修改。", "確認前に編集できます。", "확인하기 전에 수정할 수 있습니다."),
    ("confirmOpenEditor", "Approve and fine-tune in the editor", "确认并到编辑页调整", "承認してエディタで微調整", "승인 후 편집 페이지에서 조정"),
    ("confirmOpenEditorHint", "Runs the action as shown, then opens the item's page so you can adjust every detail.", "先按上面的内容执行，然后打开该条目的页面，方便你继续调整每个细节。", "表示どおり実行したあと、その項目のページを開いて細部を調整できます。", "표시된 대로 실행한 뒤 해당 항목 페이지를 열어 세부 내용을 조정할 수 있습니다."),
    ("assistantConfirmToolsTitle", "Confirm assistant actions", "手动确认助手操作", "アシスタントの操作を確認", "어시스턴트 작업 확인"),
    ("assistantConfirmToolsSub", "Ask before the assistant changes anything — creating, editing, deleting, pinning, or attaching files to notes and tasks. Reading your notes and tasks and searching the web run straight away. Turn off to let it act without asking at all.", "助手在修改内容前会弹窗请你确认——新建、编辑、删除、置顶、添加附件等。读取笔记和任务、联网搜索会直接执行，不再打扰你。关闭后所有操作都不再询问。", "アシスタントが内容を変更する前に確認します（作成・編集・削除・ピン留め・ファイル添付など）。ノートやタスクの読み取りとウェブ検索はそのまま実行されます。オフにするとすべて確認なしで実行します。", "어시스턴트가 내용을 변경하기 전에 확인합니다 — 생성·편집·삭제·고정·파일 첨부 등. 노트와 작업 읽기, 웹 검색은 바로 실행됩니다. 끄면 모든 작업을 확인 없이 실행합니다."),
    ("backupChooseWhat", "What to include", "备份内容", "バックアップ対象", "백업 항목"),
    ("restoreChooseWhat", "What to restore", "还原内容", "復元対象", "복원 항목"),
    ("backupModNotes", "Notes", "笔记", "メモ", "노트"),
    ("backupModTasks", "Tasks", "任务", "タスク", "할 일"),
    ("backupModChats", "Assistant conversations", "助手对话", "アシスタントの会話", "어시스턴트 대화"),
    ("backupModSettings", "App settings", "应用设置", "アプリ設定", "앱 설정"),
    ("backupModApi", "API profiles and keys", "API 配置与密钥", "APIプロファイルとキー", "API 프로필과 키"),
    ("backupModLocalAssistant", "Local assistant settings", "本地助手设置", "ローカルアシスタント設定", "로컬 어시스턴트 설정"),
    ("backupModLocalModelFiles", "Local model files", "本地模型文件", "ローカルモデルのファイル", "로컬 모델 파일"),
    ("backupModLocalModelFilesDesc(size: String)", "Includes the imported .gguf files themselves ({size}), so a restore brings the model back too. This makes the backup very large — leave it off if you only want your notes and settings.", "包含已导入的 .gguf 模型文件本体（{size}），还原时模型也会一并恢复。这会使备份文件非常大——如果只想备份笔记和设置，请保持关闭。", "インポート済みの .gguf ファイル自体（{size}）を含めるため、復元時にモデルも戻ります。バックアップが非常に大きくなるので、メモと設定だけでよければオフのままにしてください。", "가져온 .gguf 파일 자체({size})를 포함하므로 복원 시 모델도 함께 돌아옵니다. 백업 파일이 매우 커지므로 노트와 설정만 필요하다면 꺼 두세요."),
    ("backupSelectionEmpty", "Pick at least one thing to include.", "请至少选择一项内容。", "少なくとも 1 つ選んでください。", "최소 한 가지를 선택하세요."),
    ("backupModelFilesRestored(count: Int)", " Restored {count} local model file(s).", "已还原 {count} 个本地模型文件。", "ローカルモデルファイル {count} 件を復元しました。", "로컬 모델 파일 {count}개를 복원했습니다."),
    ("backupModelsInFile(count: Int)", "{count} local model file(s)", "{count} 个本地模型文件", "ローカルモデルファイル {count} 件", "로컬 모델 파일 {count}개"),
    ("bkImportedFonts", "Imported fonts", "导入的字体", "インポート済みフォント", "가져온 글꼴"),
    ("backupFontsRestored(count: Int)", " Restored {count} imported font(s).", "已还原 {count} 个导入的字体。", "インポート済みフォント {count} 件を復元しました。", "가져온 글꼴 {count}개를 복원했습니다."),
    ("backupModSettingsFontsDesc(size: String)", "Includes your imported font files ({size}), so a restore brings your fonts back too.", "包含已导入的字体文件（{size}），还原时字体也会一并恢复。", "インポート済みのフォントファイル（{size}）を含めるため、復元時にフォントも戻ります。", "가져온 글꼴 파일({size})을 포함하므로 복원 시 글꼴도 함께 복원됩니다."),
    "",
    "// ---- Per-item backup selection (second-level picker) ----",
    ("backupChooseItems", "Choose…", "选择…", "選択…", "선택…"),
    ("backupNOfM(chosen: Int, total: Int)", "{chosen} of {total} selected", "已选 {chosen}/{total}", "{total} 件中 {chosen} 件を選択", "{total}개 중 {chosen}개 선택"),
    ("backupPickNotesTitle", "Which notes?", "选择要备份的笔记", "バックアップするメモ", "백업할 노트"),
    ("backupPickTasksTitle", "Which tasks?", "选择要备份的任务", "バックアップするタスク", "백업할 할 일"),
    ("backupPickChatsTitle", "Which conversations?", "选择要备份的对话", "バックアップする会話", "백업할 대화"),
    ("backupPickApiTitle", "Which API profiles?", "选择要备份的 API 配置", "バックアップする API プロファイル", "백업할 API 프로필"),
    ("backupImportApiLimit(canAdd: Int, max: Int)", "Over the {max}-profile limit — choose up to {canAdd} to import:", "超过 {max} 个配置上限——请选择最多 {canAdd} 个导入：", "{max} 件の上限を超えています。インポートする {canAdd} 件までを選択してください：", "{max}개 제한을 초과했습니다. 가져올 항목을 최대 {canAdd}개 선택하세요:"),
    ("backupImportApiFull(max: Int)", "You already have the maximum of {max} API profiles. Remove one first to import more.", "你已有 {max} 个 API 配置（已达上限）。请先删除一个再导入。", "API プロファイルは既に上限の {max} 件です。インポートするには先に 1 件削除してください。", "API 프로필이 이미 최대 {max}개입니다. 더 가져오려면 먼저 하나를 삭제하세요."),
    ("backupNothingToPick", "There is nothing here to back up yet.", "目前没有可备份的内容。", "バックアップできるものはまだありません。", "아직 백업할 항목이 없습니다."),

    # ---- 1.1.0 / group A ----
    # Checklist rows: insert in the middle instead of only appending (task A18).
    ("checklistInsertBelow", "Insert item below", "在下方插入一条", "下に項目を挿入", "아래에 항목 삽입"),

    # Attachment rows: rename (A4) and custom order (A11).
    ("attachmentRenameTitle", "Rename attachment", "重命名附件", "添付ファイルの名前を変更", "첨부파일 이름 바꾸기"),
    ("attachmentNameLabel", "File name", "文件名", "ファイル名", "파일 이름"),
    ("attachmentNameTaken", "Another attachment already uses that name.", "已有同名附件。", "同じ名前の添付ファイルが既にあります。", "같은 이름의 첨부파일이 이미 있습니다."),
    ("a11yRenameNamed(name: String)", "Rename {name}", "重命名 {name}", "{name} の名前を変更", "{name} 이름 바꾸기"),
    ("a11yDragToReorder", "Drag to reorder", "拖拽以调整顺序", "ドラッグして並べ替え", "드래그하여 순서 변경"),

    # Text-based preview for documents and code files (task A1).
    ("previewTextOnly", "Text preview — layout, images and styling are not shown.", "纯文本预览——不含排版、图片和样式。", "テキストのみのプレビューです（レイアウト・画像・書式は表示されません）。", "텍스트만 미리 봅니다 — 레이아웃·이미지·서식은 표시되지 않습니다."),
    ("previewTextTruncated", "Preview truncated. Open the file to read the rest.", "预览已截断，打开文件可查看剩余内容。", "プレビューは途中までです。続きはファイルを開いてください。", "미리보기가 잘렸습니다. 나머지는 파일을 열어 확인하세요."),
    ("cantLoadText", "This file's text couldn't be read.", "无法读取该文件的文本。", "このファイルのテキストを読み取れませんでした。", "이 파일의 텍스트를 읽을 수 없습니다."),
    ("previewEmptyDocument", "No readable text in this document.", "该文档中没有可读文本。", "この文書に読み取れるテキストはありません。", "이 문서에는 읽을 수 있는 텍스트가 없습니다."),

    # Home list: pinned items get their own section above everything else (task A13).
    ("sectionPinned", "Pinned", "置顶", "ピン留め", "고정됨"),

    # Task A25 (replacement scope): confirm a share once the user is back in Lucent.
    ("sharedToast(name: String)", "Shared \u201c{name}\u201d", "\u5df2\u5206\u4eab\u201c{name}\u201d", "\u300c{name}\u300d\u3092\u5171\u6709\u3057\u307e\u3057\u305f", "\u201c{name}\u201d\uc744(\ub97c) \uacf5\uc720\ud588\uc2b5\ub2c8\ub2e4"),

    # Task A23: on-screen readouts for the video gestures.
    ("videoSpeedBoost", "2\u00d7", "2\u00d7", "2\u00d7", "2\u00d7"),
    ("a11yReplay", "Replay", "\u91cd\u64ad", "\u518d\u751f\u3057\u76f4\u3059", "\ub2e4\uc2dc \uc7ac\uc0dd"),
    ("a11yBrightness", "Brightness", "\u4eae\u5ea6", "\u660e\u308b\u3055", "\ubc1d\uae30"),
    ("a11yVolume", "Volume", "\u97f3\u91cf", "\u97f3\u91cf", "\uc74c\ub7c9"),

    # Task A6: detail pages get an explicit "copy the whole thing" action, now that a long press
    # selects text instead of silently copying everything.
    ("copyAll", "Copy all", "\u590d\u5236\u5168\u90e8", "\u3059\u3079\u3066\u30b3\u30d4\u30fc", "\uc804\uccb4 \ubcf5\uc0ac"),
    ("copiedAllToast", "Copied", "\u5df2\u590d\u5236", "\u30b3\u30d4\u30fc\u3057\u307e\u3057\u305f", "\ubcf5\uc0ac\ub428"),

    # Task A19: manual deletion of a single stored revision (notes and tasks alike).
    ("deleteVersionTitle", "Delete this version?", "\u5220\u9664\u8fd9\u4e2a\u7248\u672c\uff1f", "\u3053\u306e\u30d0\u30fc\u30b8\u30e7\u30f3\u3092\u524a\u9664\u3057\u307e\u3059\u304b\uff1f", "\uc774 \ubc84\uc804\uc744 \uc0ad\uc81c\ud560\uae4c\uc694?"),
    ("deleteVersionBody(time: String)", "The version from {time} will be removed permanently. The current text is not affected.", "\u5c06\u6c38\u4e45\u5220\u9664 {time} \u7684\u7248\u672c\u3002\u5f53\u524d\u5185\u5bb9\u4e0d\u53d7\u5f71\u54cd\u3002", "{time} \u306e\u30d0\u30fc\u30b8\u30e7\u30f3\u3092\u5b8c\u5168\u306b\u524a\u9664\u3057\u307e\u3059\u3002\u73fe\u5728\u306e\u5185\u5bb9\u306b\u5f71\u97ff\u306f\u3042\u308a\u307e\u305b\u3093\u3002", "{time} \ubc84\uc804\uc774 \uc601\uad6c\uc801\uc73c\ub85c \uc0ad\uc81c\ub429\ub2c8\ub2e4. \ud604\uc7ac \ub0b4\uc6a9\uc740 \uc601\ud5a5\uc744 \ubc1b\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4."),
    ("deleteThisVersion", "Delete this version", "\u5220\u9664\u6b64\u7248\u672c", "\u3053\u306e\u30d0\u30fc\u30b8\u30e7\u30f3\u3092\u524a\u9664", "\uc774 \ubc84\uc804 \uc0ad\uc81c"),
    ("historySubtasksHeader", "Subtasks", "\u5b50\u4efb\u52a1", "\u30b5\u30d6\u30bf\u30b9\u30af", "\ud558\uc704 \ud560 \uc77c"),

    # Task A10: the draft area, a sibling of the trash.
    ("screenDrafts", "Drafts", "\u8349\u7a3f", "\u4e0b\u66f8\u304d", "\uc784\uc2dc \uc800\uc7a5"),
    ("saveToDraft", "Save to drafts", "\u5b58\u5165\u8349\u7a3f", "\u4e0b\u66f8\u304d\u306b\u4fdd\u5b58", "\uc784\uc2dc \uc800\uc7a5\uc5d0 \ubcf4\uad00"),
    ("draftsEmpty", "No drafts. Anything you save here — or that was still open when the app closed unexpectedly — will appear in this list.", "\u6ca1\u6709\u8349\u7a3f\u3002\u4f60\u5b58\u5165\u7684\u5185\u5bb9\uff0c\u6216\u8005\u8f6f\u4ef6\u5f02\u5e38\u5173\u95ed\u65f6\u672a\u4fdd\u5b58\u7684\u7f16\u8f91\uff0c\u90fd\u4f1a\u51fa\u73b0\u5728\u8fd9\u91cc\u3002", "\u4e0b\u66f8\u304d\u306f\u3042\u308a\u307e\u305b\u3093\u3002\u4fdd\u5b58\u3057\u305f\u3082\u306e\u3084\u3001\u30a2\u30d7\u30ea\u304c\u4e88\u671f\u305b\u305a\u7d42\u4e86\u3057\u305f\u3068\u304d\u306e\u7de8\u96c6\u5185\u5bb9\u304c\u3053\u3053\u306b\u8868\u793a\u3055\u308c\u307e\u3059\u3002", "\uc784\uc2dc \uc800\uc7a5\ub41c \ud56d\ubaa9\uc774 \uc5c6\uc2b5\ub2c8\ub2e4. \uc800\uc7a5\ud55c \ub0b4\uc6a9\uc774\ub098 \uc571\uc774 \uc608\uae30\uce58 \uc54a\uac8c \uc885\ub8cc\ub420 \ub54c \ud3b8\uc9d1 \uc911\uc774\ub358 \ub0b4\uc6a9\uc774 \uc5ec\uae30\uc5d0 \ud45c\uc2dc\ub429\ub2c8\ub2e4."),
    ("draftSavedToast", "Saved to drafts", "\u5df2\u5b58\u5165\u8349\u7a3f", "\u4e0b\u66f8\u304d\u306b\u4fdd\u5b58\u3057\u307e\u3057\u305f", "\uc784\uc2dc \uc800\uc7a5\uc5d0 \ubcf4\uad00\ud588\uc2b5\ub2c8\ub2e4"),
    ("draftRestoreTitle", "Unfinished edits found", "\u53d1\u73b0\u672a\u5b8c\u6210\u7684\u7f16\u8f91", "\u672a\u5b8c\u6210\u306e\u7de8\u96c6\u304c\u3042\u308a\u307e\u3059", "\uc644\ub8cc\ub418\uc9c0 \uc54a\uc740 \ud3b8\uc9d1\uc774 \uc788\uc2b5\ub2c8\ub2e4"),
    # Round R1 - the abnormal-shutdown prompt (task 5). Deliberately worded as a question about a
    # PAGE, not about data: the draft copy is safe either way, what is being offered is the trip back.
    ("sessionRestoreTitle", "Go back to where you were?", "要回到上次的页面吗？", "前回の画面に戻りますか？", "마지막 화면으로 돌아갈까요?"),
    ("sessionRestoreNoteBody(title: String)", "Lucent closed unexpectedly while you were editing the note \u201c{title}\u201d. Reopen it with the changes you had not saved?", "上次 Lucent 在你编辑笔记“{title}”时异常关闭。是否重新打开并恢复当时未保存的内容？", "メモ「{title}」の編集中に Lucent が予期せず終了しました。未保存の内容を復元して開き直しますか？", "노트 “{title}”을(를) 편집하는 중에 Lucent가 예기치 않게 종료되었습니다. 저장하지 않은 내용을 복원해 다시 열까요?"),
    ("sessionRestoreTaskBody(title: String)", "Lucent closed unexpectedly while you were editing the task \u201c{title}\u201d. Reopen it with the changes you had not saved?", "上次 Lucent 在你编辑任务“{title}”时异常关闭。是否重新打开并恢复当时未保存的内容？", "タスク「{title}」の編集中に Lucent が予期せず終了しました。未保存の内容を復元して開き直しますか？", "할 일 “{title}”을(를) 편집하는 중에 Lucent가 예기치 않게 종료되었습니다. 저장하지 않은 내용을 복원해 다시 열까요?"),
    ("sessionRestoreConfirm", "Take me back", "回到上次页面", "戻る", "돌아가기"),
    ("sessionRestoreDismiss", "Start fresh", "不用，重新开始", "新しく始める", "새로 시작"),
    ("draftRestoreBody(count: Int)", "Lucent closed with {count} unsaved item(s). They were kept in Drafts — open it now?", "Lucent \u4e0a\u6b21\u5173\u95ed\u65f6\u6709 {count} \u9879\u672a\u4fdd\u5b58\uff0c\u5df2\u4fdd\u7559\u5728\u8349\u7a3f\u91cc\u3002\u73b0\u5728\u6253\u5f00\u5417\uff1f", "Lucent \u306e\u7d42\u4e86\u6642\u306b\u672a\u4fdd\u5b58\u306e\u9805\u76ee\u304c {count} \u4ef6\u3042\u308a\u3001\u4e0b\u66f8\u304d\u306b\u4fdd\u5b58\u3055\u308c\u3066\u3044\u307e\u3059\u3002\u4eca\u958b\u304d\u307e\u3059\u304b\uff1f", "Lucent\uac00 \uc885\ub8cc\ub420 \ub54c \uc800\uc7a5\ub418\uc9c0 \uc54a\uc740 \ud56d\ubaa9 {count}\uac1c\uac00 \uc784\uc2dc \uc800\uc7a5\uc5d0 \ubcf4\uad00\ub418\uc5c8\uc2b5\ub2c8\ub2e4. \uc9c0\uae08 \uc5f4\uae4c\uc694?"),
    ("draftOpen", "Open drafts", "\u6253\u5f00\u8349\u7a3f", "\u4e0b\u66f8\u304d\u3092\u958b\u304f", "\uc784\uc2dc \uc800\uc7a5 \uc5f4\uae30"),
    ("draftPromote", "Move out of drafts", "\u79fb\u51fa\u8349\u7a3f", "\u4e0b\u66f8\u304d\u304b\u3089\u623b\u3059", "\uc784\uc2dc \uc800\uc7a5\uc5d0\uc11c \uaebc\ub0b4\uae30"),

    # ---- Task A16: user-defined order ----
    ("sortCustom", "Custom order", "\u81ea\u5b9a\u4e49\u987a\u5e8f", "\u30ab\u30b9\u30bf\u30e0\u9806", "\uc0ac\uc6a9\uc790 \uc9c0\uc815 \uc21c\uc11c"),
    ("a11yDragItems", "Long-press and drag to reorder", "\u957f\u6309\u62d6\u62fd\u53ef\u8c03\u6574\u987a\u5e8f", "\u9577\u62bc\u3057\u3057\u3066\u30c9\u30e9\u30c3\u30b0\u3067\u4e26\u3079\u66ff\u3048", "\uae38\uac8c \ub20c\ub7ec \ub04c\uc5b4\uc11c \uc21c\uc11c \ubcc0\uacbd"),

    # ---- Task A21: the hidden area ----
    ("screenHidden", "Hidden", "\u9690\u85cf", "\u975e\u8868\u793a", "\uc228\uae40"),
    ("hiddenSettingTitle", "Show hidden area", "\u663e\u793a\u9690\u85cf\u533a", "\u975e\u8868\u793a\u30a8\u30ea\u30a2\u3092\u8868\u793a", "\uc228\uae40 \uc601\uc5ed \ud45c\uc2dc"),
    ("hiddenSettingDesc", "Reveals the hidden area in the Notes and Tasks menus. Turns itself back off the next time Lucent starts.", "\u5728\u7b14\u8bb0\u548c\u4efb\u52a1\u7684\u83dc\u5355\u4e2d\u663e\u793a\u9690\u85cf\u533a\u3002\u4e0b\u6b21\u542f\u52a8 Lucent \u65f6\u4f1a\u81ea\u52a8\u5173\u95ed\u3002", "\u30e1\u30e2\u3068\u30bf\u30b9\u30af\u306e\u30e1\u30cb\u30e5\u30fc\u306b\u975e\u8868\u793a\u30a8\u30ea\u30a2\u3092\u8868\u793a\u3057\u307e\u3059\u3002\u6b21\u56de Lucent \u8d77\u52d5\u6642\u306b\u81ea\u52d5\u3067\u30aa\u30d5\u306b\u623b\u308a\u307e\u3059\u3002", "\ub178\ud2b8\uc640 \ud560 \uc77c \uba54\ub274\uc5d0 \uc228\uae40 \uc601\uc5ed\uc744 \ud45c\uc2dc\ud569\ub2c8\ub2e4. \ub2e4\uc74c\ubc88 Lucent \uc2dc\uc791 \uc2dc \uc790\ub3d9\uc73c\ub85c \uaebc\uc9d1\ub2c8\ub2e4."),
    ("hiddenUnlockPrompt", "Enter your app lock password to show the hidden area.", "\u8bf7\u8f93\u5165\u5e94\u7528\u9501\u5bc6\u7801\u4ee5\u663e\u793a\u9690\u85cf\u533a\u3002", "\u975e\u8868\u793a\u30a8\u30ea\u30a2\u3092\u8868\u793a\u3059\u308b\u306b\u306f\u30a2\u30d7\u30ea\u30ed\u30c3\u30af\u306e\u30d1\u30b9\u30ef\u30fc\u30c9\u3092\u5165\u529b\u3057\u3066\u304f\u3060\u3055\u3044\u3002", "\uc228\uae40 \uc601\uc5ed\uc744 \ud45c\uc2dc\ud558\ub824\uba74 \uc571 \uc7a0\uae08 \ube44\ubc00\ubc88\ud638\ub97c \uc785\ub825\ud558\uc138\uc694."),
    ("hiddenWrongPassword", "Wrong password.", "\u5bc6\u7801\u9519\u8bef\u3002", "\u30d1\u30b9\u30ef\u30fc\u30c9\u304c\u9055\u3044\u307e\u3059\u3002", "\ube44\ubc00\ubc88\ud638\uac00 \ud2c0\ub838\uc2b5\ub2c8\ub2e4."),
    ("hiddenAdd", "Move to hidden", "\u52a0\u5165\u9690\u85cf", "\u975e\u8868\u793a\u306b\u79fb\u52d5", "\uc228\uae40\uc73c\ub85c \uc774\ub3d9"),
    ("hiddenRemove", "Move out of hidden", "\u79fb\u51fa\u9690\u85cf", "\u975e\u8868\u793a\u304b\u3089\u623b\u3059", "\uc228\uae40\uc5d0\uc11c \uaebc\ub0b4\uae30"),
    ("hiddenEmpty", "Nothing is hidden. Long-press a note or task and choose \u201cMove to hidden\u201d.", "\u9690\u85cf\u533a\u4e3a\u7a7a\u3002\u957f\u6309\u7b14\u8bb0\u6216\u4efb\u52a1\uff0c\u9009\u62e9\u201c\u52a0\u5165\u9690\u85cf\u201d\u3002", "\u975e\u8868\u793a\u306e\u9805\u76ee\u306f\u3042\u308a\u307e\u305b\u3093\u3002\u30e1\u30e2\u3084\u30bf\u30b9\u30af\u3092\u9577\u62bc\u3057\u3057\u3066\u300c\u975e\u8868\u793a\u306b\u79fb\u52d5\u300d\u3092\u9078\u3093\u3067\u304f\u3060\u3055\u3044\u3002", "\uc228\uae34 \ud56d\ubaa9\uc774 \uc5c6\uc2b5\ub2c8\ub2e4. \ub178\ud2b8\ub098 \ud560 \uc77c\uc744 \uae38\uac8c \ub20c\ub7ec \u201c\uc228\uae40\uc73c\ub85c \uc774\ub3d9\u201d\uc744 \uc120\ud0dd\ud558\uc138\uc694."),
    ("hiddenRemoveConfirmTitle", "Move out of the hidden area?", "要移出隐藏区吗？", "非表示から戻しますか？", "숨김에서 꺼낼까요?"),
    ("hiddenRemoveConfirmBody", "This item goes back to its normal list, where anyone using this device can see it.", "该条目将回到普通列表，使用本设备的任何人都能看到它。", "この項目は通常の一覧に戻り、この端末を使う人なら誰でも見られるようになります。", "이 항목은 일반 목록으로 돌아가며, 이 기기를 사용하는 누구나 볼 수 있게 됩니다."),
    ("hiddenRemoveConfirmAction", "Move out", "移出", "戻す", "꺼내기"),

    # ---- Task A22: doodle notes ----
    ("doodleNote", "Doodle", "\u6d82\u9e26", "\u624b\u66f8\u304d", "\ub099\uc11c"),
    ("doodleClear", "Clear canvas", "\u6e05\u7a7a\u753b\u5e03", "\u30ad\u30e3\u30f3\u30d0\u30b9\u3092\u6d88\u53bb", "\uce94\ubc84\uc2a4 \uc9c0\uc6b0\uae30"),
    ("doodleUndoStroke", "Undo stroke", "\u64a4\u9500\u4e00\u7b14", "\u4e00\u753b\u5143\u306b\u623b\u3059", "\ud55c \ud68d \uc2e4\ud589 \ucde8\uc18c"),
    ("doodleEmpty", "Nothing drawn yet.", "\u8fd8\u6ca1\u6709\u753b\u4efb\u4f55\u4e1c\u897f\u3002", "\u307e\u3060\u4f55\u3082\u63cf\u304b\u308c\u3066\u3044\u307e\u305b\u3093\u3002", "\uc544\uc9c1 \uadf8\ub824\uc9c4 \uac83\uc774 \uc5c6\uc2b5\ub2c8\ub2e4."),
    ("doodleRedoStroke", "Redo stroke", "重做笔画", "筆をやり直す", "획 다시 실행"),
    ("doodleAddPage", "Add a canvas", "添加画布", "キャンバスを追加", "캔버스 추가"),
    ("doodleSaveCanvas", "Save canvas", "保存画布", "キャンバスを保存", "캔버스 저장"),
    ("doodleDeletePage", "Delete this canvas", "删除这块画布", "このキャンバスを削除", "이 캔버스 삭제"),
    ("doodlePageName(index: Int)", "Canvas {index}", "画布 {index}", "キャンバス {index}", "캔버스 {index}"),

    # ---- Task A7: the floating scroll / quick-edit control ----
    ("a11yScrollTop", "Scroll to top", "\u56de\u5230\u9876\u90e8", "\u4e00\u756a\u4e0a\u3078", "\ub9e8 \uc704\ub85c"),
    ("a11yScrollBottom", "Scroll to bottom", "\u56de\u5230\u5e95\u90e8", "\u4e00\u756a\u4e0b\u3078", "\ub9e8 \uc544\ub798\ub85c"),
    ("a11yQuickEdit", "Quick edit", "\u5feb\u901f\u7f16\u8f91", "\u30af\u30a4\u30c3\u30af\u7de8\u96c6", "\ube60\ub978 \ud3b8\uc9d1"),
    ("actionRedo", "Redo", "\u91cd\u505a", "\u3084\u308a\u76f4\u3059", "\ub2e4\uc2dc \uc2e4\ud589"),

    # ============================================================================
    # Group B (assistant) — merged in during integration. These 52 keys were
    # hand-written into the generated Android I18n.kt by group B; moved here so the
    # generator remains the single source of truth for the Android file.
    # ============================================================================
    ("quickModelTitle", "Switch model", "切换模型", "モデルを切り替え", "모델 전환"),
    ("quickModelCurrent", "Current model", "当前模型", "現在のモデル", "현재 모델"),
    ("quickModelRecent", "Recently used", "最近使用", "最近使ったモデル", "최근 사용"),
    ("quickModelNone", "No model set yet", "尚未设置模型", "モデルが未設定です", "설정된 모델이 없습니다"),
    ("quickModelFetch", "Load this API's model list", "获取该 API 的模型列表", "この API のモデル一覧を取得", "이 API의 모델 목록 불러오기"),
    ("quickModelFetching", "Loading…", "获取中…", "取得中…", "불러오는 중…"),
    ("quickModelFetchFailed", "Couldn't load the model list. Check the API settings, or type a model name.", "无法获取模型列表。请检查 API 设置，或手动输入模型名。", "モデル一覧を取得できませんでした。API 設定を確認するか、モデル名を入力してください。", "모델 목록을 불러오지 못했습니다. API 설정을 확인하거나 모델 이름을 직접 입력하세요."),
    ("quickModelFetchEmpty", "This API returned no models.", "该 API 没有返回任何模型。", "この API からモデルは返されませんでした。", "이 API가 모델을 반환하지 않았습니다."),
    ("quickModelCustom", "Type a model name…", "手动输入模型名…", "モデル名を入力…", "모델 이름 직접 입력…"),
    ("quickModelCustomLabel", "Model name", "模型名称", "モデル名", "모델 이름"),
    ("quickModelLocalSection", "On-device models", "本机模型", "端末内モデル", "기기 내 모델"),
    ("quickModelLocalEmpty", "No model imported. Import one under Settings > Assistant > Local model.", "尚未导入模型。可在“设置 > 助手 > 本机模型”中导入。", "モデルが未インポートです。設定 > アシスタント > ローカルモデルから追加できます。", "가져온 모델이 없습니다. 설정 > 어시스턴트 > 로컬 모델에서 추가하세요."),
    ("quickModelSameApiHint", "Switches the model only — the API, key and endpoint stay as they are.", "仅切换模型，API、密钥和地址保持不变。", "モデルのみを切り替えます。API、キー、エンドポイントはそのままです。", "모델만 바꿔집니다. API와 키, 엔드포인트는 그대로입니다."),
    ("quickModelSwitched(model: String)", "Now using {model}", "已切换到 {model}", "{model} に切り替えました", "{model}(으)로 전환했습니다"),
    ("confirmReviewHint", "Check it over and change anything you like. Nothing is saved until you add it.", "可以先确认和修改内容，点“添加”之前不会保存任何东西。", "内容を確認・修正できます。「追加」を押すまで何も保存されません。", "내용을 확인하고 수정할 수 있습니다. ‘추가’를 누를 때까지 아무것도 저장되지 않습니다."),
    ("confirmAddIt", "Add it", "添加", "追加", "추가"),
    ("confirmKeepRefining", "Keep refining with the assistant", "和助手继续完善", "アシスタントと調整を続ける", "어시스턴트와 계속 다듬기"),
    ("confirmEditBodyLabel", "Body", "正文", "本文", "본문"),
    ("confirmEditChecklistLabel", "Checklist", "清单", "チェックリスト", "체크리스트"),
    ("confirmEditTagsLabel", "Tags", "标签", "タグ", "태그"),
    ("confirmEditNotesLabel", "Notes", "备注", "メモ", "메모"),
    ("confirmEditDueLabel", "Due", "截止时间", "期限", "마감"),
    ("confirmEditSubtasksLabel", "Subtasks", "子任务", "サブタスク", "하위 작업"),
    ("confirmEditFileNameLabel", "File name", "文件名", "ファイル名", "파일 이름"),
    ("confirmEditContentLabel", "Content", "内容", "内容", "내용"),
    ("smallModelModeTitle", "Optimize for small models", "针对小模型优化", "小さなモデル向けに最適化", "작은 모델에 맞게 최적화"),
    ("smallModelModeSub", "Send a much shorter prompt and a compact tool list, so a small or on-device model can keep up. Off by default.", "大幅缩短提示词并精简工具列表，让小模型或本机模型跑得动。默认关闭。", "プロンプトを大幅に短くし、ツール一覧も簡素にして、小さなモデルや端末内モデルでも動くようにします。既定ではオフ。", "프롬프트를 훨씬 짧게 줄이고 도구 목록도 간소화하여 작은 모델이나 기기 내 모델도 따라올 수 있게 합니다. 기본값은 끔짐입니다."),
    ("smallModelModeWarn", "This trims most of the assistant's instructions to the bare minimum. A small model will run faster and get stuck far less often, but the assistant's tone will be plainer and it will follow your personalization and style settings less closely. Turn it back off at any time.", "开启后会把助手的大部分指令精简到最低限度。小模型会更快、更少卡住，但助手的语气会变得平淡，对你设置的个性和风格遵循得也没那么好。可随时关闭。", "アシスタントへの指示の大半を最低限まで削ります。小さなモデルは速くなり、詰まることも減りますが、口調は素っ気なくなり、設定した性格やスタイルへの追従も弱くなります。いつでもオフにできます。", "어시스턴트 지침의 대부분을 최소한으로 줄입니다. 작은 모델은 더 빠르고 멈추는 일도 줄지만, 말투가 단순해지고 설정한 성격과 스타일을 덜 잘 따릅니다. 언제든 다시 끈 수 있습니다."),
    ("attachFromFiles", "Files", "文件", "ファイル", "파일"),
    ("attachFromCamera", "Camera", "相机", "カメラ", "카메라"),
    ("attachFromGallery", "Photos", "相册", "写真", "사진"),
    ("attachFromCloud", "Cloud storage", "云存储", "クラウドストレージ", "클라우드 저장소"),
    ("attachCameraFailed", "Couldn't open the camera. Try attaching a file instead.", "无法打开相机，请改用文件方式添加。", "カメラを起動できませんでした。ファイルから添付してください。", "카메라를 열 수 없습니다. 파일로 첨부해 보세요."),
    ("attachNoCloudFolder", "No cloud sync folder found on this PC (Google Drive, OneDrive, Dropbox).", "本机未找到云盘同步文件夹（Google Drive、OneDrive、Dropbox）。", "この PC にクラウド同期フォルダー（Google Drive、OneDrive、Dropbox）が見つかりません。", "이 PC에서 클라우드 동기화 폴더(Google Drive, OneDrive, Dropbox)를 찾지 못했습니다."),
    ("downloadFetching", "Fetching the files…", "正在获取文件…", "ファイルを取得中…", "파일을 가져오는 중…"),
    ("downloadFetchFailed", "Couldn't fetch that file. It may have expired or need a sign-in.", "无法获取该文件，可能已过期或需要登录。", "ファイルを取得できませんでした。期限切れかログインが必要な可能性があります。", "파일을 가져올 수 없습니다. 만료되었거나 로그인이 필요할 수 있습니다."),
    ("downloadFetchPartial(count: Int)", "{count} file(s) couldn't be fetched and were left out.", "有 {count} 个文件获取失败，已跳过。", "{count} 件のファイルを取得できず、除外しました。", "파일 {count}개를 가져오지 못해 제외했습니다."),
    ("downloadSaveInto", "Or save this reply and its files into:", "或将此回复及其文件转存为：", "またはこの返信とファイルを保存：", "또는 이 답변과 파일을 저장:"),
    ("saveAsNote", "New note", "新建笔记", "新しいノート", "새 노트"),
    ("saveAsTask", "New task", "新建任务", "新しいタスク", "새 작업"),
    ("lmFirstLoadHint", "Loading the model into memory. The first reply after opening the app takes a few seconds; the ones after it are much faster.", "正在将模型载入内存。打开应用后的第一条回复需要几秒，之后会快很多。", "モデルをメモリに読み込んでいます。アプリ起動後の最初の返信は数秒かかりますが、以降はずっと速くなります。", "모델을 메모리에 불러오는 중입니다. 앱 실행 후 첫 답변은 몇 초 걸리며, 그 다음부터는 훨씬 빨라집니다."),
    ("selectedCount(count: Int)", "{count} selected", "已选 {count} 条", "{count} 件選択中", "{count}개 선택됨"),
    ("messageActionsTitle", "Message", "消息", "メッセージ", "메시지"),
    ("msgCopyWhole", "Copy the whole message", "复制整条消息", "メッセージ全体をコピー", "메시지 전체 복사"),
    ("msgSelectText", "Select text", "选择文本", "テキストを選択", "텍스트 선택"),
    ("msgSelectTextHint", "Long-press the message to pick out just the part you want.", "长按消息即可只选取你需要的部分。", "メッセージを長押しすると、必要な部分だけを選べます。", "메시지를 길게 누르면 원하는 부분만 선택할 수 있습니다."),
    ("msgMultiSelect", "Select messages", "多选消息", "メッセージを複数選択", "메시지 선택"),
    ("batchDeleteTitle", "Delete these messages?", "删除这些消息？", "これらのメッセージを削除しますか？", "이 메시지를 삭제할까요?"),
    ("batchDeleteBody(count: Int)", "{count} message(s) will be deleted from this conversation. Chat messages don't go to Trash, so this can't be undone.", "将从本对话中删除 {count} 条消息。聊天消息不进回收站，无法撤销。", "この会話から {count} 件のメッセージを削除します。チャットのメッセージはゴミ箱に入らず、元に戻せません。", "이 대화에서 메시지 {count}개를 삭제합니다. 채팅 메시지는 휴지통으로 가지 않으므로 되돌릴 수 없습니다."),
    ("resendMessage", "Ask again", "重新发送", "もう一度送る", "다시 보내기"),
    ("variantPrevious", "Previous answer", "上一个回复", "前の回答", "이전 답변"),
    ("variantNext", "Next answer", "下一个回复", "次の回答", "다음 답변"),

    # ---- INTEGRATION: C-group task 20 rich text, wired into A-group's corner button ----
    ("richTextTitle", "Rich text", "富文本", "リッチテキスト", "서식 있는 텍스트"),
    ("richTextSub", "Select text while editing and style it from the corner button: three weights, italic, and five highlighters. No Markdown syntax to type.", "编辑时选中文字，用右下角按钮设置样式：三种字重、斜体和五种荧光笔。不需要输入任何 Markdown 语法。", "編集中にテキストを選択し、右下のボタンで書式を設定します：3 つの太さ、斜体、5 色のマーカー。Markdown 記法の入力は不要です。", "편집 중에 텍스트를 선택해 오른쪽 아래 버튼으로 서식을 지정합니다: 세 가지 굵기, 기울임, 형광펜 5색. 마크다운 문법을 입력할 필요가 없습니다."),
    ("richTextExclusiveHint", "Rich text and Markdown style the same text, so only one can be on. Turning this on turns the other off.", "富文本和 Markdown 会同时作用于同一段文字，因此只能开启其中一个。打开这个会自动关闭另一个。", "リッチテキストと Markdown は同じ文章に作用するため、同時には使えません。こちらをオンにすると、もう一方は自動的にオフになります。", "서식 있는 텍스트와 마크다운은 같은 문장에 적용되므로 하나만 켤 수 있습니다. 이것을 켜면 다른 하나는 자동으로 꺼집니다."),
    ("richTextExportNote", "Formatting is kept when exporting to PDF and Word (.docx). Markdown, plain text and spreadsheets have no way to carry it, so those formats export the words without the styling.", "导出为 PDF 和 Word（.docx）时会保留格式。Markdown、纯文本和表格没有对应的表示方式，这几种格式只导出文字、不带样式。", "PDF と Word（.docx）への書き出しでは書式が保持されます。Markdown、プレーンテキスト、表計算には対応する表現がないため、文字のみが書き出されます。", "PDF와 Word(.docx)로 내보낼 때는 서식이 유지됩니다. 마크다운, 일반 텍스트, 스프레드시트에는 대응하는 표현이 없어 글자만 내보냅니다."),
    ("richTextWeight", "Weight", "字重", "太さ", "굵기"),
    ("richTextLight", "Light", "细", "細字", "가늘게"),
    ("richTextBold", "Bold", "粗", "太字", "굵게"),
    ("richTextItalic", "Italic", "斜体", "斜体", "기울임"),
    ("richTextHighlight", "Highlighter", "荧光笔", "マーカー", "형광펜"),
    ("richTextClear", "Clear formatting", "清除格式", "書式をクリア", "서식 지우기"),
    ("richTextColor", "Colour", "字体颜色", "文字色", "글자 색"),
    ("richTextNeedSelection", "Select some text first, then choose a style.", "请先选中一段文字，再选择样式。", "先にテキストを選択してから、スタイルを選んでください。", "먼저 텍스트를 선택한 다음 스타일을 고르세요."),
    ("a11yRichTextToolbar", "Formatting", "格式", "書式", "서식"),

    # ---- Phase 3: assistant draft deletion (confirmation sentence) ----
    ("ccDeleteDraft(title: String)", "Permanently delete the draft \"{title}\" (drafts do not go to the Trash)", "永久删除草稿“{title}”（草稿不经过回收站）", "下書き「{title}」を完全に削除（下書きはゴミ箱を経由しません）", "초안 \"{title}\"을(를) 영구 삭제 (초안은 휴지통을 거치지 않습니다)"),

    # ---- W-1: AVX2 guard message (desktop shows it; Android declares it for the shared twin) ----
    ("localModelNeedsAvx2", "This computer's processor doesn't support AVX2, which the on-device model engine requires, so local models can't run here. Everything else in Lucent works normally, and cloud models are unaffected.", "这台电脑的处理器不支持 AVX2 指令集，而本地模型引擎需要它，因此本地模型无法在此运行。Lucent 的其它功能一切正常，云端模型也不受影响。", "このパソコンのプロセッサはオンデバイスモデルのエンジンに必要な AVX2 に対応していないため、ローカルモデルはここでは実行できません。Lucent のその他の機能は通常どおり動作し、クラウドモデルにも影響はありません。", "이 컴퓨터의 프로세서는 온디바이스 모델 엔진에 필요한 AVX2를 지원하지 않아 로컬 모델을 실행할 수 없습니다. Lucent의 다른 기능은 모두 정상 작동하며 클라우드 모델에도 영향이 없습니다."),

    # ---- F-1: saved searches ----
    ("saveSearchAction", "Save this search", "保存此搜索", "この検索を保存", "이 검색 저장"),
    ("saveSearchNamePlaceholder", "Name this view", "为这个视图命名", "このビューに名前を付ける", "이 보기의 이름 지정"),
    ("savedSearchRemove(name: String)", "Remove saved search \"{name}\"", "移除已保存的搜索“{name}”", "保存済み検索「{name}」を削除", "저장된 검색 \"{name}\" 제거"),

    # ---- PHASE 4: voice input (dictation) ----
    ("dictateStart", "Voice input", "语音输入", "音声入力", "음성 입력"),
    ("dictateStop", "Stop recording", "停止录音", "録音を停止", "녹음 중지"),
    ("dictateFailed", "Voice input didn't work. Please try again.", "语音输入失败，请重试。", "音声入力に失敗しました。もう一度お試しください。", "음성 입력에 실패했습니다. 다시 시도해 주세요."),
    ("sttNeedsApi", "Desktop voice input sends your recording to the AI provider you configured, and no API is set up yet. Add your API connection in Settings first.", "桌面端语音输入会把录音发送到你配置的 AI 服务商，目前还没有配置 API。请先在设置中填写 API 连接。", "デスクトップの音声入力は録音を設定済みの AI プロバイダーに送信しますが、API がまだ設定されていません。まず設定で API 接続を追加してください。", "데스크톱 음성 입력은 녹음을 설정한 AI 제공업체로 전송하는데, 아직 API가 설정되지 않았습니다. 먼저 설정에서 API 연결을 추가해 주세요."),

    # ---- PHASE 4: local multimodal (mmproj) ----
    ("lmMmprojTitle", "Multimodal projector (mmproj)", "多模态投影器（mmproj）", "マルチモーダルプロジェクター（mmproj）", "멀티모달 프로젝터(mmproj)"),
    ("lmMmprojDesc", "Lets the local model see images you attach in the assistant chat. Import the mmproj .gguf built for this exact model family — a projector from a different model will not work. Vision-capable model pages on Hugging Face provide it alongside the main .gguf.", "让本地模型能看懂你在助手聊天中附上的图片。请导入与当前模型同一家族的 mmproj .gguf——不同模型的投影器不能混用。支持视觉的模型在 Hugging Face 页面上会在主 .gguf 旁边提供它。", "アシスタントチャットに添付した画像をローカルモデルが理解できるようになります。このモデルと同じファミリー用に作られた mmproj .gguf をインポートしてください。別のモデルのプロジェクターは使えません。視覚対応モデルの Hugging Face ページでは、メインの .gguf と並んで提供されています。", "어시스턴트 채팅에 첨부한 이미지를 로컬 모델이 이해할 수 있게 합니다. 이 모델과 같은 계열용으로 만들어진 mmproj .gguf를 가져오세요. 다른 모델의 프로젝터는 사용할 수 없습니다. 비전을 지원하는 모델의 Hugging Face 페이지에서 메인 .gguf 옆에 함께 제공됩니다."),
    ("lmMmprojImport", "Import mmproj", "导入 mmproj", "mmproj をインポート", "mmproj 가져오기"),
    ("lmMmprojRemove", "Remove projector", "移除投影器", "プロジェクターを削除", "프로젝터 제거"),
    ("lmMmprojMissing", "Not imported — text only", "未导入——仅文本对话", "未インポート——テキストのみ", "가져오지 않음 — 텍스트 전용"),
    ("lmMmprojImported", "Projector imported. It loads with the model on the next reply.", "投影器已导入，下次回复时随模型一起加载。", "プロジェクターをインポートしました。次の返信時にモデルと一緒に読み込まれます。", "프로젝터를 가져왔습니다. 다음 답변 시 모델과 함께 로드됩니다."),
]

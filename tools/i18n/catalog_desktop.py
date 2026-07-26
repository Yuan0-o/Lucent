"""Desktop-only i18n entries (codebase review C-1, step 2).

The desktop I18n.kt is no longer a hand-maintained fork: gen_i18n.py now writes BOTH
files - the shared entries from catalog.py (minus ANDROID_ONLY below), plus these
DESKTOP_ONLY entries. Edit translations HERE, run the generator, commit both outputs.
Same tuple shape as catalog.py: ("key", en, zh, ja, ko) with {param} templates.
"""

# Keys that exist ONLY in the Android app (biometrics, Android-specific backup hints);
# the generator omits them from the desktop file so S.<key> there stays a compile error -
# the same typo-safety the catalog gives everything else.
ANDROID_ONLY = [
    "biometricFailed",
    "biometricPromptSubtitle",
    "biometricPromptTitle",
    "biometricUnlockDesc",
    "biometricUnlockTitle",
    "biometricUse",
    "biometricUsePassword",
]

DESKTOP_ONLY = [
    ("closeToTraySub", "Closing the window hides Lucent to the system tray instead of quitting, so reminders keep firing on time. Use Exit in the tray menu to really quit. Turn this off to make the close button quit as before — reminders then only fire while the window is open.", "点击关闭按钮时，Lucent 会隐藏到系统托盘继续运行，提醒照常按时触发。要彻底退出请用托盘菜单里的“退出”。关闭此开关则恢复点击即退出——那样提醒只在窗口打开期间有效。", "閉じるボタンで終了せず、システムトレイに隠れて動作を続けます。リマインダーは時間どおりに通知されます。完全に終了するにはトレイメニューの「終了」を使ってください。オフにすると従来どおり閉じる＝終了になり、リマインダーはウィンドウが開いている間のみ有効です。", "닫기 버튼을 누르면 종료되지 않고 시스템 트레이로 숨어 계속 실행되며, 알림은 제시간에 울립니다. 완전히 종료하려면 트레이 메뉴의 \"종료\"를 사용하세요. 이 옵션을 끄면 이전처럼 닫기 = 종료가 되고, 알림은 창이 열려 있는 동안에만 작동합니다."),
    ("closeToTrayTitle", "Keep running in the tray", "关闭后保留在托盘", "閉じてもトレイで実行し続ける", "닫아도 트레이에서 계속 실행"),
    ("exportPdfFontHint", "PDF text is drawn with your imported fonts (tried in list order). Characters none of them cover appear as \"·\".", "PDF 文本使用你导入的字体渲染（按列表顺序尝试）；这些字体都覆盖不到的字符将显示为“·”。", "PDFのテキストはインポート済みフォントで描画されます（一覧の順に試行）。どのフォントにも含まれない文字は「·」と表示されます。", "PDF 텍스트는 가져온 글꼴로 그려집니다(목록 순서대로 시도). 어떤 글꼴에도 없는 문자는 \"·\"로 표시됩니다."),
    ("exportPdfNoFontHint", "No fonts imported: PDFs fall back to a built-in Latin font, so other characters appear as \"·\". Import a font in Settings to cover your language.", "尚未导入任何字体：PDF 将退回内置拉丁字体，其他文字会显示为“·”。可在设置中导入字体以覆盖你的语言。", "フォントが未インポートのため、PDFは内蔵のラテン文字フォントのみで描画され、それ以外の文字は「·」と表示されます。設定でフォントをインポートしてください。", "가져온 글꼴이 없어 PDF는 내장 라틴 글꼴로만 렌더링되며, 그 외 문자는 \"·\"로 표시됩니다. 설정에서 글꼴을 가져와 주세요."),
    ("helloDesc", "Also unlock Lucent with your fingerprint, face, or device PIN instead of typing your password. Available because this PC has Windows Hello set up.", "除输入密码外，还可用指纹、面容或设备 PIN 解锁 Lucent。因为此电脑已设置 Windows Hello，所以可用。", "パスワードの入力に加えて、指紋・顔・デバイスの PIN で Lucent のロックを解除できます。この PC で Windows Hello が設定されているため利用できます。", "비밀번호를 입력하는 대신 지문, 얼굴 또는 장치 PIN으로 Lucent 잠금을 해제할 수 있습니다. 이 PC에 Windows Hello가 설정되어 있어 사용할 수 있습니다."),
    ("helloTitle", "Windows Hello", "Windows Hello", "Windows Hello", "Windows Hello"),
    ("insightsActive", "Active", "进行中", "進行中", "진행 중"),
    ("insightsAllClear", "All clear", "全部完成", "すべて完了", "모두 완료"),
    ("insightsCompleted", "Completed", "已完成", "完了", "완료"),
    ("insightsEmpty", "No tasks yet. Add one and your insights will appear here.", "还没有任务。添加后这里会显示概览。", "タスクがありません。追加すると概要が表示されます。", "아직 할 일이 없습니다. 추가하면 개요가 표시됩니다."),
    ("insightsNeedsAttention", "Needs attention", "需要关注", "要対応", "주의 필요"),
    ("lockHelloFailed", "Windows Hello couldn't verify you. Enter your password instead.", "Windows Hello 验证未通过，请改用密码。", "Windows Hello で確認できませんでした。パスワードを入力してください。", "Windows Hello로 확인하지 못했습니다. 비밀번호를 입력하세요."),
    ("lockUseWindowsHello", "Unlock with Windows Hello", "使用 Windows Hello 解锁", "Windows Hello でロック解除", "Windows Hello로 잠금 해제"),
    ("startWithWindowsSub", "Launch Lucent automatically when you sign in, so reminders are armed without you thinking about it. Uses a per-user registry entry; no administrator rights needed.", "登录 Windows 时自动启动 Lucent，提醒无需惦记就已就位。使用当前用户的注册表项，不需要管理员权限。", "サインイン時に Lucent を自動起動し、リマインダーを常に待機させます。現在のユーザーのレジストリ項目を使用し、管理者権限は不要です。", "로그인할 때 Lucent를 자동으로 실행해 알림이 항상 준비되도록 합니다. 현재 사용자의 레지스트리 항목을 사용하며 관리자 권한이 필요 없습니다."),
    ("startWithWindowsTitle", "Start with Windows", "开机自动启动", "Windows と同時に起動", "Windows 시작 시 자동 실행"),
    ("tabInsights", "Insights", "概览", "概要", "개요"),
    ("tabSearch", "Search", "搜索", "検索", "검색"),
    ("trayExit", "Exit", "退出", "終了", "종료"),
    ("trayOpen", "Open Lucent", "打开 Lucent", "Lucent を開く", "Lucent 열기"),
]

package com.evfix.validate;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import moe.shizuku.server.IRemoteProcess;
import moe.shizuku.server.IShizukuService;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity implements Shizuku.OnRequestPermissionResultListener {
    private static final int C_BG = 0xFFF1F3F6;
    private static final int C_CARD = 0xFFFFFFFF;
    private static final int C_TITLE = 0xFF111827;
    private static final int C_LABEL = 0xFF6B7280;
    private static final int C_FIELD = 0xFF374151;
    private static final int C_GREEN = 0xFF1F9D55;
    private static final int C_RED = 0xFFDC3A3A;
    private static final int C_SEC_BG = 0xFFE8EBF0;
    private static final int C_SEC_FG = 0xFF1F2937;
    private static final int C_PILL_OK_BG = 0xFFE4F6EC;
    private static final int C_PILL_OK_FG = 0xFF17663B;
    private static final int C_PILL_WARN_BG = 0xFFFFF3DC;
    private static final int C_PILL_WARN_FG = 0xFF8A5B00;
    private static final int C_PILL_ERR_BG = 0xFFFCE4E4;
    private static final int C_PILL_ERR_FG = 0xFFA32020;
    private static final int C_LOG_BG = 0xFF0B0E13;
    private static final int C_LOG_FG = 0xFF7BE38B;
    private static final String[] MAT = {"0 恒等(不修正)", "1 逆时针90° ←推荐(修 右→上)", "2 顺时针90°", "3 旋转180°", "4 交换XY", "5 翻转X", "6 翻转Y"};
    private static final int REQ = 1;
    private static final int REQ_NOTIF = 4;
    private static final int REQ_OVERLAY = 3;
    private static final int REQ_PICK_DIR = 2;
    private CheckBox ballCb;
    private Spinner bufSpin;
    private Spinner deviceSpin;
    private View helpOverlay;
    private ScrollView logScroll;
    private TextView logView;
    private Spinner matSpin;
    private Button refreshBtn;
    private Button startBtn;
    private TextView pill;
    private Button stopBtn;
    private final List<String> devPaths = new ArrayList();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final Runnable uiTick = new Runnable() {
        @Override
        public void run() {
            MainActivity.this.updateButtons();
            MainActivity.this.refreshLogView();
            MainActivity.this.main.postDelayed(this, 500L);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        if (FixService.running) {
            String dev = FixService.runningDevice;
            if (dev != null) {
                selectDeviceInSpinner(dev);
            }
            this.matSpin.setSelection(FixService.runningMatrix);
            int buf = FixService.runningBuf;
            this.bufSpin.setSelection(buf == 0 ? 0 : buf == 4 ? 1 : buf == 8 ? 2 : 3);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_BG);
        int pad = dp(14);
        root.setPadding(pad, pad, pad, pad);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("OTG 鼠标校正");
        title.setTextSize(18.0f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(C_TITLE);
        title.setSingleLine(true);
        this.pill = new TextView(this);
        this.pill.setTextSize(12.0f);
        this.pill.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        header.addView(this.pill, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Button helpBtn = flatButton("使用说明", C_SEC_BG, C_SEC_FG, 11.0f);
        helpBtn.setPadding(dp(8), dp(6), dp(8), dp(6));
        helpBtn.setOnClickListener(v -> showHelp(true));
        LinearLayout.LayoutParams helpLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        helpLp.leftMargin = dp(8);
        header.addView(helpBtn, helpLp);
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout devCard = card("设备");
        LinearLayout devRow = labeledRow("输入设备", null);
        this.deviceSpin = new Spinner(this);
        this.refreshBtn = flatButton("刷新", C_SEC_BG, C_SEC_FG, 12.0f);
        this.refreshBtn.setOnClickListener(v -> refreshDevices());
        devRow.addView(this.deviceSpin, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        LinearLayout.LayoutParams rfLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rfLp.leftMargin = dp(6);
        devRow.addView(this.refreshBtn, rfLp);
        addWithMargin(devCard, devRow, dp(2));
        this.matSpin = new Spinner(this);
        ArrayAdapter<String> ma = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, MAT);
        ma.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.matSpin.setAdapter((SpinnerAdapter) ma);
        this.matSpin.setSelection(1);
        addWithMargin(devCard, labeledRow("修正矩阵", this.matSpin), dp(8));
        this.bufSpin = new Spinner(this);
        ArrayAdapter<String> ba = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"0 关(原始)", "4ms(低延迟)", "8ms 推荐", "16ms 稳"});
        ba.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.bufSpin.setAdapter((SpinnerAdapter) ba);
        this.bufSpin.setSelection(2);
        addWithMargin(devCard, labeledRow("合并缓冲", this.bufSpin), dp(8));
        root.addView(devCard, cardLp(false));

        LinearLayout ctlCard = card("控制");
        this.ballCb = new CheckBox(this);
        this.ballCb.setText("显示悬浮球（切到游戏里也能控制）");
        this.ballCb.setTextSize(13.0f);
        this.ballCb.setTextColor(C_FIELD);
        this.ballCb.setChecked(true);
        ctlCard.addView(this.ballCb, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        this.startBtn = flatButton("开始接管", C_GREEN, 0xFFFFFFFF, 15.0f);
        this.startBtn.setOnClickListener(v -> start());
        this.stopBtn = flatButton("停止接管", C_RED, 0xFFFFFFFF, 15.0f);
        this.stopBtn.setOnClickListener(v -> stopGrab());
        this.stopBtn.setEnabled(false);
        btnRow.addView(this.startBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        stopLp.leftMargin = dp(8);
        btnRow.addView(this.stopBtn, stopLp);
        addWithMargin(ctlCard, btnRow, dp(10));
        root.addView(ctlCard, cardLp(true));

        LinearLayout logCard = card("运行日志");
        this.logView = new TextView(this);
        this.logView.setTypeface(Typeface.MONOSPACE);
        this.logView.setTextSize(11.0f);
        this.logView.setTextColor(C_LOG_FG);
        int lp2 = dp(10);
        this.logView.setPadding(lp2, lp2, lp2, lp2);
        GradientDrawable logBg = new GradientDrawable();
        logBg.setColor(C_LOG_BG);
        logBg.setCornerRadius(dp(10));
        this.logView.setBackground(logBg);
        this.logScroll = new ScrollView(this);
        this.logScroll.addView(this.logView);
        logCard.addView(this.logScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        root.addView(logCard, logCardLp());

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(C_BG);
        frame.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        this.helpOverlay = buildHelpOverlay();
        this.helpOverlay.setVisibility(View.GONE);
        frame.addView(this.helpOverlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(frame);
    }

    private void showHelp(boolean show) {
        if (this.helpOverlay != null) {
            this.helpOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private View buildHelpOverlay() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(C_BG);
        int pad = dp(16);
        page.setPadding(pad, pad, pad, pad);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("使用说明");
        title.setTextSize(18.0f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(C_TITLE);
        Button closeBtn = flatButton("关闭", C_SEC_BG, C_SEC_FG, 12.0f);
        closeBtn.setOnClickListener(v -> showHelp(false));
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        header.addView(closeBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.bottomMargin = dp(10);
        page.addView(header, hlp);

        ScrollView sc = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        sc.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        slp.topMargin = dp(4);
        page.addView(sc, slp);

        addHelpSection(content, "一、工作原理",
                "本应用通过 Shizuku 获得与 adb（shell）同级的系统权限，把自带的命令行工具 evtool（安装包内的 libevtool.so）复制到 /data/local/tmp/evtool 并赋予执行权限后运行。"
                        + "evtool 会独占抓取（EVIOCGRAB）你在「输入设备」中选定的鼠标设备节点（/dev/input/eventX），此后该鼠标产生的原始事件全部交给 evtool 处理："
                        + "先按「修正矩阵」对 X/Y 相对位移做几何变换，再按「合并缓冲」的时间窗口合并相邻事件，最后通过内核 uinput 虚拟设备把修正后的事件重新注入系统。"
                        + "由于原生输入路径已被替换，光标方向即为修正后的方向，从而修复部分机型 OTG 鼠标轴向映射错误（如右移变上移）的问题。"
                        + "抓取期间由前台服务保活（通知栏常驻「EvFix」通知）；停止接管时结束 evtool 进程，系统自动恢复原生输入。运行状态同时写入 /data/local/tmp/evfix_status.txt 供排查。");
        addHelpSection(content, "二、前置条件与权限说明",
                "系统要求：Android 10（API 29）及以上。\n\n"
                        + "Shizuku：本应用所有特权操作都经由 Shizuku 执行，必须先安装并激活 Shizuku。两种方式：① 无线调试——系统设置打开开发者选项与「无线调试」，在 Shizuku 应用内点「通过无线调试启动」，按提示输入配对码完成配对（部分机型重启后需重新配对）；② ROOT——已 root 的设备在 Shizuku 应用内点「通过 Root 启动」，重启后需再次启动或配置开机自启。\n\n"
                        + "应用授权：首次打开本应用会自动弹出 Shizuku 授权框，请选择允许；若当时拒绝，重启本应用会再次弹出，也可在 Shizuku 应用→「使用 Shizuku 的应用」中手动允许。\n\n"
                        + "权限说明：① 「显示在其他应用上层」（悬浮窗权限）——显示悬浮球/面板所需，仅当勾选「显示悬浮球」且尚未授权时，点「开始接管」会引导开启；② 「通知」权限（Android 13+）——显示前台服务常驻通知所需，首次开始接管时请求；③ 前台服务——保持接管会话在后台持续运行。\n\n"
                        + "隐私说明：本应用未申请联网权限，不采集任何数据；特权操作仅限于部署与运行 /data/local/tmp/evtool、枚举与抓取输入设备、结束自身进程。");
        addHelpSection(content, "三、各项设置详解",
                "输入设备：点「刷新」通过 Shizuku 执行 evtool list 枚举系统输入设备。列表项格式为 [标记] 节点名 · 设备名称 [VID:PID]：带 [鼠标] 前缀的是 evtool 根据设备能力位自动识别出的鼠标类设备，优先选择它；VID:PID 是 USB 设备的厂商/产品编号，可用来确认是不是你的鼠标。首次刷新会先把 evtool 部署到 /data/local/tmp 并赋权（日志区可见 cp 与 chmod 记录）。若日志出现 OPEN_FAIL 开头的行，表示该设备节点打开失败（权限受限），此类节点不会进入下拉列表。\n\n"
                        + "修正矩阵：对相对位移 (x, y) 做几何变换，共 7 档，请对照症状选择——\n"
                        + "· 0 恒等（不修正）：原样透传，用于对比验证；\n"
                        + "· 1 逆时针90°（推荐）：修「向右移动、光标向上」类症状，多数 OTG 方向异常用它即可解决；\n"
                        + "· 2 顺时针90°：修「向右移动、光标向下」类症状；\n"
                        + "· 3 旋转180°：修「向右移动、光标向左（上下同时反向）」类症状；\n"
                        + "· 4 交换XY：仅交换横纵轴，不反转正负方向；\n"
                        + "· 5 翻转X：仅水平方向反向（右移变左移，垂直正常）；\n"
                        + "· 6 翻转Y：仅垂直方向反向（上移变下移，水平正常）。\n"
                        + "判断方法：观察光标实际方向与手部动作的差异，选对应档位；拿不准就按 1→2→4→3→5→6 顺序尝试。主界面切换矩阵后需手动重新开始接管；悬浮面板切换会自动重启接管。\n\n"
                        + "合并缓冲：把时间窗口内的相邻事件合并后一次性上报，窗口越长越平滑、延迟越高——0 关（原始）：逐条上报，完整保留原始时序；4ms（低延迟）：几乎无感延迟，适合竞技游戏；8ms（推荐）：平滑与延迟的平衡点，默认选项；16ms（稳）：光标最稳，适合办公浏览。\n\n"
                        + "显示悬浮球：以你点「开始接管」那一刻的勾选状态为准并记住，下次启动沿用。开始接管后屏幕出现圆形悬浮球——灰色 OFF 表示未在接管，绿色 ON 表示接管中；单击弹出/收起控制面板，按住可拖动位置。\n\n"
                        + "开始 / 停止接管：「开始接管」要求 Shizuku 就绪且已选择设备，否则以 Toast 提示具体原因；成功后右上角胶囊显示「运行中 · 设备名 · M矩阵号」，通知栏出现 EvFix 常驻通知。「停止接管」结束进程并恢复原生输入，胶囊回到 Shizuku 状态。");
        addHelpSection(content, "四、分步使用教程",
                "第 1 步 激活 Shizuku：按「二、前置条件」的方法激活，确认 Shizuku 应用内显示运行中。\n"
                        + "第 2 步 授权本应用：打开本应用，首次自动弹出授权框选择允许；成功后右上角胶囊变为绿色「Shizuku 已授权」。\n"
                        + "第 3 步 连接鼠标：OTG 线连接鼠标；供电不足时改用带独立供电的 OTG 线或拓展坞。\n"
                        + "第 4 步 刷新并选设备：点「刷新」（未就绪会提示，先回到第 1 步）。日志区出现「---- 输入设备 ----」与设备清单；在「输入设备」下拉框选择带 [鼠标] 标记的条目。\n"
                        + "第 5 步 试方向：保持「修正矩阵」为 1，点「开始接管」，到桌面或备忘录移动鼠标。方向正确→下一步；不对→点「停止接管」，换矩阵档位后再开始；勾选了悬浮球的也可直接在面板上点「矩阵」按钮，每点一次切换下一档并自动重启接管。\n"
                        + "第 6 步 调缓冲：打游戏用 4ms 或 8ms；出现抖动加大到 16ms；确认无抖动后追求极致手感可用 0 关。\n"
                        + "第 7 步 悬浮控制（可选）：勾选「显示悬浮球」后开始接管；首次会引导授予悬浮窗权限，授权后需重新点「开始接管」。游戏中单击悬浮球呼出面板：开始 / 停止 / 矩阵 / 主界面 / 退出；按住面板顶部状态栏可拖动面板，悬浮球本身也可拖动。\n"
                        + "第 8 步 结束使用：点「停止接管」或悬浮面板的「停止」；面板「退出」会停止接管并直接结束前台服务（悬浮球一并消失）。从最近任务划掉本应用也会自动停止接管并移除悬浮窗。");
        addHelpSection(content, "五、注意事项",
                "· Shizuku 是一切功能的前提：未运行或未授权时刷新与开始都会失败；本应用无法自行拉起 Shizuku，被系统清理后需手动重新激活。\n"
                        + "· 接管期间选中鼠标的原始输入被修正后的事件替换；正常停止、进程意外退出、从最近任务划掉应用三种情况都会自动恢复原生输入。\n"
                        + "· 面板切换矩阵会先停止、约 0.15 秒后自动重启接管，瞬间鼠标无响应属正常；主界面切换矩阵需手动重新开始。\n"
                        + "· 「显示悬浮球」的勾选在点「开始接管」时生效并记住；未授悬浮窗权限时点开始会先引导授权，此时接管尚未开始，授权后需再点一次。\n"
                        + "· 运行日志仅存于内存（应用内日志区），内容超过约 6 万字符时自动裁剪到最近 4 万字符，应用退出即清空；需留存请现场截图。\n"
                        + "· Android 13+ 首次开始接管会请求通知权限；拒绝后接管仍能运行，只是看不到常驻通知。\n"
                        + "· 系统设置里「强行停止」本应用会连同前台服务一起结束并恢复原生输入。\n"
                        + "· 频繁自动停止的常见原因：OTG 供电不稳（换带供电的线/拓展坞）、Shizuku 被系统清理（锁定 Shizuku 后台，或改用 ROOT 方式自启）。");
        addHelpSection(content, "六、常见问题排查",
                "Q：刷新提示「Shizuku 未就绪」？\nA：看右上角胶囊——「Shizuku 未运行」请先激活 Shizuku；「Shizuku 待授权」请在弹框中允许，或到 Shizuku 应用内手动授权。\n\n"
                        + "Q：刷新后下拉框只有「(无设备)」？\nA：确认鼠标已被系统识别（连接后屏幕出现指针），更换 USB 口/OTG 线后重新点刷新；日志若大量出现 OPEN_FAIL，多为系统限制，重启手机或重启 Shizuku 后再试。\n\n"
                        + "Q：列表中没有带 [鼠标] 标记的条目？\nA：按 VID:PID 编号选择你鼠标对应的条目（编号可查鼠标包装或官网）；键盘、传感器等其他设备请勿选择。\n\n"
                        + "Q：方向怎么调都不对？\nA：先确认症状归类（右移后光标向上/向下/向左），按矩阵表对应选择；纯水平反向用 5，纯垂直反向用 6，横纵互换用 4。\n\n"
                        + "Q：游戏里鼠标无响应？\nA：确认胶囊为「运行中」；把合并缓冲降到 4ms 或 0 关再试；个别游戏会过滤 uinput 虚拟设备的事件，此类游戏暂时无法支持。\n\n"
                        + "Q：悬浮球不见了？\nA：确认开始接管时「显示悬浮球」已勾选；确认本应用已获「显示在其他应用上层」权限（系统设置→应用→OTG鼠标校正→其他权限）；从最近任务划掉应用会移除悬浮球，重新开始接管即可。\n\n"
                        + "Q：通知栏的 EvFix 通知一直在？\nA：这是前台服务保活通知，点开可回主界面；停止接管后标题变回「EvFix 待命」，通过面板「退出」或系统强行停止结束服务后通知消失。");
        return page;
    }

    private void addHelpSection(LinearLayout parent, String title, String body) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(C_CARD);
        bg.setCornerRadius(dp(12));
        card.setBackground(bg);
        int p = dp(14);
        card.setPadding(p, p, p, p);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(15.0f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(0xFF1F2937);
        card.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView b = new TextView(this);
        b.setText(body);
        b.setTextSize(14.0f);
        b.setTextColor(C_FIELD);
        b.setLineSpacing(dp(3), 1.2f);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(10);
        card.addView(b, blp);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(12);
        parent.addView(card, clp);
    }

    private LinearLayout card(String title) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(C_CARD);
        bg.setCornerRadius(dp(14));
        c.setBackground(bg);
        int p = dp(12);
        c.setPadding(p, p, p, p);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(11.0f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(C_LABEL);
        t.setPadding(0, 0, 0, dp(6));
        c.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return c;
    }

    private LinearLayout labeledRow(String label, View field) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(13.0f);
        l.setTextColor(C_FIELD);
        row.addView(l, new LinearLayout.LayoutParams(dp(72), ViewGroup.LayoutParams.WRAP_CONTENT));
        if (field != null) {
            row.addView(field, rowFieldLp());
        }
        return row;
    }

    private LinearLayout.LayoutParams rowFieldLp() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
    }

    private void addWithMargin(LinearLayout parent, View v, int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        parent.addView(v, lp);
    }

    private LinearLayout.LayoutParams cardLp(boolean first) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(first ? 12 : 10);
        return lp;
    }

    private LinearLayout.LayoutParams logCardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        lp.topMargin = dp(10);
        return lp;
    }

    private Button flatButton(String text, int bgColor, int fgColor, float sizeSp) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(sizeSp);
        b.setTextColor(fgColor);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(dp(12), dp(9), dp(12), dp(9));
        b.setMinimumWidth(0);
        b.setMinimumHeight(0);
        b.setStateListAnimator(null);
        GradientDrawable g = new GradientDrawable();
        g.setColor(bgColor);
        g.setCornerRadius(dp(12));
        b.setBackground(g);
        return b;
    }

    private void setPill(String text, int bgColor, int fgColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(bgColor);
        g.setCornerRadius(dp(999));
        this.pill.setBackground(g);
        this.pill.setTextColor(fgColor);
        this.pill.setPadding(dp(10), dp(4), dp(10), dp(4));
        this.pill.setText(text);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Shizuku.addRequestPermissionResultListener(this);
        this.main.post(this.uiTick);
        updateStatus();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Shizuku.removeRequestPermissionResultListener(this);
        this.main.removeCallbacks(this.uiTick);
    }

    @Override
    public void onRequestPermissionResult(int requestCode, int grantResult) {
        updateStatus();
    }

    private void updateStatus() {
        String[] r = resolveStatus();
        setPill(r[0], Integer.parseInt(r[1]), Integer.parseInt(r[2]));
        if ("Shizuku 待授权".equals(r[0])) {
            Shizuku.requestPermission(1);
        }
    }

    private String[] resolveStatus() {
        String s;
        int bg;
        int fg;
        try {
            if (!Shizuku.pingBinder()) {
                s = "Shizuku 未运行";
                bg = C_PILL_ERR_BG;
                fg = C_PILL_ERR_FG;
            } else if (Shizuku.checkSelfPermission() == 0) {
                s = "Shizuku 已授权";
                bg = C_PILL_OK_BG;
                fg = C_PILL_OK_FG;
            } else {
                s = "Shizuku 待授权";
                bg = C_PILL_WARN_BG;
                fg = C_PILL_WARN_FG;
            }
        } catch (Throwable t) {
            s = "Shizuku 异常";
            bg = C_PILL_ERR_BG;
            fg = C_PILL_ERR_FG;
        }
        return new String[]{s, String.valueOf(bg), String.valueOf(fg)};
    }

    private boolean shizukuReady() {
        try {
            if (Shizuku.pingBinder()) {
                return Shizuku.checkSelfPermission() == 0;
            }
            return false;
        } catch (Throwable th) {
            return false;
        }
    }

    private void updateButtons() {
        boolean run = FixService.running;
        boolean ready = shizukuReady();
        this.startBtn.setEnabled(!run && ready);
        this.startBtn.setAlpha(this.startBtn.isEnabled() ? 1.0f : 0.4f);
        this.stopBtn.setEnabled(run);
        this.stopBtn.setAlpha(run ? 1.0f : 0.4f);
        this.refreshBtn.setEnabled(!this.busy.get());
        this.refreshBtn.setAlpha(this.refreshBtn.isEnabled() ? 1.0f : 0.4f);
        if (run) {
            String dev = FixService.runningDevice;
            setPill("运行中 · " + (dev == null ? "?" : dev.replace("/dev/input/", "")) + " · M" + FixService.runningMatrix, C_PILL_OK_BG, C_PILL_OK_FG);
        } else {
            String[] r = resolveStatus();
            setPill(r[0], Integer.parseInt(r[1]), Integer.parseInt(r[2]));
        }
    }

    private void refreshLogView() {
        String s;
        synchronized (FixService.sharedLog) {
            s = FixService.sharedLog.toString();
        }
        if (!s.equals(this.logView.getText().toString())) {
            this.logView.setText(s);
            this.logScroll.post(() -> this.logScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void refreshDevices() {
        if (!shizukuReady()) {
            toast("Shizuku 未就绪");
            updateStatus();
        } else if (this.busy.compareAndSet(false, true)) {
            this.refreshBtn.setEnabled(false);
            new Thread(() -> doRefreshDevices()).start();
        }
    }

    private void doRefreshDevices() {
        try {
            try {
                String[] out = Shell.run("/data/local/tmp/evtool list 2>/dev/null || echo __NEED_STAGE__");
                if (out[0].contains("__NEED_STAGE__")) {
                    String lib = getApplicationInfo().nativeLibraryDir + "/libevtool.so";
                    String[] st = Shell.run("cp '" + lib + "' /data/local/tmp/evtool && chmod 755 /data/local/tmp/evtool && /data/local/tmp/evtool list");
                    parseDevices(st[0], st[1]);
                } else {
                    parseDevices(out[0], out[1]);
                }
                this.busy.set(false);
            } catch (Exception e) {
                log("!! 刷新失败: " + e);
                this.busy.set(false);
            }
            this.main.post(() -> this.refreshBtn.setEnabled(true));
        } catch (Throwable th) {
            log("!! 刷新异常: " + th);
            this.busy.set(false);
            this.main.post(() -> this.refreshBtn.setEnabled(true));
        }
    }

    private void parseDevices(String raw, String err) {
        final List<String> paths = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        int mousePos = -1;
        log("---- 输入设备 ----");
        if (err != null && !err.isEmpty()) {
            log("[stderr] " + err.trim());
        }
        String[] split = raw.split("\n");
        for (String line0 : split) {
            String line = line0.trim();
            if (line.startsWith("PATH=")) {
                String path = between(line, "PATH=", " | ");
                String name = between(line, "NAME=", " | ");
                String vid = between(line, "VID=", " | ");
                String pid = between(line, "PID=", " | ");
                String mouse = between(line, "MOUSE=", null);
                if (path.contains("OPEN_FAIL")) {
                    log(path + " (" + name + ")");
                } else {
                    boolean isMouse = "1".equals(mouse);
                    if (isMouse) {
                        mousePos = paths.size();
                    }
                    paths.add(path);
                    labels.add((isMouse ? "[鼠标] " : "") + path.replace("/dev/input/", "") + " · " + name + " [" + vid + ":" + pid + "]");
                }
            }
        }
        for (String l : labels) {
            log(l);
        }
        final int selPos = mousePos;
        this.main.post(() -> applyDeviceList(paths, labels, selPos));
    }

    private void applyDeviceList(List<String> paths, List<String> labels, int selPos) {
        List<String> list;
        this.devPaths.clear();
        this.devPaths.addAll(paths);
        if (labels.isEmpty()) {
            List<String> m = new ArrayList<>();
            m.add("(无设备)");
            list = m;
        } else {
            list = labels;
        }
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, list);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.deviceSpin.setAdapter((SpinnerAdapter) ad);
        if (selPos >= 0) {
            this.deviceSpin.setSelection(selPos);
        }
        if (FixService.running && FixService.runningDevice != null && this.devPaths.indexOf(FixService.runningDevice) >= 0) {
            this.deviceSpin.setSelection(this.devPaths.indexOf(FixService.runningDevice));
        }
        if (labels.isEmpty()) {
            log("!! 没有发现输入设备");
        }
    }

    private void selectDeviceInSpinner(String path) {
        int idx = this.devPaths.indexOf(path);
        if (idx >= 0) {
            this.deviceSpin.setSelection(idx);
        }
    }

    private String between(String s, String start, String end) {
        int a = s.indexOf(start);
        if (a < 0) {
            return "";
        }
        int a2 = a + start.length();
        int b = end == null ? s.length() : s.indexOf(end, a2);
        if (b < 0) {
            b = s.length();
        }
        return s.substring(a2, b);
    }

    private void start() {
        if (!shizukuReady()) {
            toast("Shizuku 未就绪");
            updateStatus();
            return;
        }
        int pos = this.deviceSpin.getSelectedItemPosition();
        if (pos < 0 || pos >= this.devPaths.size()) {
            toast("请先刷新并选择鼠标设备");
            return;
        }
        if (this.ballCb.isChecked() && !Settings.canDrawOverlays(this)) {
            toast("请授予悬浮窗权限（只弹一次）");
            Intent i = new Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION", Uri.parse("package:" + getPackageName()));
            startActivityForResult(i, 3);
            return;
        }
        int buf = 4;
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 4);
        }
        String dev = this.devPaths.get(pos);
        int mat = this.matSpin.getSelectedItemPosition();
        if (this.bufSpin.getSelectedItemPosition() == 0) {
            buf = 0;
        } else if (this.bufSpin.getSelectedItemPosition() != 1) {
            buf = this.bufSpin.getSelectedItemPosition() == 2 ? 8 : 16;
        }
        Intent it = new Intent(this, (Class<?>) FixService.class);
        it.setAction(FixService.ACT_START);
        it.putExtra(FixService.EXTRA_DEVICE, dev);
        it.putExtra(FixService.EXTRA_MATRIX, mat);
        it.putExtra(FixService.EXTRA_BUF, buf);
        getSharedPreferences("cfg", 0).edit().putBoolean("ball", this.ballCb.isChecked()).apply();
        startForegroundService(it);
        toast("已启动接管");
    }

    private void stopGrab() {
        Intent it = new Intent(this, (Class<?>) FixService.class);
        it.setAction(FixService.ACT_STOP);
        startService(it);
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(1, v, getResources().getDisplayMetrics());
    }

    private void toast(String s) {
        Toast.makeText(this, s, 0).show();
    }

    private void log(final String s) {
        this.main.post(() -> {
            String ts = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
            synchronized (FixService.sharedLog) {
                FixService.sharedLog.append(ts).append(' ').append(s).append('\n');
                if (FixService.sharedLog.length() > 60000) {
                    FixService.sharedLog.delete(0, FixService.sharedLog.length() - 40000);
                }
            }
            refreshLogView();
        });
    }

    static class Shell {
        Shell() {
        }

        static String[] run(String script) throws Exception {
            IBinder b = Shizuku.getBinder();
            IShizukuService svc = IShizukuService.Stub.asInterface(b);
            IRemoteProcess p = svc.newProcess(new String[]{"/system/bin/sh", "-c", script}, null, null);
            StringBuilder so = new StringBuilder();
            StringBuilder se = new StringBuilder();
            Thread t1 = read(p.getInputStream(), so);
            Thread t2 = read(p.getErrorStream(), se);
            long deadline = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < deadline) {
                boolean alive;
                try {
                    alive = p.alive();
                } catch (Exception e) {
                    alive = false;
                }
                if (!alive) {
                    break;
                }
                Thread.sleep(200L);
            }
            try {
                p.destroy();
            } catch (Exception e) {
            }
            t1.join(2000L);
            t2.join(2000L);
            int code;
            try {
                code = p.exitValue();
            } catch (Exception e) {
                code = -1;
            }
            return new String[]{so.toString(), se.toString(), String.valueOf(code)};
        }

        static Thread read(final ParcelFileDescriptor pfd, final StringBuilder sb) {
            Thread t = new Thread(() -> {
                try {
                    BufferedReader r = new BufferedReader(new InputStreamReader(new ParcelFileDescriptor.AutoCloseInputStream(pfd)));
                    while (true) {
                        String l = r.readLine();
                        if (l == null) {
                            r.close();
                            return;
                        }
                        sb.append(l).append('\n');
                    }
                } catch (Exception e) {
                }
            });
            t.start();
            return t;
        }
    }
}

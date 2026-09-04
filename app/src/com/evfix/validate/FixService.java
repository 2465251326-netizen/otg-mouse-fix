package com.evfix.validate;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import moe.shizuku.server.IRemoteProcess;
import moe.shizuku.server.IShizukuService;
import rikka.shizuku.Shizuku;

public class FixService extends Service {
    public static final String ACT_KILL = "kill_service";
    public static final String ACT_MATRIX = "set_matrix";
    public static final String ACT_START = "start_grab";
    public static final String ACT_STOP = "stop_grab";
    private static final String CH = "evfix";
    private static final int C_BALL_OFF = 0xCC4B5563;
    private static final int C_BALL_ON = 0xCC1F9D55;
    private static final int C_PANEL_BG = 0xF214161D;
    private static final int C_PANEL_STROKE = 0x24FFFFFF;
    private static final int C_PANEL_TEXT = 0xFFE5E7EB;
    private static final int C_GREEN = 0xFF1F9D55;
    private static final int C_RED = 0xFFDC3A3A;
    private static final int C_BLUE = 0xFF2F6FED;
    private static final int C_NEUTRAL = 0xFF39414E;
    public static final String EXTRA_BUF = "buf";
    public static final String EXTRA_DEVICE = "device";
    public static final String EXTRA_MATRIX = "matrix";
    public static final String EXTRA_PICK_DIR = "pick_dir";
    private View ball;
    private TextView ballText;
    private Button bStart;
    private Button bStop;
    private Button bMatrix;
    private String device;
    private View panel;
    private IRemoteProcess proc;
    private TextView statusText;
    private WindowManager wm;
    public static volatile boolean running = false;
    public static volatile String runningDevice = null;
    public static volatile int runningMatrix = 1;
    public static volatile int runningBuf = 8;
    public static volatile String lastError = null;
    public static final StringBuilder sharedLog = new StringBuilder();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable restartGrab = this::startGrab;
    private int evPid = -1;
    private int matrix = 1;
    private int bufMs = 8;

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.wm = (WindowManager) getSystemService("window");
        Notification n = buildNotification("EvFix 待命", "从主界面或悬浮球开始接管");
        startForeground(1, n);
        log("服务已启动");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String act = intent == null ? null : intent.getAction();
        if (ACT_START.equals(act)) {
            this.device = intent.getStringExtra(EXTRA_DEVICE);
            this.matrix = intent.getIntExtra(EXTRA_MATRIX, 1);
            this.bufMs = intent.getIntExtra(EXTRA_BUF, 8);
            startGrab();
        } else if (ACT_STOP.equals(act)) {
            stopGrab();
        } else if (ACT_MATRIX.equals(act)) {
            this.matrix = intent.getIntExtra(EXTRA_MATRIX, 1);
            if (running) {
                stopGrab();
                this.main.postDelayed(this.restartGrab, 150L);
            }
        } else if (ACT_KILL.equals(act)) {
            stopGrab();
            stopForeground(true);
            stopSelf();
        }
        return 1;
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

    private IShizukuService svc() throws Exception {
        IBinder b = Shizuku.getBinder();
        if (b == null) {
            throw new IllegalStateException("Shizuku binder 不可用");
        }
        return IShizukuService.Stub.asInterface(b);
    }

    private void startGrab() {
        if (running) {
            if (this.device != null && this.device.equals(runningDevice) && this.matrix == runningMatrix && this.bufMs == runningBuf) {
                return;
            }
            stopGrab();
        }
        if (this.device == null) {
            log("!! 未指定设备");
            return;
        }
        if (!shizukuReady()) {
            log("!! Shizuku 未就绪");
            fail("Shizuku 未就绪");
            return;
        }
        String lib = getApplicationInfo().nativeLibraryDir + "/libevtool.so";
        String cmd = "cp '" + lib + "' /data/local/tmp/evtool && chmod 755 /data/local/tmp/evtool && /data/local/tmp/evtool grab " + this.device + " --matrix " + this.matrix + " --buf " + this.bufMs + " --status-file /data/local/tmp/evfix_status.txt";
        log(">> " + cmd);
        try {
            this.proc = svc().newProcess(new String[]{"/system/bin/sh", "-c", cmd}, null, null);
            running = true;
            runningDevice = this.device;
            runningMatrix = this.matrix;
            runningBuf = this.bufMs;
            lastError = null;
            pump(this.proc);
            if (getSharedPreferences("cfg", 0).getBoolean("ball", true)) {
                showBall();
            }
            updateBall();
            updateNotification("EvFix 运行中 · " + shortDev(this.device) + " · M" + this.matrix);
        } catch (Exception e) {
            log("!! 启动失败: " + e);
            fail(String.valueOf(e));
        }
    }

    private void pump(final IRemoteProcess p) {
        new Thread(() -> {
            try {
                readStream(new ParcelFileDescriptor.AutoCloseInputStream(p.getInputStream()));
            } catch (Exception e) {
            }
        }).start();
        new Thread(() -> {
            try {
                readStream(new ParcelFileDescriptor.AutoCloseInputStream(p.getErrorStream()));
            } catch (Exception e) {
            }
        }).start();
        new Thread(() -> {
            try {
                p.waitFor();
            } catch (Exception e) {
            }
            if (running) {
                running = false;
                runningDevice = null;
                log("<< evtool 进程退出(意外)");
                fail("evtool 意外退出, 原生输入已恢复");
                this.main.post(this::updateBall);
                updateNotification("EvFix 已停止(异常退出)");
            }
        }).start();
    }

    private void readStream(InputStream is) {
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(is));
            while (true) {
                String l = r.readLine();
                if (l != null) {
                    if (l.startsWith("PID=")) {
                        try {
                            this.evPid = Integer.parseInt(l.substring(4).trim());
                        } catch (Exception e) {
                        }
                    }
                    log(l);
                } else {
                    r.close();
                    return;
                }
            }
        } catch (Exception e2) {
        }
    }

    private void stopGrab() {
        this.main.removeCallbacks(this.restartGrab);
        if (running || this.proc != null) {
            running = false;
            runningDevice = null;
            try {
                if (this.proc != null) {
                    this.proc.destroy();
                }
            } catch (Exception e) {
            }
            final int pid = this.evPid;
            if (pid > 0) {
                new Thread(() -> {
                    try {
                        svc().newProcess(new String[]{"/system/bin/sh", "-c", "kill " + pid}, null, null);
                    } catch (Exception e) {
                    }
                }).start();
            }
            this.proc = null;
            this.evPid = -1;
            log("已停止, 原生输入恢复");
            updateBall();
            updateNotification("EvFix 待命");
        }
    }

    private void fail(String msg) {
        lastError = msg;
    }

    private boolean haveOverlayPerm() {
        return Settings.canDrawOverlays(this);
    }

    private void showBall() {
        if (this.ball != null || !haveOverlayPerm()) {
            return;
        }
        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-2, -2, 2038, 40, -3);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = dp(20);
        lp.y = dp(200);
        FrameLayout wrap = new FrameLayout(this);
        this.ballText = new TextView(this);
        this.ballText.setTextSize(14.0f);
        this.ballText.setTypeface(Typeface.DEFAULT_BOLD);
        this.ballText.setTextColor(-1);
        this.ballText.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(C_BALL_OFF);
        bg.setStroke(dp(1), 0x40FFFFFF);
        this.ballText.setBackground(bg);
        wrap.addView(this.ballText, new FrameLayout.LayoutParams(dp(56), dp(56)));
        this.ball = wrap;
        this.ballText.setText("OFF");
        this.ball.setOnTouchListener(new View.OnTouchListener() {
            int downX;
            int downY;
            boolean moved;
            int rawX;
            int rawY;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                int action = e.getActionMasked();
                if (action == 0) {
                    this.downX = (int) e.getRawX();
                    this.downY = (int) e.getRawY();
                    this.rawX = lp.x;
                    this.rawY = lp.y;
                    this.moved = false;
                    return true;
                } else if (action == 1) {
                    if (!this.moved) {
                        FixService.this.togglePanel();
                    }
                    return true;
                } else if (action == 2) {
                    int dx = ((int) e.getRawX()) - this.downX;
                    int dy = ((int) e.getRawY()) - this.downY;
                    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                        this.moved = true;
                        lp.x = this.rawX + dx;
                        lp.y = this.rawY + dy;
                        FixService.this.wm.updateViewLayout(FixService.this.ball, lp);
                    }
                    return true;
                } else {
                    return false;
                }
            }
        });
        try {
            this.wm.addView(this.ball, lp);
        } catch (Exception e) {
            this.ball = null;
            log("!! 悬浮球创建失败: " + e);
        }
    }

    private void togglePanel() {
        if (this.panel != null) {
            try {
                this.wm.removeView(this.panel);
            } catch (Exception e) {
            }
            this.panel = null;
            this.statusText = null;
            return;
        }
        if (haveOverlayPerm()) {
            final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(dp(236), -2, 2038, 8, -3);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = dp(60);
            lp.y = dp(160);
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(C_PANEL_BG);
            bg.setCornerRadius(dp(14));
            bg.setStroke(dp(1), C_PANEL_STROKE);
            box.setBackground(bg);
            int p = dp(10);
            box.setPadding(p, p, p, p);
            this.statusText = new TextView(this);
            this.statusText.setTextColor(C_PANEL_TEXT);
            this.statusText.setTextSize(12.0f);
            this.statusText.setTypeface(Typeface.DEFAULT_BOLD);
            this.statusText.setPadding(dp(4), 0, dp(4), dp(8));
            this.statusText.setOnTouchListener(new View.OnTouchListener() {
                int downX;
                int downY;
                int rawX;
                int rawY;

                @Override
                public boolean onTouch(View v, MotionEvent e) {
                    int action = e.getActionMasked();
                    if (action == 0) {
                        this.downX = (int) e.getRawX();
                        this.downY = (int) e.getRawY();
                        this.rawX = lp.x;
                        this.rawY = lp.y;
                        return true;
                    } else if (action == 2) {
                        lp.x = this.rawX + (((int) e.getRawX()) - this.downX);
                        lp.y = this.rawY + (((int) e.getRawY()) - this.downY);
                        try {
                            FixService.this.wm.updateViewLayout(FixService.this.panel, lp);
                        } catch (Exception e2) {
                        }
                        return true;
                    } else {
                        return false;
                    }
                }
            });
            box.addView(this.statusText, new LinearLayout.LayoutParams(-1, -2));
            this.bStart = panelButton("开始", C_GREEN);
            this.bStart.setOnClickListener(v -> {
                if (FixService.this.device != null && !running) {
                    startGrab();
                }
                refreshPanel();
            });
            this.bStop = panelButton("停止", C_RED);
            this.bStop.setOnClickListener(v -> {
                stopGrab();
                refreshPanel();
            });
            box.addView(twoButtonRow(this.bStart, this.bStop), twoRowLp(0));
            this.bMatrix = panelButton("矩阵 M" + this.matrix, C_BLUE);
            this.bMatrix.setOnClickListener(v -> {
                int next = (this.matrix + 1) % 7;
                Intent it = new Intent(this, FixService.class);
                it.setAction(ACT_MATRIX);
                it.putExtra(EXTRA_MATRIX, next);
                startService(it);
                this.matrix = next;
                refreshPanel();
            });
            Button bOpen = panelButton("主界面", C_NEUTRAL);
            bOpen.setOnClickListener(v -> {
                Intent it = new Intent(this, MainActivity.class);
                it.addFlags(268435456);
                startActivity(it);
            });
            Button bKill = panelButton("退出", C_NEUTRAL);
            bKill.setOnClickListener(v -> {
                stopGrab();
                Intent it = new Intent(this, FixService.class);
                it.setAction(ACT_KILL);
                startService(it);
            });
            box.addView(threeButtonRow(this.bMatrix, bOpen, bKill), twoRowLp(1));
            this.panel = box;
            try {
                this.wm.addView(this.panel, lp);
                refreshPanel();
            } catch (Exception e) {
                this.panel = null;
                this.statusText = null;
                log("!! 悬浮面板创建失败: " + e);
            }
        }
    }

    private LinearLayout twoButtonRow(Button a, Button b) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(a, panelBtnLp(0));
        LinearLayout.LayoutParams lp = panelBtnLp(0);
        lp.leftMargin = dp(6);
        row.addView(b, lp);
        return row;
    }

    private LinearLayout threeButtonRow(Button a, Button b, Button c) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(a, panelBtnLp(0));
        LinearLayout.LayoutParams lpb = panelBtnLp(0);
        lpb.leftMargin = dp(6);
        row.addView(b, lpb);
        LinearLayout.LayoutParams lpc = panelBtnLp(0);
        lpc.leftMargin = dp(6);
        row.addView(c, lpc);
        return row;
    }

    private LinearLayout.LayoutParams panelBtnLp(int weight) {
        return new LinearLayout.LayoutParams(0, dp(40), 1.0f);
    }

    private LinearLayout.LayoutParams twoRowLp(int which) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(which == 0 ? 2 : 6);
        return lp;
    }

    private Button panelButton(String text, int bgColor) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(11.0f);
        b.setTextColor(-1);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(0, 0, 0, 0);
        b.setMinimumWidth(0);
        b.setMinimumHeight(0);
        b.setStateListAnimator(null);
        GradientDrawable g = new GradientDrawable();
        g.setColor(bgColor);
        g.setCornerRadius(dp(10));
        b.setBackground(g);
        return b;
    }

    private void refreshPanel() {
        if (this.statusText == null) {
            return;
        }
        if (running) {
            this.statusText.setText("运行中 · " + shortDev(this.device) + " · 矩阵" + this.matrix);
        } else {
            this.statusText.setText("已停止" + (lastError != null ? " · " + lastError : ""));
        }
        if (this.bMatrix != null) {
            this.bMatrix.setText("矩阵 M" + this.matrix);
        }
        if (this.bStart != null) {
            this.bStart.setEnabled(!running);
            this.bStart.setAlpha(running ? 0.4f : 1.0f);
        }
        if (this.bStop != null) {
            this.bStop.setEnabled(running);
            this.bStop.setAlpha(running ? 1.0f : 0.4f);
        }
    }

    private void updateBall() {
        this.main.post(() -> {
            if (this.ballText != null) {
                boolean run = running;
                this.ballText.setText(run ? "ON" : "OFF");
                GradientDrawable bg = (GradientDrawable) this.ballText.getBackground();
                if (bg != null) {
                    bg.setColor(run ? C_BALL_ON : C_BALL_OFF);
                    this.ballText.setBackground(bg);
                }
            }
            refreshPanel();
        });
    }

    private void removeOverlays() {
        try {
            if (this.ball != null) {
                this.wm.removeView(this.ball);
            }
        } catch (Exception e) {
        }
        try {
            if (this.panel != null) {
                this.wm.removeView(this.panel);
            }
        } catch (Exception e2) {
        }
        this.ball = null;
        this.panel = null;
        this.statusText = null;
        this.bStart = null;
        this.bStop = null;
        this.bMatrix = null;
    }

    private Notification buildNotification(String title, String text) {
        NotificationManager nm = (NotificationManager) getSystemService("notification");
        if (nm.getNotificationChannel(CH) == null) {
            NotificationChannel c = new NotificationChannel(CH, "EvFix", 2);
            nm.createNotificationChannel(c);
        }
        Intent oi = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, oi, 67108864);
        Notification.Builder b = new Notification.Builder(this, CH).setSmallIcon(android.R.drawable.ic_menu_manage).setContentTitle(title).setContentText(text).setContentIntent(pi).setOngoing(true);
        return b.build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService("notification");
        nm.notify(1, buildNotification(text.contains("运行中") ? text : "EvFix 待命", text));
    }

    private void log(final String s) {
        this.main.post(() -> {
            String ts = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
            synchronized (sharedLog) {
                sharedLog.append(ts).append(' ').append(s).append('\n');
                if (sharedLog.length() > 60000) {
                    sharedLog.delete(0, sharedLog.length() - 40000);
                }
            }
        });
    }

    private String shortDev(String d) {
        return d == null ? "?" : d.replace("/dev/input/", "");
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopGrab();
        removeOverlays();
        stopForeground(true);
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        stopGrab();
        removeOverlays();
        super.onDestroy();
    }
}

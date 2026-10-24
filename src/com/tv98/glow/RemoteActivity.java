package com.tv98.glow;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.nayuki.qrcodegen.QrCode;

/** Shows how to connect a phone: QR code, address and pairing code. Opening it switches the remote on. */
public class RemoteActivity extends Activity {
    private TextView status, codeView;
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            if (!Remote.running()) {
                status.setText("The phone remote is off.");
                return;
            }
            codeView.setText(Remote.code());
            int n = Remote.phones();
            status.setText(n == 0 ? "Waiting for a phone…   It switches itself off after 30 minutes unused."
                    : n + (n == 1 ? " phone connected" : " phones connected") + "   ·   switches off after 30 minutes unused");
            status.postDelayed(this, 1500);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new Ui.Aurora());

        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Ui.glass(Ui.dp(24), false));
        card.setPadding(Ui.dp(30), Ui.dp(28), Ui.dp(36), Ui.dp(28));
        root.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        ImageView qr = new ImageView(this);
        card.addView(qr, new LinearLayout.LayoutParams(Ui.dp(200), Ui.dp(200)));

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(Ui.dp(32), 0, 0, 0);
        card.addView(words, new LinearLayout.LayoutParams(Ui.dp(400), ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = Ui.text(this, 26, Ui.TEXT, 300);
        title.setText("Phone remote");
        words.addView(title);
        TextView how = Ui.text(this, 14, Ui.TEXT_DIM, 400);
        how.setLineSpacing(0, 1.25f);
        how.setPadding(0, Ui.dp(10), 0, Ui.dp(14));
        words.addView(how);
        TextView codeLabel = Ui.label(this, "Pairing code");
        codeLabel.setPadding(0, 0, 0, Ui.dp(4));
        words.addView(codeLabel);
        codeView = Ui.text(this, 44, Ui.ACCENT, 300);
        codeView.setLetterSpacing(0.25f);
        words.addView(codeView);
        status = Ui.text(this, 12, Ui.TEXT_DIM, 400);
        status.setPadding(0, Ui.dp(12), 0, Ui.dp(16));
        words.addView(status);

        Button off = new Button(this);
        off.setText("Turn off");
        off.setAllCaps(false);
        off.setTextColor(Ui.TEXT);
        off.setStateListAnimator(null);
        android.graphics.drawable.StateListDrawable bg = new android.graphics.drawable.StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused}, Ui.glass(Ui.dp(22), true));
        bg.addState(new int[] {}, Ui.glass(Ui.dp(22), false));
        off.setBackground(bg);
        off.setPadding(Ui.dp(26), Ui.dp(8), Ui.dp(26), Ui.dp(8));
        off.setOnClickListener(v -> {
            Remote.stop();
            finish();
        });
        words.addView(off, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);

        String url = Remote.start(this);
        if (url == null) {
            how.setText("The box isn't connected to a network, so a phone can't reach it.");
            codeView.setText("—");
        } else {
            how.setText("Scan with your phone's camera, or open\n" + url + "\non a phone on the same Wi-Fi. Type, search and pick channels from there.");
            qr.setImageBitmap(qrBitmap(url, Ui.dp(200)));
        }
        off.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh.run();
    }

    @Override
    protected void onPause() {
        super.onPause();
        status.removeCallbacks(refresh);
    }

    /** White-on-dark would confuse some phone cameras, so: dark modules on a light rounded tile. */
    static Bitmap qrBitmap(String text, int sizePx) {
        QrCode qr = QrCode.encodeText(text, QrCode.Ecc.MEDIUM);
        int border = 2, n = qr.size + border * 2;
        int scale = Math.max(1, sizePx / n);
        Bitmap bmp = Bitmap.createBitmap(n * scale, n * scale, Bitmap.Config.RGB_565);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                boolean dark = qr.getModule(x - border, y - border);
                int color = dark ? 0xFF0B0E1C : 0xFFEEF1FF;
                for (int dy = 0; dy < scale; dy++) for (int dx = 0; dx < scale; dx++) bmp.setPixel(x * scale + dx, y * scale + dy, color);
            }
        }
        return bmp;
    }
}

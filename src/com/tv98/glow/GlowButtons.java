package com.tv98.glow;

import android.accessibilityservice.AccessibilityService;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * "Glow remote buttons": the viewer's button jobs in every app. Only buttons set in Glow ▸ Settings ▸ Remote buttons
 * are touched (everything else passes straight through), and the screen's contents are never read.
 */
public class GlowButtons extends AccessibilityService {
    private long downAt;
    private boolean heldDone;

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        if (!Buttons.mapped(this, code)) return false;
        String hold = Buttons.action(this, code, true);
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() == 0) {
                downAt = e.getEventTime();
                heldDone = false;
            } else if (!heldDone && hold != null && e.getEventTime() - downAt > 600) {
                heldDone = true;
                Buttons.run(this, hold);
            }
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_UP) {
            if (!heldDone) {
                if (hold != null && e.getEventTime() - downAt > 600) Buttons.run(this, hold);
                else Buttons.run(this, Buttons.action(this, code, false));
            }
            heldDone = false;
            return true;
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}

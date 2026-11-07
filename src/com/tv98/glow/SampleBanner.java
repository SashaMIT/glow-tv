package com.tv98.glow;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Shows a score banner on demand, for filming the demo videos. Not exported: only root on the box can send it. */
public class SampleBanner extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Overlay.show(c, i.getStringExtra("badge"), i.getStringExtra("headline"), i.getStringExtra("line"), 0xFF3D195B);
    }
}

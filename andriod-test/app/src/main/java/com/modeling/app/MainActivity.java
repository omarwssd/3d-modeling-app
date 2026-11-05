package com.modeling.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout workspace = new FrameLayout(this);
        workspace.setBackgroundColor(Color.rgb(25, 25, 28));

        TextView title = new TextView(this);
        title.setText("3D Modeling Workspace");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setPadding(25, 20, 0, 0);

        workspace.addView(title);

        setContentView(workspace);

        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }
}

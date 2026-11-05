
package com.modeling.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.rgb(25, 25, 28));
        getWindow().setNavigationBarColor(Color.rgb(25, 25, 28));

        WorkspaceView workspace = new WorkspaceView(this);
        setContentView(workspace);

        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    public static class WorkspaceView extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private float yaw = 0.8f;
        private float pitch = 0.45f;
        private float cameraDistance = 8.0f;

        private int screenWidth;
        private int screenHeight;

        private static class Point3 {
            float x, y, z;

            Point3(float x, float y, float z) {
                this.x = x;
                this.y = y;
                this.z = z;
            }
        }

        private static class Point2 {
            float x, y;

            Point2(float x, float y) {
                this.x = x;
                this.y = y;
            }
        }

        public WorkspaceView(Activity activity) {
            super(activity);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            screenWidth = getWidth();
            screenHeight = getHeight();

            canvas.drawColor(Color.rgb(25, 25, 28));

            drawMenu(canvas);
            drawGrid(canvas);
            drawCube(canvas);
            drawStatusBar(canvas);
        }

        private void drawMenu(Canvas canvas) {

            paint.setColor(Color.rgb(34, 34, 38));
            canvas.drawRect(0, 0, screenWidth, 48, paint);

            paint.setColor(Color.rgb(65, 65, 70));
            canvas.drawLine(0, 48, screenWidth, 48, paint);

            paint.setTextSize(14);
            paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            paint.setColor(Color.WHITE);

            canvas.drawText("File", 18, 30, paint);
            canvas.drawText("Add", 66, 30, paint);
            canvas.drawText("Export", 112, 30, paint);
            canvas.drawText("Import", 170, 30, paint);

            paint.setColor(Color.rgb(190, 190, 195));
            paint.setTextSize(13);
            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

            String title = "3D Modeling Workspace";
            float titleWidth = paint.measureText(title);

            canvas.drawText(
                title,
                Math.max(220, screenWidth - titleWidth - 18),
                30,
                paint
            );
        }

        private Point2 project(float x, float y, float z) {

            float cy = (float) Math.cos(yaw);
            float sy = (float) Math.sin(yaw);

            float cp = (float) Math.cos(pitch);
            float sp = (float) Math.sin(pitch);

            float cameraX = x * cy - z * sy;
            float cameraZ = x * sy + z * cy;

            float depth = cameraZ * cp - y * sp;
            float vertical = cameraZ * sp + y * cp;

            float distance = cameraDistance + depth;

            if (distance < 0.5f) {
                distance = 0.5f;
            }

            float focal = Math.min(screenWidth, screenHeight) * 0.85f;

            float px = screenWidth * 0.5f +
                (cameraX * focal / distance);

            float py = screenHeight * 0.56f -
                (vertical * focal / distance);

            return new Point2(px, py);
        }

        private void drawGrid(Canvas canvas) {

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f);

            for (int i = -20; i <= 20; i++) {

                int color = (i == 0)
                    ? Color.rgb(115, 115, 120)
                    : Color.rgb(65, 65, 70);

                paint.setColor(color);

                Point2 a = project(i, -1.0f, -20);
                Point2 b = project(i, -1.0f, 20);

                canvas.drawLine(a.x, a.y, b.x, b.y, paint);

                Point2 c = project(-20, -1.0f, i);
                Point2 d = project(20, -1.0f, i);

                canvas.drawLine(c.x, c.y, d.x, d.y, paint);
            }

            paint.setStyle(Paint.Style.FILL);
        }

        private void drawCube(Canvas canvas) {

            Point3[] vertices = {
                new Point3(-1, -1, -1),
                new Point3( 1, -1, -1),
                new Point3( 1,  1, -1),
                new Point3(-1,  1, -1),

                new Point3(-1, -1,  1),
                new Point3( 1, -1,  1),
                new Point3( 1,  1,  1),
                new Point3(-1,  1,  1)
            };

            Point2[] projected = new Point2[8];

            for (int i = 0; i < vertices.length; i++) {
                projected[i] = project(
                    vertices[i].x,
                    vertices[i].y,
                    vertices[i].z
                );
            }

            int[][] faces = {
                {0, 1, 2, 3},
                {4, 5, 6, 7},
                {0, 4, 7, 3},
                {1, 5, 6, 2},
                {3, 2, 6, 7},
                {0, 1, 5, 4}
            };

            int[] shades = {
                Color.rgb(105, 105, 110),
                Color.rgb(145, 145, 150),
                Color.rgb(85, 85, 90),
                Color.rgb(115, 115, 120),
                Color.rgb(155, 155, 160),
                Color.rgb(75, 75, 80)
            };

            paint.setStyle(Paint.Style.FILL);

            for (int i = 0; i < faces.length; i++) {

                Path path = new Path();

                int[] face = faces[i];

                path.moveTo(
                    projected[face[0]].x,
                    projected[face[0]].y
                );

                for (int j = 1; j < face.length; j++) {
                    path.lineTo(
                        projected[face[j]].x,
                        projected[face[j]].y
                    );
                }

                path.close();

                paint.setColor(shades[i]);
                canvas.drawPath(path, paint);

                paint.setColor(Color.rgb(45, 45, 48));
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1.5f);

                canvas.drawPath(path, paint);

                paint.setStyle(Paint.Style.FILL);
            }
        }

        private void drawStatusBar(Canvas canvas) {

            paint.setColor(Color.rgb(34, 34, 38));
            canvas.drawRect(
                0,
                screenHeight - 28,
                screenWidth,
                screenHeight,
                paint
            );

            paint.setColor(Color.rgb(165, 165, 170));
            paint.setTextSize(11);

            canvas.drawText(
                "3D Modeling Workspace",
                12,
                screenHeight - 10,
                paint
            );

            paint.setTextAlign(Paint.Align.RIGHT);

            canvas.drawText(
                "Perspective View",
                screenWidth - 12,
                screenHeight - 10,
                paint
            );

            paint.setTextAlign(Paint.Align.LEFT);
        }
    }
}

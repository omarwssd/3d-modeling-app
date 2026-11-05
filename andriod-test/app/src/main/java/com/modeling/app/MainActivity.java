
package com.modeling.app;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.opengl.GLSurfaceView;
import android.opengl.GLES20;
import android.opengl.Matrix;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Scanner;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends Activity {

    private static final int REQUEST_SAVE = 100;
    private static final int REQUEST_OPEN = 101;

    private GLSurfaceView glView;
    private SceneRenderer renderer;
    private MenuOverlay menu;

    private float previousX;
    private float previousY;
    private float previousPinchDistance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.rgb(19, 19, 19));
        getWindow().setNavigationBarColor(Color.rgb(19, 19, 19));

        FrameLayout root = new FrameLayout(this);

        glView = new GLSurfaceView(this);
        glView.setEGLContextClientVersion(2);

        renderer = new SceneRenderer();
        glView.setRenderer(renderer);
        glView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        root.addView(glView);

        menu = new MenuOverlay(this);
        root.addView(menu);

        glView.setOnTouchListener((view, event) -> handleSceneTouch(event));

        setContentView(root);
        hideSystemUI();
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    private boolean handleSceneTouch(MotionEvent event) {

        int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            previousX = event.getX();
            previousY = event.getY();
            return true;
        }

        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            if (event.getPointerCount() >= 2) {
                previousPinchDistance = getPinchDistance(event);
            }
            return true;
        }

        if (action == MotionEvent.ACTION_MOVE) {

            if (event.getPointerCount() >= 2) {

                float distance = getPinchDistance(event);

                if (previousPinchDistance > 0) {
                    float difference = distance - previousPinchDistance;
                    renderer.zoomCamera(difference * 0.015f);
                }

                previousPinchDistance = distance;
            }
            else {

                float x = event.getX();
                float y = event.getY();

                float dx = x - previousX;
                float dy = y - previousY;

                renderer.rotateCamera(dx * 0.005f, dy * 0.005f);

                previousX = x;
                previousY = y;
            }

            return true;
        }

        if (action == MotionEvent.ACTION_POINTER_UP) {
            previousPinchDistance = 0;
            return true;
        }

        if (action == MotionEvent.ACTION_UP ||
            action == MotionEvent.ACTION_CANCEL) {
            previousPinchDistance = 0;
            return true;
        }

        return true;
    }

    private float getPinchDistance(MotionEvent event) {
        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    // ==================================================
    // ANDROID FILE PICKER
    // ==================================================

    private void saveProject() {

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, "Project.Ac");

        startActivityForResult(intent, REQUEST_SAVE);
    }

    private void openProject() {

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");

        startActivityForResult(intent, REQUEST_OPEN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null || data.getData() == null)
            return;

        Uri uri = data.getData();

        try {
            if (requestCode == REQUEST_SAVE) {
                writeProject(uri);
            }
            else if (requestCode == REQUEST_OPEN) {
                readProject(uri);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void writeProject(Uri uri) throws Exception {

        String project = renderer.createProjectData();

        OutputStream output = getContentResolver().openOutputStream(uri);

        if (output == null)
            throw new Exception("Could not create project file.");

        BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(output)
        );

        writer.write(project);
        writer.flush();
        writer.close();
    }

    private void readProject(Uri uri) throws Exception {

        BufferedReader reader = new BufferedReader(
            new InputStreamReader(
                getContentResolver().openInputStream(uri)
            )
        );

        StringBuilder content = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            content.append(line).append("\n");
        }

        reader.close();

        renderer.loadProjectData(content.toString());
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (glView != null)
            glView.onResume();

        hideSystemUI();
    }

    @Override
    protected void onPause() {
        if (glView != null)
            glView.onPause();

        super.onPause();
    }

    // ==================================================
    // MENU OVERLAY
    // ==================================================

    public class MenuOverlay extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private int activeMenu = 0;

        public MenuOverlay(Activity activity) {
            super(activity);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float scale = getWidth() / 900.0f;

            canvas.save();
            canvas.scale(scale, scale);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(20, 20, 20));
            canvas.drawRect(0, 0, 900, 30, paint);

            drawButton(canvas, 10, 48, "File", activeMenu == 1);
            drawButton(canvas, 62, 48, "Edit", false);
            drawButton(canvas, 114, 65, "Select", false);
            drawButton(canvas, 183, 48, "Add", activeMenu == 2);

            paint.setColor(Color.rgb(89, 89, 89));
            paint.setStrokeWidth(1);

            canvas.drawLine(58, 6, 58, 24, paint);
            canvas.drawLine(110, 6, 110, 24, paint);
            canvas.drawLine(179, 6, 179, 24, paint);

            if (activeMenu == 1) {
                drawDropdown(canvas, 10, 165,
                    new String[]{"Save", "Open", "Export", "Import"});
            }

            if (activeMenu == 2) {
                drawDropdown(canvas, 183, 125,
                    new String[]{"Cube", "Cylinder", "Sphere"});
            }

            canvas.restore();
        }

        private void drawButton(
            Canvas canvas,
            float x,
            float width,
            String text,
            boolean selected
        ) {
            paint.setColor(selected ?
                Color.rgb(64, 64, 64) :
                Color.rgb(31, 31, 31));

            canvas.drawRect(x, 0, x + width, 30, paint);

            paint.setColor(Color.rgb(217, 217, 217));
            paint.setTextSize(16);
            paint.setTypeface(Typeface.create("Arial", Typeface.NORMAL));

            canvas.drawText(text, x + 8, 19, paint);
        }

        private void drawDropdown(
            Canvas canvas,
            float x,
            float width,
            String[] items
        ) {
            for (int i = 0; i < items.length; i++) {

                float y = 30 + i * 30;

                paint.setColor(Color.rgb(41, 41, 41));
                canvas.drawRect(x, y, x + width, y + 30, paint);

                paint.setColor(Color.rgb(230, 230, 230));
                paint.setTextSize(16);

                canvas.drawText(items[i], x + 10, y + 20, paint);
            }

            paint.setColor(Color.rgb(89, 89, 89));
            paint.setStyle(Paint.Style.STROKE);

            canvas.drawRect(
                x, 30, x + width, 30 + items.length * 30, paint
            );

            paint.setStyle(Paint.Style.FILL);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {

            if (event.getAction() != MotionEvent.ACTION_DOWN)
                return activeMenu != 0;

            float scale = getWidth() / 900.0f;

            float x = event.getX() / scale;
            float y = event.getY() / scale;

            if (y <= 30) {

                if (x >= 10 && x <= 58) {
                    activeMenu = activeMenu == 1 ? 0 : 1;
                }
                else if (x >= 183 && x <= 231) {
                    activeMenu = activeMenu == 2 ? 0 : 2;
                }
                else {
                    activeMenu = 0;
                }

                invalidate();
                return true;
            }

            if (activeMenu == 1 && x >= 10 && x <= 175) {

                int item = (int)((y - 30) / 30);

                activeMenu = 0;
                invalidate();

                switch (item) {
                    case 0:
                        saveProject();
                        break;

                    case 1:
                        openProject();
                        break;

                    case 2:
                    case 3:
                        break;
                }

                return true;
            }

            if (activeMenu == 2 && x >= 183 && x <= 308) {

                int item = (int)((y - 30) / 30);

                activeMenu = 0;
                invalidate();

                if (item == 0)
                    renderer.addObject(0);

                if (item == 1)
                    renderer.addObject(1);

                if (item == 2)
                    renderer.addObject(2);

                return true;
            }

            activeMenu = 0;
            invalidate();

            return true;
        }
    }

    // ==================================================
    // SCENE OBJECT
    // ==================================================

    public static class SceneObject {

        int type;

        float x;
        float y;
        float z;

        SceneObject(int type, float x, float y, float z) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    // ==================================================
    // OPENGL ES 2.0 RENDERER
    // ==================================================

    public static class SceneRenderer implements GLSurfaceView.Renderer {

        private final ArrayList<SceneObject> objects = new ArrayList<>();

        private int program;
        private int positionHandle;
        private int matrixHandle;
        private int colorHandle;

        private FloatBuffer gridBuffer;
        private FloatBuffer cubeBuffer;
        private FloatBuffer cylinderBuffer;
        private FloatBuffer sphereBuffer;

        private int cylinderVertexCount;
        private int sphereVertexCount;

        private final float[] projection = new float[16];
        private final float[] view = new float[16];
        private final float[] model = new float[16];
        private final float[] mvp = new float[16];

        private float cameraYaw = 0.8f;
        private float cameraPitch = 0.4f;
        private float cameraDistance = 8.0f;

        private static final String VERTEX_SHADER =
            "uniform mat4 uMatrix;" +
            "attribute vec3 aPosition;" +
            "void main() {" +
            "gl_Position = uMatrix * vec4(aPosition, 1.0);" +
            "}";

        private static final String FRAGMENT_SHADER =
            "precision mediump float;" +
            "uniform vec4 uColor;" +
            "void main() {" +
            "gl_FragColor = uColor;" +
            "}";

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {

            GLES20.glClearColor(0.075f, 0.075f, 0.075f, 1.0f);

            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glDisable(GLES20.GL_CULL_FACE);

            program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);

            positionHandle = GLES20.glGetAttribLocation(program, "aPosition");
            matrixHandle = GLES20.glGetUniformLocation(program, "uMatrix");
            colorHandle = GLES20.glGetUniformLocation(program, "uColor");

            createGrid();
            createCube();
            createCylinder();
            createSphere();
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {

            GLES20.glViewport(0, 0, width, Math.max(height, 1));

            Matrix.perspectiveM(
                projection, 0,
                45.0f,
                (float) width / Math.max(height, 1),
                0.1f,
                100.0f
            );
        }

        @Override
        public void onDrawFrame(GL10 gl) {

            GLES20.glClear(
                GLES20.GL_COLOR_BUFFER_BIT |
                GLES20.GL_DEPTH_BUFFER_BIT
            );

            GLES20.glUseProgram(program);

            Matrix.setIdentityM(view, 0);

            Matrix.translateM(view, 0, 0, 0, -cameraDistance);

            Matrix.rotateM(
                view, 0,
                (float)Math.toDegrees(cameraPitch),
                1, 0, 0
            );

            Matrix.rotateM(
                view, 0,
                (float)Math.toDegrees(cameraYaw),
                0, 1, 0
            );

            Matrix.multiplyMM(mvp, 0, projection, 0, view, 0);

            drawGrid();
            drawObjects();
        }

        // ==================================================
        // CAMERA
        // ==================================================

        public synchronized void rotateCamera(float dx, float dy) {

            cameraYaw += dx;
            cameraPitch += dy;

            cameraPitch = Math.max(-1.5f, Math.min(1.5f, cameraPitch));
        }

        public synchronized void zoomCamera(float amount) {

            cameraDistance -= amount;

            cameraDistance = Math.max(
                2.0f,
                Math.min(30.0f, cameraDistance)
            );
        }

        // ==================================================
        // OBJECT MANAGEMENT
        // ==================================================

        public synchronized void addObject(int type) {

            if (type < 0 || type > 2)
                return;

            objects.add(new SceneObject(type, 0, 0, 0));
        }

        private void drawObjects() {

            synchronized (this) {

                for (SceneObject object : objects) {

                    Matrix.setIdentityM(model, 0);

                    Matrix.translateM(
                        model, 0,
                        object.x, object.y, object.z
                    );

                    float[] objectMVP = new float[16];

                    Matrix.multiplyMM(
                        objectMVP, 0,
                        mvp, 0,
                        model, 0
                    );

                    GLES20.glUniformMatrix4fv(
                        matrixHandle, 1, false, objectMVP, 0
                    );

                    GLES20.glUniform4f(
                        colorHandle,
                        0.65f, 0.65f, 0.65f, 1.0f
                    );

                    FloatBuffer buffer;
                    int count;

                    if (object.type == 0) {
                        buffer = cubeBuffer;
                        count = cubeBuffer.limit() / 3;
                    }
                    else if (object.type == 1) {
                        buffer = cylinderBuffer;
                        count = cylinderVertexCount;
                    }
                    else {
                        buffer = sphereBuffer;
                        count = sphereVertexCount;
                    }

                    drawBuffer(
                        buffer,
                        GLES20.GL_TRIANGLES,
                        count
                    );
                }
            }
        }

        // ==================================================
        // GRID
        // ==================================================

        private void createGrid() {

            float[] vertices = new float[41 * 12];

            int index = 0;

            for (int i = -20; i <= 20; i++) {

                vertices[index++] = i;
                vertices[index++] = -1.01f;
                vertices[index++] = -20;

                vertices[index++] = i;
                vertices[index++] = -1.01f;
                vertices[index++] = 20;

                vertices[index++] = -20;
                vertices[index++] = -1.01f;
                vertices[index++] = i;

                vertices[index++] = 20;
                vertices[index++] = -1.01f;
                vertices[index++] = i;
            }

            gridBuffer = createBuffer(vertices);
        }

        private void drawGrid() {

            GLES20.glUniformMatrix4fv(
                matrixHandle, 1, false, mvp, 0
            );

            GLES20.glEnableVertexAttribArray(positionHandle);

            gridBuffer.position(0);

            GLES20.glVertexAttribPointer(
                positionHandle, 3,
                GLES20.GL_FLOAT, false, 12, gridBuffer
            );

            for (int i = -20; i <= 20; i++) {

                float shade = i == 0 ? 0.45f : 0.22f;

                GLES20.glUniform4f(
                    colorHandle, shade, shade, shade, 1.0f
                );

                GLES20.glDrawArrays(
                    GLES20.GL_LINES,
                    (i + 20) * 4,
                    4
                );
            }

            GLES20.glDisableVertexAttribArray(positionHandle);
        }

        // ==================================================
        // CUBE
        // ==================================================

        private void createCube() {

            float s = 0.75f;

            float[] vertices = {
                // Front
                -s,-s,s,  s,-s,s,  s,s,s,
                -s,-s,s,  s,s,s,  -s,s,s,

                // Back
                -s,-s,-s, -s,s,-s, s,s,-s,
                -s,-s,-s, s,s,-s, s,-s,-s,

                // Left
                -s,-s,-s, -s,-s,s, -s,s,s,
                -s,-s,-s, -s,s,s, -s,s,-s,

                // Right
                s,-s,-s, s,s,-s, s,s,s,
                s,-s,-s, s,s,s, s,-s,s,

                // Top
                -s,s,-s, -s,s,s, s,s,s,
                -s,s,-s, s,s,s, s,s,-s,

                // Bottom
                -s,-s,-s, s,-s,-s, s,-s,s,
                -s,-s,-s, s,-s,s, -s,-s,s
            };

            cubeBuffer = createBuffer(vertices);
        }

        // ==================================================
        // CYLINDER
        // ==================================================

        private void createCylinder() {

            ArrayList<Float> data = new ArrayList<>();

            float radius = 0.65f;
            float height = 1.5f;
            int segments = 32;

            for (int i = 0; i < segments; i++) {

                float a1 = (float)(2 * Math.PI * i / segments);
                float a2 = (float)(2 * Math.PI * (i + 1) / segments);

                float x1 = (float)Math.cos(a1) * radius;
                float z1 = (float)Math.sin(a1) * radius;

                float x2 = (float)Math.cos(a2) * radius;
                float z2 = (float)Math.sin(a2) * radius;

                float bottom = -height / 2;
                float top = height / 2;

                // Side
                addVertex(data, x1, bottom, z1);
                addVertex(data, x2, bottom, z2);
                addVertex(data, x2, top, z2);

                addVertex(data, x1, bottom, z1);
                addVertex(data, x2, top, z2);
                addVertex(data, x1, top, z1);

                // Top
                addVertex(data, 0, top, 0);
                addVertex(data, x1, top, z1);
                addVertex(data, x2, top, z2);

                // Bottom
                addVertex(data, 0, bottom, 0);
                addVertex(data, x2, bottom, z2);
                addVertex(data, x1, bottom, z1);
            }

            cylinderBuffer = createBuffer(toArray(data));
            cylinderVertexCount = data.size() / 3;
        }

        // ==================================================
        // SPHERE
        // ==================================================

        private void createSphere() {

            ArrayList<Float> data = new ArrayList<>();

            float radius = 0.8f;
            int rings = 20;
            int segments = 32;

            for (int i = 0; i < rings; i++) {

                float lat1 = (float)(
                    Math.PI * (-0.5 + (double)i / rings)
                );

                float lat2 = (float)(
                    Math.PI * (-0.5 + (double)(i + 1) / rings)
                );

                for (int j = 0; j < segments; j++) {

                    float lon1 = (float)(2 * Math.PI * j / segments);
                    float lon2 = (float)(2 * Math.PI * (j + 1) / segments);

                    float[] a = spherePoint(lat1, lon1, radius);
                    float[] b = spherePoint(lat2, lon1, radius);
                    float[] c = spherePoint(lat2, lon2, radius);
                    float[] d = spherePoint(lat1, lon2, radius);

                    addVertex(data, a[0], a[1], a[2]);
                    addVertex(data, b[0], b[1], b[2]);
                    addVertex(data, c[0], c[1], c[2]);

                    addVertex(data, a[0], a[1], a[2]);
                    addVertex(data, c[0], c[1], c[2]);
                    addVertex(data, d[0], d[1], d[2]);
                }
            }

            sphereBuffer = createBuffer(toArray(data));
            sphereVertexCount = data.size() / 3;
        }

        private float[] spherePoint(float lat, float lon, float radius) {

            return new float[] {
                (float)(Math.cos(lat) * Math.cos(lon) * radius),
                (float)(Math.sin(lat) * radius),
                (float)(Math.cos(lat) * Math.sin(lon) * radius)
            };
        }

        // ==================================================
        // PROJECT SAVE / LOAD
        // Same format as original Windows main.cpp
        // ==================================================

        public synchronized String createProjectData() {

            StringBuilder data = new StringBuilder();

            data.append("AC_PROJECT 1\n");

            data.append(String.format(
                Locale.US,
                "%.9g %.9g %.9g\n",
                cameraYaw,
                cameraPitch,
                cameraDistance
            ));

            data.append(objects.size()).append("\n");

            for (SceneObject object : objects) {

                data.append(String.format(
                    Locale.US,
                    "%d %.9g %.9g %.9g\n",
                    object.type,
                    object.x,
                    object.y,
                    object.z
                ));
            }

            return data.toString();
        }

        public synchronized void loadProjectData(String content)
            throws Exception {

            Scanner scanner = new Scanner(content);
            scanner.useLocale(Locale.US);

            String header = scanner.next();

            int version = scanner.nextInt();

            if (!header.equals("AC_PROJECT") || version != 1) {
                scanner.close();
                throw new Exception("Invalid or unsupported project file.");
            }

            float loadedYaw = scanner.nextFloat();
            float loadedPitch = scanner.nextFloat();
            float loadedDistance = scanner.nextFloat();

            if (!Float.isFinite(loadedYaw) ||
                !Float.isFinite(loadedPitch) ||
                !Float.isFinite(loadedDistance) ||
                loadedPitch < -1.5f ||
                loadedPitch > 1.5f ||
                loadedDistance < 2.0f ||
                loadedDistance > 30.0f) {

                scanner.close();
                throw new Exception("Invalid camera data.");
            }

            int count = scanner.nextInt();

            if (count < 0 || count > 100000) {
                scanner.close();
                throw new Exception("Invalid object count.");
            }

            ArrayList<SceneObject> loadedObjects = new ArrayList<>();

            for (int i = 0; i < count; i++) {

                int type = scanner.nextInt();

                float x = scanner.nextFloat();
                float y = scanner.nextFloat();
                float z = scanner.nextFloat();

                if (type < 0 || type > 2 ||
                    !Float.isFinite(x) ||
                    !Float.isFinite(y) ||
                    !Float.isFinite(z)) {

                    scanner.close();
                    throw new Exception("Invalid object data.");
                }

                loadedObjects.add(new SceneObject(type, x, y, z));
            }

            scanner.close();

            // Replace scene only after complete successful validation.
            objects.clear();
            objects.addAll(loadedObjects);

            cameraYaw = loadedYaw;
            cameraPitch = loadedPitch;
            cameraDistance = loadedDistance;
        }

        // ==================================================
        // BUFFER HELPERS
        // ==================================================

        private void drawBuffer(
            FloatBuffer buffer,
            int mode,
            int count
        ) {
            GLES20.glEnableVertexAttribArray(positionHandle);

            buffer.position(0);

            GLES20.glVertexAttribPointer(
                positionHandle,
                3,
                GLES20.GL_FLOAT,
                false,
                12,
                buffer
            );

            GLES20.glDrawArrays(mode, 0, count);

            GLES20.glDisableVertexAttribArray(positionHandle);
        }

        private FloatBuffer createBuffer(float[] data) {

            ByteBuffer bb = ByteBuffer.allocateDirect(data.length * 4);
            bb.order(ByteOrder.nativeOrder());

            FloatBuffer buffer = bb.asFloatBuffer();
            buffer.put(data);
            buffer.position(0);

            return buffer;
        }

        private void addVertex(
            ArrayList<Float> data,
            float x,
            float y,
            float z
        ) {
            data.add(x);
            data.add(y);
            data.add(z);
        }

        private float[] toArray(ArrayList<Float> list) {

            float[] result = new float[list.size()];

            for (int i = 0; i < list.size(); i++)
                result[i] = list.get(i);

            return result;
        }

        // ==================================================
        // SHADERS
        // ==================================================

        private int compileShader(int type, String source) {

            int shader = GLES20.glCreateShader(type);

            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);

            int[] status = new int[1];

            GLES20.glGetShaderiv(
                shader,
                GLES20.GL_COMPILE_STATUS,
                status,
                0
            );

            if (status[0] == 0) {

                String error = GLES20.glGetShaderInfoLog(shader);

                GLES20.glDeleteShader(shader);

                throw new RuntimeException(error);
            }

            return shader;
        }

        private int createProgram(String vertexSource, String fragmentSource) {

            int vertex = compileShader(
                GLES20.GL_VERTEX_SHADER,
                vertexSource
            );

            int fragment = compileShader(
                GLES20.GL_FRAGMENT_SHADER,
                fragmentSource
            );

            int result = GLES20.glCreateProgram();

            GLES20.glAttachShader(result, vertex);
            GLES20.glAttachShader(result, fragment);

            GLES20.glLinkProgram(result);

            int[] status = new int[1];

            GLES20.glGetProgramiv(
                result,
                GLES20.GL_LINK_STATUS,
                status,
                0
            );

            if (status[0] == 0) {

                String error = GLES20.glGetProgramInfoLog(result);

                GLES20.glDeleteProgram(result);

                throw new RuntimeException(error);
            }

            GLES20.glDeleteShader(vertex);
            GLES20.glDeleteShader(fragment);

            return result;
        }
    }
}


package com.modeling.app;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Scanner;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends Activity {

    private static final int REQUEST_SAVE = 100;
    private static final int REQUEST_OPEN = 101;
    private static final int REQUEST_IMPORT = 102;
    private static final int REQUEST_EXPORT = 103;

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

        menu = new MenuOverlay();
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

    // --------------------------------------------------
    // CAMERA TOUCH
    // --------------------------------------------------

    private boolean handleSceneTouch(MotionEvent event) {
        int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            previousX = event.getX();
            previousY = event.getY();
            return true;
        }

        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            if (event.getPointerCount() >= 2) {
                previousPinchDistance = pinchDistance(event);
            }
            return true;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            if (event.getPointerCount() >= 2) {
                float distance = pinchDistance(event);

                if (previousPinchDistance > 0) {
                    float difference = distance - previousPinchDistance;
                    renderer.zoomCamera(difference * 0.015f);
                }

                previousPinchDistance = distance;
            } else {
                float x = event.getX();
                float y = event.getY();

                renderer.rotateCamera(
                    (x - previousX) * 0.005f,
                    (y - previousY) * 0.005f
                );

                previousX = x;
                previousY = y;
            }
            return true;
        }

        if (action == MotionEvent.ACTION_POINTER_UP) {
            previousPinchDistance = 0;
            previousX = event.getX(0);
            previousY = event.getY(0);
            return true;
        }

        if (action == MotionEvent.ACTION_UP ||
            action == MotionEvent.ACTION_CANCEL) {
            previousPinchDistance = 0;
            return true;
        }

        return true;
    }

    private float pinchDistance(MotionEvent event) {
        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    // --------------------------------------------------
    // ANDROID FILE PICKERS
    // --------------------------------------------------

    private void launchPicker(int requestCode, String title, String mimeType) {
        Intent intent;

        if (requestCode == REQUEST_SAVE || requestCode == REQUEST_EXPORT) {
            intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_TITLE, title);
        } else {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
        }

        intent.setType(mimeType);
        startActivityForResult(intent, requestCode);
    }

    private void saveProject() {
        launchPicker(REQUEST_SAVE, "Project.Ac", "text/plain");
    }

    private void openProject() {
        launchPicker(REQUEST_OPEN, null, "*/*");
    }

    private void importModel() {
        launchPicker(REQUEST_IMPORT, null, "*/*");
    }

    private void exportModel() {
        launchPicker(REQUEST_EXPORT, "Model.obj", "text/plain");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null || data.getData() == null)
            return;

        Uri uri = data.getData();

        try {
            if (requestCode == REQUEST_SAVE) {
                saveAcProject(uri);
                toast("Project saved.");
            } else if (requestCode == REQUEST_OPEN) {
                openAcProject(uri);
                menu.invalidate();
                toast("Project opened.");
            } else if (requestCode == REQUEST_IMPORT) {
                importObj(uri);
            } else if (requestCode == REQUEST_EXPORT) {
                exportObj(uri);
                toast("OBJ exported.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            toast("File error: " + e.getMessage());
        }
    }

    private void importObj(Uri uri) throws Exception {
        InputStream input = getContentResolver().openInputStream(uri);

        if (input == null) throw new Exception("Cannot open selected file.");

        ModelTools.MeshData mesh = ModelTools.importOBJ(input);
        input.close();

        renderer.addImportedMesh(mesh);
        menu.invalidate();

        toast("Model imported.");
    }

    private void exportObj(Uri uri) throws Exception {
        OutputStream output = getContentResolver().openOutputStream(uri);

        if (output == null) throw new Exception("Cannot create OBJ file.");

        ModelTools.exportOBJ(output, renderer.getSceneSnapshot());
        output.close();
    }

    // --------------------------------------------------
    // AC PROJECT FORMAT
    // Compatible with the original AC_PROJECT 1 format.
    // This format stores the three built-in primitive types.
    // Use OBJ to save imported/custom mesh geometry.
    // --------------------------------------------------

    private void saveAcProject(Uri uri) throws Exception {
        OutputStream output = getContentResolver().openOutputStream(uri);

        if (output == null) throw new Exception("Cannot create project file.");

        BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(output, "UTF-8")
        );

        writer.write(renderer.createAcProject());
        writer.close();
    }

    private void openAcProject(Uri uri) throws Exception {
        InputStream input = getContentResolver().openInputStream(uri);

        if (input == null) throw new Exception("Cannot open project file.");

        BufferedReader reader = new BufferedReader(
            new InputStreamReader(input, "UTF-8")
        );

        StringBuilder content = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            content.append(line).append('\n');
        }

        reader.close();
        renderer.loadAcProject(content.toString());
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (glView != null) glView.onResume();
        hideSystemUI();
    }

    @Override
    protected void onPause() {
        if (glView != null) glView.onPause();
        super.onPause();
    }

    // ==================================================
    // MENU + LEFT STATISTICS PANEL
    // ==================================================

    private class MenuOverlay extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int activeMenu = 0;

        MenuOverlay() {
            super(MainActivity.this);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        private void rect(Canvas canvas, float x, float y,
                          float w, float h, int color) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRect(x, y, x + w, y + h, paint);
        }

        private void text(Canvas canvas, String value,
                          float x, float y, float size, int color) {
            paint.setColor(color);
            paint.setTextSize(size);
            paint.setTypeface(Typeface.create("Arial", Typeface.NORMAL));
            paint.setStyle(Paint.Style.FILL);
            canvas.drawText(value, x, y, paint);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            // Keep menu readable on phone-sized screens.
            float scale = Math.max(1.0f, getWidth() / 900.0f);

            canvas.save();
            canvas.scale(scale, scale);

            rect(canvas, 0, 0, 900, 30, Color.rgb(20, 20, 20));

            drawButton(canvas, 10, 48, "File", activeMenu == 1);
            drawButton(canvas, 62, 48, "Edit", false);
            drawButton(canvas, 114, 65, "Select", false);
            drawButton(canvas, 183, 48, "Add", activeMenu == 2);

            paint.setColor(Color.rgb(89, 89, 89));
            canvas.drawLine(58, 6, 58, 24, paint);
            canvas.drawLine(110, 6, 110, 24, paint);
            canvas.drawLine(179, 6, 179, 24, paint);

            if (activeMenu == 1) {
                drawDropdown(canvas, 10, 165,
                    new String[]{"Save", "Open", "Import", "Export"});
            }

            if (activeMenu == 2) {
                drawDropdown(canvas, 183, 125,
                    new String[]{"Cube", "Cylinder", "Sphere"});
            }

            canvas.restore();

            drawStatistics(canvas);
        }

        private void drawButton(Canvas canvas, float x, float width,
                                String label, boolean selected) {
            rect(canvas, x, 0, width, 30,
                selected ? Color.rgb(64, 64, 64) : Color.rgb(31, 31, 31));

            text(canvas, label, x + 8, 20, 16,
                Color.rgb(217, 217, 217));
        }

        private void drawDropdown(Canvas canvas, float x, float width,
                                  String[] items) {
            for (int i = 0; i < items.length; i++) {
                float y = 30 + i * 30;
                rect(canvas, x, y, width, 30, Color.rgb(41, 41, 41));
                text(canvas, items[i], x + 10, y + 20, 16,
                    Color.rgb(230, 230, 230));
            }
        }

        private void drawStatistics(Canvas canvas) {
            float density = getResources().getDisplayMetrics().density;

            int[] stats = renderer.getStatistics();

            float left = 10 * density;
            float top = 48 * density;
            float width = 145 * density;
            float height = 112 * density;

            paint.setColor(Color.argb(220, 18, 18, 18));
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(
                left, top, left + width, top + height,
                8 * density, 8 * density, paint
            );

            paint.setColor(Color.rgb(220, 220, 220));
            paint.setTextSize(14 * density);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("SCENE STATS",
                left + 10 * density, top + 21 * density, paint);

            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextSize(13 * density);

            canvas.drawText("Vertices: " + stats[0],
                left + 10 * density, top + 48 * density, paint);

            canvas.drawText("Edges: " + stats[1],
                left + 10 * density, top + 72 * density, paint);

            canvas.drawText("Faces: " + stats[2],
                left + 10 * density, top + 96 * density, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() != MotionEvent.ACTION_DOWN)
                return activeMenu != 0;

            float scale = Math.max(1.0f, getWidth() / 900.0f);
            float x = event.getX() / scale;
            float y = event.getY() / scale;

            if (y <= 30) {
                if (x >= 10 && x <= 58) {
                    activeMenu = activeMenu == 1 ? 0 : 1;
                } else if (x >= 183 && x <= 231) {
                    activeMenu = activeMenu == 2 ? 0 : 2;
                } else {
                    activeMenu = 0;
                }

                invalidate();
                return true;
            }

            if (activeMenu == 1 && x >= 10 && x <= 175) {
                int item = (int) ((y - 30) / 30);
                activeMenu = 0;
                invalidate();

                switch (item) {
                    case 0: saveProject(); break;
                    case 1: openProject(); break;
                    case 2: importModel(); break;
                    case 3: exportModel(); break;
                }
                return true;
            }

            if (activeMenu == 2 && x >= 183 && x <= 308) {
                int item = (int) ((y - 30) / 30);
                activeMenu = 0;
                invalidate();

                if (item == 0) renderer.addPrimitive(0);
                if (item == 1) renderer.addPrimitive(1);
                if (item == 2) renderer.addPrimitive(2);

                return true;
            }

            if (activeMenu != 0) {
                activeMenu = 0;
                invalidate();
                return true;
            }

            return false;
        }
    }

    // ==================================================
    // OPENGL SCENE RENDERER
    // ==================================================

    public static class SceneRenderer implements GLSurfaceView.Renderer {

        private final ArrayList<ModelTools.SceneItem> scene = new ArrayList<>();
        private final HashMap<ModelTools.MeshData, FloatBuffer> meshBuffers =
            new HashMap<>();

        private int program;
        private int positionHandle;
        private int matrixHandle;
        private int colorHandle;

        private FloatBuffer gridBuffer;

        private final float[] projection = new float[16];
        private final float[] view = new float[16];
        private final float[] mvp = new float[16];
        private final float[] model = new float[16];
        private final float[] objectMvp = new float[16];

        private float cameraYaw = 0.8f;
        private float cameraPitch = 0.4f;
        private float cameraDistance = 8.0f;

        private static final String VERTEX_SHADER =
            "uniform mat4 uMatrix;" +
            "attribute vec3 aPosition;" +
            "void main(){gl_Position=uMatrix*vec4(aPosition,1.0);}";

        private static final String FRAGMENT_SHADER =
            "precision mediump float;" +
            "uniform vec4 uColor;" +
            "void main(){gl_FragColor=uColor;}";

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
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, Math.max(height, 1));

            Matrix.perspectiveM(
                projection, 0, 45.0f,
                (float) width / Math.max(height, 1),
                0.1f, 100.0f
            );
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            GLES20.glClear(
                GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT
            );

            GLES20.glUseProgram(program);

            Matrix.setIdentityM(view, 0);
            Matrix.translateM(view, 0, 0, 0, -cameraDistance);
            Matrix.rotateM(view, 0,
                (float) Math.toDegrees(cameraPitch), 1, 0, 0);
            Matrix.rotateM(view, 0,
                (float) Math.toDegrees(cameraYaw), 0, 1, 0);

            Matrix.multiplyMM(mvp, 0, projection, 0, view, 0);

            drawGrid();
            drawScene();
        }

        // --------------------------------------------------
        // CAMERA
        // --------------------------------------------------

        public synchronized void rotateCamera(float dx, float dy) {
            cameraYaw += dx;
            cameraPitch += dy;
            cameraPitch = Math.max(-1.5f, Math.min(1.5f, cameraPitch));
        }

        public synchronized void zoomCamera(float amount) {
            cameraDistance -= amount;
            cameraDistance = Math.max(2.0f, Math.min(30.0f, cameraDistance));
        }

        // --------------------------------------------------
        // OBJECTS
        // --------------------------------------------------

        public synchronized void addPrimitive(int type) {
            ModelTools.MeshData mesh;

            if (type == 0) mesh = ModelTools.createCube();
            else if (type == 1) mesh = ModelTools.createCylinder();
            else mesh = ModelTools.createSphere();

            addMesh(mesh);
        }

        public synchronized void addImportedMesh(ModelTools.MeshData mesh) {
            addMesh(mesh);
        }

        private void addMesh(ModelTools.MeshData mesh) {
            scene.add(new ModelTools.SceneItem(mesh, 0, 0, 0));
            meshBuffers.remove(mesh);
        }

        public synchronized ArrayList<ModelTools.SceneItem> getSceneSnapshot() {
            return new ArrayList<>(scene);
        }

        public synchronized int[] getStatistics() {
            int vertices = 0;
            int edges = 0;
            int faces = 0;

            for (ModelTools.SceneItem item : scene) {
                vertices += item.mesh.getVertexCount();
                edges += item.mesh.getEdgeCount();
                faces += item.mesh.getFaceCount();
            }

            return new int[]{vertices, edges, faces};
        }

        // --------------------------------------------------
        // RENDER OBJECT MESHES
        // --------------------------------------------------

        private void drawScene() {
            synchronized (this) {
                for (ModelTools.SceneItem item : scene) {
                    FloatBuffer buffer = meshBuffers.get(item.mesh);

                    if (buffer == null) {
                        buffer = createBuffer(item.mesh.getTriangleVertices());
                        meshBuffers.put(item.mesh, buffer);
                    }

                    Matrix.setIdentityM(model, 0);
                    Matrix.translateM(model, 0, item.x, item.y, item.z);

                    Matrix.multiplyMM(
                        objectMvp, 0, mvp, 0, model, 0
                    );

                    GLES20.glUniformMatrix4fv(
                        matrixHandle, 1, false, objectMvp, 0
                    );

                    GLES20.glUniform4f(
                        colorHandle, 0.65f, 0.65f, 0.65f, 1.0f
                    );

                    drawBuffer(
                        buffer,
                        buffer.limit() / 3
                    );
                }
            }
        }

        // --------------------------------------------------
        // GRID
        // --------------------------------------------------

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
            GLES20.glUniformMatrix4fv(matrixHandle, 1, false, mvp, 0);

            GLES20.glEnableVertexAttribArray(positionHandle);
            gridBuffer.position(0);

            GLES20.glVertexAttribPointer(
                positionHandle, 3, GLES20.GL_FLOAT,
                false, 12, gridBuffer
            );

            for (int i = -20; i <= 20; i++) {
                float shade = i == 0 ? 0.45f : 0.22f;

                GLES20.glUniform4f(
                    colorHandle, shade, shade, shade, 1.0f
                );

                GLES20.glDrawArrays(
                    GLES20.GL_LINES, (i + 20) * 4, 4
                );
            }

            GLES20.glDisableVertexAttribArray(positionHandle);
        }

        private void drawBuffer(FloatBuffer buffer, int count) {
            GLES20.glEnableVertexAttribArray(positionHandle);

            buffer.position(0);

            GLES20.glVertexAttribPointer(
                positionHandle, 3, GLES20.GL_FLOAT,
                false, 12, buffer
            );

            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count);

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

        // --------------------------------------------------
        // AC_PROJECT 1 SAVE / LOAD
        // Preserves compatibility with original primitive files.
        // --------------------------------------------------

        public synchronized String createAcProject() {
            StringBuilder output = new StringBuilder();

            output.append("AC_PROJECT 1\n");

            output.append(String.format(
                Locale.US, "%.9g %.9g %.9g\n",
                cameraYaw, cameraPitch, cameraDistance
            ));

            // AC v1 stores primitive type IDs only.
            ArrayList<ModelTools.SceneItem> primitives = new ArrayList<>();

            for (ModelTools.SceneItem item : scene) {
                if (item.mesh.name.equals("Cube") ||
                    item.mesh.name.equals("Cylinder") ||
                    item.mesh.name.equals("Sphere")) {
                    primitives.add(item);
                }
            }

            output.append(primitives.size()).append('\n');

            for (ModelTools.SceneItem item : primitives) {
                int type = item.mesh.name.equals("Cube") ? 0 :
                    item.mesh.name.equals("Cylinder") ? 1 : 2;

                output.append(String.format(
                    Locale.US, "%d %.9g %.9g %.9g\n",
                    type, item.x, item.y, item.z
                ));
            }

            return output.toString();
        }

        public synchronized void loadAcProject(String content)
            throws Exception {

            Scanner scanner = new Scanner(content);
            scanner.useLocale(Locale.US);

            try {
                String header = scanner.next();
                int version = scanner.nextInt();

                if (!header.equals("AC_PROJECT") || version != 1) {
                    throw new Exception("Unsupported project format.");
                }

                float yaw = scanner.nextFloat();
                float pitch = scanner.nextFloat();
                float distance = scanner.nextFloat();

                if (!Float.isFinite(yaw) ||
                    !Float.isFinite(pitch) ||
                    !Float.isFinite(distance) ||
                    pitch < -1.5f || pitch > 1.5f ||
                    distance < 2.0f || distance > 30.0f) {
                    throw new Exception("Invalid camera settings.");
                }

                int count = scanner.nextInt();

                if (count < 0 || count > 100000) {
                    throw new Exception("Invalid object count.");
                }

                ArrayList<ModelTools.SceneItem> loaded = new ArrayList<>();

                for (int i = 0; i < count; i++) {
                    int type = scanner.nextInt();
                    float x = scanner.nextFloat();
                    float y = scanner.nextFloat();
                    float z = scanner.nextFloat();

                    if (type < 0 || type > 2 ||
                        !Float.isFinite(x) ||
                        !Float.isFinite(y) ||
                        !Float.isFinite(z)) {
                        throw new Exception("Invalid object data.");
                    }

                    ModelTools.MeshData mesh =
                        type == 0 ? ModelTools.createCube() :
                        type == 1 ? ModelTools.createCylinder() :
                        ModelTools.createSphere();

                    loaded.add(new ModelTools.SceneItem(mesh, x, y, z));
                }

                scene.clear();
                scene.addAll(loaded);
                meshBuffers.clear();

                cameraYaw = yaw;
                cameraPitch = pitch;
                cameraDistance = distance;
            } finally {
                scanner.close();
            }
        }

        // --------------------------------------------------
        // SHADERS
        // --------------------------------------------------

        private int compileShader(int type, String source) {
            int shader = GLES20.glCreateShader(type);

            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);

            int[] status = new int[1];

            GLES20.glGetShaderiv(
                shader, GLES20.GL_COMPILE_STATUS, status, 0
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
                GLES20.GL_VERTEX_SHADER, vertexSource
            );

            int fragment = compileShader(
                GLES20.GL_FRAGMENT_SHADER, fragmentSource
            );

            int result = GLES20.glCreateProgram();

            GLES20.glAttachShader(result, vertex);
            GLES20.glAttachShader(result, fragment);
            GLES20.glLinkProgram(result);

            int[] status = new int[1];

            GLES20.glGetProgramiv(
                result, GLES20.GL_LINK_STATUS, status, 0
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


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

import org.json.*;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends Activity {

    private static final int REQUEST_SAVE=100, REQUEST_OPEN=101,
        REQUEST_IMPORT=102, REQUEST_EXPORT=103,
        REQUEST_IMPORT_GLB=104, REQUEST_EXPORT_GLB=105;

    private GLSurfaceView glView;
    private SceneRenderer renderer;
    private MenuOverlay menu;

    private float previousX, previousY, previousPinchDistance;
    private boolean vertexMode=false;
    private int dragAxis=-1;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(19,19,19));
        getWindow().setNavigationBarColor(Color.rgb(19,19,19));

        FrameLayout root=new FrameLayout(this);
        glView=new GLSurfaceView(this);
        glView.setEGLContextClientVersion(2);
        renderer=new SceneRenderer();
        glView.setRenderer(renderer);
        glView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        root.addView(glView);

        menu=new MenuOverlay();
        root.addView(menu);
        glView.setOnTouchListener((v,e)->handleSceneTouch(e));
        setContentView(root);
        hideSystemUI();
    }

    private void hideSystemUI(){
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private boolean handleSceneTouch(MotionEvent e){
        int action=e.getActionMasked();
        float x=e.getX(), y=e.getY();

        if(action==MotionEvent.ACTION_DOWN){
            previousX=x;previousY=y;dragAxis=-1;
            if(vertexMode){
                dragAxis=renderer.beginVertexInteraction(x,y,glView.getWidth(),glView.getHeight());
                return true;
            }
            return true;
        }

        if(action==MotionEvent.ACTION_POINTER_DOWN){
            if(e.getPointerCount()>=2)previousPinchDistance=pinchDistance(e);
            return true;
        }

        if(action==MotionEvent.ACTION_MOVE){
            if(vertexMode && e.getPointerCount()==1){
                if(dragAxis>=0){
                    renderer.dragSelectedVertex(x-previousX,y-previousY,
                        dragAxis,glView.getWidth(),glView.getHeight());
                }
                previousX=x;previousY=y;
                return true;
            }

            if(e.getPointerCount()>=2){
                float d=pinchDistance(e);
                if(previousPinchDistance>0)renderer.zoomCamera((d-previousPinchDistance)*0.015f);
                previousPinchDistance=d;
            }else{
                renderer.rotateCamera((x-previousX)*0.005f,(y-previousY)*0.005f);
                previousX=x;previousY=y;
            }
            return true;
        }

        if(action==MotionEvent.ACTION_POINTER_UP){
            previousPinchDistance=0;
            previousX=e.getX(0);previousY=e.getY(0);
            return true;
        }

        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
            previousPinchDistance=0;
            dragAxis=-1;
            if(vertexMode && action==MotionEvent.ACTION_UP){
                if(renderer.getSelectedVertex()<0)
                    renderer.selectNearestVertex(x,y,glView.getWidth(),glView.getHeight());
            }
            menu.invalidate();
            return true;
        }
        return true;
    }

    private float pinchDistance(MotionEvent e){
        float dx=e.getX(0)-e.getX(1),dy=e.getY(0)-e.getY(1);
        return (float)Math.sqrt(dx*dx+dy*dy);
    }

    private void launchPicker(int code,String title,String mime){
        Intent i;
        if(code==REQUEST_SAVE||code==REQUEST_EXPORT||code==REQUEST_EXPORT_GLB){
            i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.putExtra(Intent.EXTRA_TITLE,title);
        }else{
            i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
        }
        i.setType(mime);
        startActivityForResult(i,code);
    }

    private void saveProject(){launchPicker(REQUEST_SAVE,"Project.Ac","text/plain");}
    private void openProject(){launchPicker(REQUEST_OPEN,null,"*/*");}
    private void importModel(){launchPicker(REQUEST_IMPORT,null,"*/*");}
    private void exportModel(){launchPicker(REQUEST_EXPORT,"Model.obj","text/plain");}
    private void importGLB(){launchPicker(REQUEST_IMPORT_GLB,null,"model/gltf-binary");}
    private void exportGLB(){launchPicker(REQUEST_EXPORT_GLB,"Model.glb","model/gltf-binary");}

    @Override
    protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();

        try{
            if(request==REQUEST_SAVE){
                saveAcProject(uri);toast("Project saved.");
            }else if(request==REQUEST_OPEN){
                openAcProject(uri);menu.invalidate();toast("Project opened.");
            }else if(request==REQUEST_IMPORT){
                importObj(uri);
            }else if(request==REQUEST_EXPORT){
                exportObj(uri);toast("OBJ exported.");
            }else if(request==REQUEST_IMPORT_GLB){
                importGlb(uri);
            }else if(request==REQUEST_EXPORT_GLB){
                exportGlb(uri);toast("GLB exported.");
            }
        }catch(Exception ex){
            ex.printStackTrace();toast("File error: "+ex.getMessage());
        }
    }

    private void importObj(Uri uri)throws Exception{
        InputStream in=getContentResolver().openInputStream(uri);
        if(in==null)throw new IOException("Cannot open OBJ.");
        ModelTools.MeshData mesh;
        try{mesh=ModelTools.importOBJ(in);}finally{in.close();}
        renderer.addImportedMesh(mesh);
        menu.invalidate();toast("OBJ imported.");
    }

    private void exportObj(Uri uri)throws Exception{
        OutputStream out=getContentResolver().openOutputStream(uri);
        if(out==null)throw new IOException("Cannot create OBJ.");
        try{ModelTools.exportOBJ(out,renderer.getSceneSnapshot());}finally{out.close();}
    }

    private void importGlb(Uri uri)throws Exception{
        InputStream in=getContentResolver().openInputStream(uri);
        if(in==null)throw new IOException("Cannot open GLB.");
        ArrayList<ModelTools.SceneItem> items;
        try{items=ModelTools.importGLB(in);}finally{in.close();}
        renderer.addImportedItems(items);
        menu.invalidate();
        toast("GLB imported: "+items.size()+" mesh(es).");
    }

    private void exportGlb(Uri uri)throws Exception{
        OutputStream out=getContentResolver().openOutputStream(uri);
        if(out==null)throw new IOException("Cannot create GLB.");
        try{ModelTools.exportGLB(out,renderer.getSceneSnapshot());}finally{out.close();}
    }

    private void saveAcProject(Uri uri)throws Exception{
        OutputStream out=getContentResolver().openOutputStream(uri);
        if(out==null)throw new IOException("Cannot create project.");
        try{
            out.write(renderer.createAcProject().getBytes(StandardCharsets.UTF_8));
        }finally{out.close();}
    }

    private void openAcProject(Uri uri)throws Exception{
        InputStream in=getContentResolver().openInputStream(uri);
        if(in==null)throw new IOException("Cannot open project.");
        StringBuilder s=new StringBuilder();
        try{
            BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));
            String line;
            while((line=r.readLine())!=null)s.append(line).append('\n');
        }finally{in.close();}
        renderer.loadAcProject(s.toString());
    }

    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}

    @Override protected void onResume(){super.onResume();if(glView!=null)glView.onResume();hideSystemUI();}
    @Override protected void onPause(){if(glView!=null)glView.onPause();super.onPause();}

    // ==================================================
    // MENU AND STATS
    // ==================================================

    private class MenuOverlay extends View{
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private int activeMenu=0;
        MenuOverlay(){super(MainActivity.this);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}

        private void rect(Canvas c,float x,float y,float w,float h,int color){
            p.setColor(color);p.setStyle(Paint.Style.FILL);c.drawRect(x,y,x+w,y+h,p);
        }
        private void text(Canvas c,String s,float x,float y,float size,int color){
            p.setColor(color);p.setTextSize(size);p.setTypeface(Typeface.DEFAULT);c.drawText(s,x,y,p);
        }

        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            float scale=Math.max(1f,getWidth()/900f);
            c.save();c.scale(scale,scale);
            rect(c,0,0,900,30,Color.rgb(20,20,20));
            drawButton(c,10,48,"File",activeMenu==1);
            drawButton(c,62,48,"Edit",false);
            drawButton(c,114,65,"Select",false);
            drawButton(c,183,48,"Add",activeMenu==2);
            drawButton(c,245,105,vertexMode?"Vertex ON":"Vertex",vertexMode);

            if(activeMenu==1)drawDropdown(c,10,180,
                new String[]{"Save AC","Open AC","Import OBJ","Export OBJ","Import GLB","Export GLB"});
            if(activeMenu==2)drawDropdown(c,183,125,
                new String[]{"Cube","Cylinder","Sphere"});
            c.restore();
            drawStats(c);
        }

        private void drawButton(Canvas c,float x,float width,String label,boolean selected){
            rect(c,x,0,width,30,selected?Color.rgb(65,65,65):Color.rgb(31,31,31));
            text(c,label,x+8,20,15,Color.LTGRAY);
        }
        private void drawDropdown(Canvas c,float x,float width,String[] items){
            for(int i=0;i<items.length;i++){
                float y=30+i*30;
                rect(c,x,y,width,30,Color.rgb(41,41,41));
                text(c,items[i],x+10,y+20,15,Color.LTGRAY);
            }
        }

        private void drawStats(Canvas c){
            float d=getResources().getDisplayMetrics().density;
            int[] s=renderer.getStatistics();
            float x=10*d,y=48*d,w=150*d,h=112*d;
            p.setColor(Color.argb(220,18,18,18));
            c.drawRoundRect(x,y,x+w,y+h,8*d,8*d,p);
            p.setColor(Color.LTGRAY);p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(14*d);c.drawText("SCENE STATS",x+10*d,y+21*d,p);
            p.setTypeface(Typeface.DEFAULT);p.setTextSize(13*d);
            c.drawText("Vertices: "+s[0],x+10*d,y+48*d,p);
            c.drawText("Edges: "+s[1],x+10*d,y+72*d,p);
            c.drawText("Faces: "+s[2],x+10*d,y+96*d,p);
            if(vertexMode){
                p.setColor(Color.rgb(100,220,255));
                c.drawText("VERTEX EDIT MODE",x+10*d,y+132*d,p);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e){
            if(e.getAction()!=MotionEvent.ACTION_DOWN)return activeMenu!=0;
            float scale=Math.max(1f,getWidth()/900f);
            float x=e.getX()/scale,y=e.getY()/scale;

            if(y<=30){
                if(x>=10&&x<=58)activeMenu=activeMenu==1?0:1;
                else if(x>=183&&x<=231)activeMenu=activeMenu==2?0:2;
                else if(x>=245&&x<=350){
                    vertexMode=!vertexMode;
                    renderer.clearSelection();
                    activeMenu=0;
                }else activeMenu=0;
                invalidate();return true;
            }

            if(activeMenu==1&&x>=10&&x<=190){
                int item=(int)((y-30)/30);activeMenu=0;invalidate();
                switch(item){
                    case 0:saveProject();break;
                    case 1:openProject();break;
                    case 2:importModel();break;
                    case 3:exportModel();break;
                    case 4:importGLB();break;
                    case 5:exportGLB();break;
                }
                return true;
            }

            if(activeMenu==2&&x>=183&&x<=308){
                int item=(int)((y-30)/30);activeMenu=0;
                if(item==0)renderer.addPrimitive(0);
                if(item==1)renderer.addPrimitive(1);
                if(item==2)renderer.addPrimitive(2);
                invalidate();return true;
            }
            if(activeMenu!=0){activeMenu=0;invalidate();return true;}
            return false;
        }
    }

    // ==================================================
    // OPENGL RENDERER AND VERTEX GIZMO
    // ==================================================

    public static class SceneRenderer implements GLSurfaceView.Renderer{
        private final ArrayList<ModelTools.SceneItem> scene=new ArrayList<>();
        private final HashMap<ModelTools.MeshData,FloatBuffer> buffers=new HashMap<>();

        private int program,posHandle,matrixHandle,colorHandle;
        private FloatBuffer gridBuffer;

        private final float[] projection=new float[16],view=new float[16],vp=new float[16];
        private final float[] model=new float[16],objectMvp=new float[16],inverseVP=new float[16];

        private float cameraYaw=.8f,cameraPitch=.4f,cameraDistance=8f;
        private int selectedObject=-1,selectedVertex=-1;
        private float surfaceWidth=1,surfaceHeight=1;
        private float gizmoLength=.85f;

        private static final String VS=
            "uniform mat4 uMatrix;attribute vec3 aPosition;void main(){gl_Position=uMatrix*vec4(aPosition,1.0);gl_PointSize=13.0;}";
        private static final String FS=
            "precision mediump float;uniform vec4 uColor;void main(){gl_FragColor=uColor;}";

        @Override public void onSurfaceCreated(GL10 gl,EGLConfig config){
            GLES20.glClearColor(.075f,.075f,.075f,1);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glDisable(GLES20.GL_CULL_FACE);
            program=createProgram(VS,FS);
            posHandle=GLES20.glGetAttribLocation(program,"aPosition");
            matrixHandle=GLES20.glGetUniformLocation(program,"uMatrix");
            colorHandle=GLES20.glGetUniformLocation(program,"uColor");
            createGrid();
        }

        @Override public void onSurfaceChanged(GL10 gl,int w,int h){
            surfaceWidth=Math.max(1,w);surfaceHeight=Math.max(1,h);
            GLES20.glViewport(0,0,w,Math.max(1,h));
            Matrix.perspectiveM(projection,0,45,(float)w/Math.max(1,h),.1f,100);
        }

        @Override public void onDrawFrame(GL10 gl){
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
            GLES20.glUseProgram(program);
            updateCameraMatrices();
            drawGrid();
            drawScene();
            drawGizmo();
        }

        private void updateCameraMatrices(){
            Matrix.setIdentityM(view,0);
            Matrix.translateM(view,0,0,0,-cameraDistance);
            Matrix.rotateM(view,0,(float)Math.toDegrees(cameraPitch),1,0,0);
            Matrix.rotateM(view,0,(float)Math.toDegrees(cameraYaw),0,1,0);
            Matrix.multiplyMM(vp,0,projection,0,view,0);
        }

        public synchronized void rotateCamera(float dx,float dy){
            cameraYaw+=dx;cameraPitch=Math.max(-1.5f,Math.min(1.5f,cameraPitch+dy));
        }
        public synchronized void zoomCamera(float amount){
            cameraDistance=Math.max(2,Math.min(30,cameraDistance-amount));
        }

        public synchronized void addPrimitive(int type){
            ModelTools.MeshData m=type==0?ModelTools.createCube():
                type==1?ModelTools.createCylinder():ModelTools.createSphere();
            addMesh(m);
        }
        public synchronized void addImportedMesh(ModelTools.MeshData mesh){addMesh(mesh);}
        public synchronized void addImportedItems(ArrayList<ModelTools.SceneItem> items){
            for(ModelTools.SceneItem item:items)addMesh(item.mesh,item.x,item.y,item.z);
        }
        private void addMesh(ModelTools.MeshData m){addMesh(m,0,0,0);}
        private void addMesh(ModelTools.MeshData m,float x,float y,float z){
            scene.add(new ModelTools.SceneItem(m,x,y,z));buffers.remove(m);
        }
        public synchronized ArrayList<ModelTools.SceneItem> getSceneSnapshot(){
            return new ArrayList<>(scene);
        }
        public synchronized int[] getStatistics(){
            int v=0,e=0,f=0;
            for(ModelTools.SceneItem i:scene){
                v+=i.mesh.getVertexCount();e+=i.mesh.getEdgeCount();f+=i.mesh.getFaceCount();
            }
            return new int[]{v,e,f};
        }
        public synchronized int getSelectedVertex(){return selectedVertex;}
        public synchronized void clearSelection(){selectedObject=-1;selectedVertex=-1;}

        private void drawScene(){
            synchronized(this){
                for(int n=0;n<scene.size();n++){
                    ModelTools.SceneItem item=scene.get(n);
                    FloatBuffer b=buffers.get(item.mesh);
                    if(b==null){b=createBuffer(item.mesh.getTriangleVertices());buffers.put(item.mesh,b);}
                    Matrix.setIdentityM(model,0);
                    Matrix.translateM(model,0,item.x,item.y,item.z);
                    Matrix.multiplyMM(objectMvp,0,vp,0,model,0);
                    GLES20.glUniformMatrix4fv(matrixHandle,1,false,objectMvp,0);
                    GLES20.glUniform4f(colorHandle,.65f,.65f,.65f,1);
                    drawBuffer(b,b.limit()/3);
                }
            }
        }

        // Select the nearest projected vertex within a touch radius.
        public synchronized void selectNearestVertex(float sx,float sy,int width,int height){
            updateCameraMatrices();
            float best=Float.MAX_VALUE;
            selectedObject=-1;selectedVertex=-1;
            for(int oi=0;oi<scene.size();oi++){
                ModelTools.SceneItem item=scene.get(oi);
                for(int vi=0;vi<item.mesh.getVertexCount();vi++){
                    float[] p=worldVertex(item,vi);
                    float[] screen=project(p,width,height);
                    float dx=screen[0]-sx,dy=screen[1]-sy;
                    float d=dx*dx+dy*dy;
                    if(d<best&&d<28*28){best=d;selectedObject=oi;selectedVertex=vi;}
                }
            }
        }

        // On DOWN: select a nearby vertex if needed, then hit-test gizmo axes.
        public synchronized int beginVertexInteraction(float sx,float sy,int width,int height){
            if(selectedObject<0||selectedVertex<0){
                selectNearestVertex(sx,sy,width,height);
            }
            if(selectedObject<0)return -1;
            float[] origin=selectedWorldVertex();
            float[] screenOrigin=project(origin,width,height);
            float best=Float.MAX_VALUE;
            int axis=-1;
            for(int a=0;a<3;a++){
                float[] end=origin.clone();
                end[a]+=gizmoLength;
                float[] s=project(end,width,height);
                float d=pointSegmentDistance(sx,sy,screenOrigin[0],screenOrigin[1],s[0],s[1]);
                if(d<best){best=d;axis=a;}
            }
            return best<32?axis:-1;
        }

        public synchronized void dragSelectedVertex(float dx,float dy,int axis,int width,int height){
            if(selectedObject<0||selectedVertex<0||axis<0||axis>2)return;
            ModelTools.SceneItem item=scene.get(selectedObject);
            float[] origin=selectedWorldVertex();
            float[] end=origin.clone();end[axis]+=gizmoLength;
            float[] a=project(origin,width,height),b=project(end,width,height);
            float vx=b[0]-a[0],vy=b[1]-a[1];
            float denom=vx*vx+vy*vy;
            if(denom<0.001f)return;
            float amount=(dx*vx+dy*vy)/denom*gizmoLength;
            int p=selectedVertex*3+axis;
            item.mesh.vertices[p]+=amount;
            buffers.remove(item.mesh);
        }

        private float[] selectedWorldVertex(){
            ModelTools.SceneItem item=scene.get(selectedObject);
            int p=selectedVertex*3;
            return new float[]{item.mesh.vertices[p]+item.x,item.mesh.vertices[p+1]+item.y,item.mesh.vertices[p+2]+item.z};
        }
        private float[] worldVertex(ModelTools.SceneItem item,int index){
            int p=index*3;
            return new float[]{item.mesh.vertices[p]+item.x,item.mesh.vertices[p+1]+item.y,item.mesh.vertices[p+2]+item.z};
        }

        private float[] project(float[] point,int width,int height){
            float[] clip=new float[4];
            float[] v={point[0],point[1],point[2],1};
            Matrix.multiplyMV(clip,0,vp,0,v,0);
            if(Math.abs(clip[3])<0.00001f)return new float[]{-10000,-10000};
            float nx=clip[0]/clip[3],ny=clip[1]/clip[3];
            return new float[]{(nx*.5f+.5f)*width,(1-(ny*.5f+.5f))*height};
        }

        private float pointSegmentDistance(float px,float py,float ax,float ay,float bx,float by){
            float dx=bx-ax,dy=by-ay;
            float t=((px-ax)*dx+(py-ay)*dy)/(dx*dx+dy*dy+0.0001f);
            t=Math.max(0,Math.min(1,t));
            float x=ax+t*dx,y=ay+t*dy;
            return (float)Math.hypot(px-x,py-y);
        }

        private void drawGizmo(){
            synchronized(this){
                if(selectedObject<0||selectedVertex<0||selectedObject>=scene.size())return;
                float[] o=selectedWorldVertex();
                drawPoint(o,13,1,1,0,1);
                float[][] colors={{1,0,0},{0,1,0},{0.1f,0.4f,1}};
                for(int axis=0;axis<3;axis++){
                    float[] end=o.clone();end[axis]+=gizmoLength;
                    drawLine(o,end,colors[axis][0],colors[axis][1],colors[axis][2]);
                    float[] tip=end.clone();tip[axis]+=0.12f;
                    drawLine(end,tip,colors[axis][0],colors[axis][1],colors[axis][2]);
                }
            }
        }

        private void drawLine(float[] a,float[] b,float r,float g,float bl){
            FloatBuffer f=createBuffer(new float[]{a[0],a[1],a[2],b[0],b[1],b[2]});
            GLES20.glUniformMatrix4fv(matrixHandle,1,false,vp,0);
            GLES20.glUniform4f(colorHandle,r,g,bl,1);
            GLES20.glLineWidth(5);
            GLES20.glEnableVertexAttribArray(posHandle);
            GLES20.glVertexAttribPointer(posHandle,3,GLES20.GL_FLOAT,false,12,f);
            GLES20.glDrawArrays(GLES20.GL_LINES,0,2);
            GLES20.glDisableVertexAttribArray(posHandle);
        }

        private void drawPoint(float[] point,float size,float r,float g,float b){
            FloatBuffer f=createBuffer(point);
            GLES20.glUniformMatrix4fv(matrixHandle,1,false,vp,0);
            GLES20.glUniform4f(colorHandle,r,g,b,1);
            GLES20.glEnableVertexAttribArray(posHandle);
            GLES20.glVertexAttribPointer(posHandle,3,GLES20.GL_FLOAT,false,12,f);
            GLES20.glDrawArrays(GLES20.GL_POINTS,0,1);
            GLES20.glDisableVertexAttribArray(posHandle);
        }

        private FloatBuffer gridBuffer;
        private void createGrid(){
            float[] v=new float[41*12];int k=0;
            for(int i=-20;i<=20;i++){
                v[k++]=i;v[k++]=-1.01f;v[k++]=-20;
                v[k++]=i;v[k++]=-1.01f;v[k++]=20;
                v[k++]=-20;v[k++]=-1.01f;v[k++]=i;
                v[k++]=20;v[k++]=-1.01f;v[k++]=i;
            }
            gridBuffer=createBuffer(v);
        }
        private void drawGrid(){
            GLES20.glUniformMatrix4fv(matrixHandle,1,false,vp,0);
            GLES20.glEnableVertexAttribArray(posHandle);
            gridBuffer.position(0);
            GLES20.glVertexAttribPointer(posHandle,3,GLES20.GL_FLOAT,false,12,gridBuffer);
            for(int i=-20;i<=20;i++){
                float shade=i==0?.45f:.22f;
                GLES20.glUniform4f(colorHandle,shade,shade,shade,1);
                GLES20.glDrawArrays(GLES20.GL_LINES,(i+20)*4,4);
            }
            GLES20.glDisableVertexAttribArray(posHandle);
        }
        private void drawBuffer(FloatBuffer b,int count){
            GLES20.glEnableVertexAttribArray(posHandle);
            b.position(0);
            GLES20.glVertexAttribPointer(posHandle,3,GLES20.GL_FLOAT,false,12,b);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,count);
            GLES20.glDisableVertexAttribArray(posHandle);
        }
        private FloatBuffer createBuffer(float[] data){
            ByteBuffer bb=ByteBuffer.allocateDirect(data.length*4).order(ByteOrder.nativeOrder());
            FloatBuffer b=bb.asFloatBuffer();b.put(data);b.position(0);return b;
        }

        // AC_PROJECT 1 format
        public synchronized String createAcProject(){
            StringBuilder out=new StringBuilder("AC_PROJECT 1\n");
            out.append(String.format(Locale.US,"%.9g %.9g %.9g\n",cameraYaw,cameraPitch,cameraDistance));
            ArrayList<ModelTools.SceneItem> prim=new ArrayList<>();
            for(ModelTools.SceneItem i:scene){
                String n=i.mesh.name;
                if(n.equals("Cube")||n.equals("Cylinder")||n.equals("Sphere"))prim.add(i);
            }
            out.append(prim.size()).append('\n');
            for(ModelTools.SceneItem i:prim){
                int type=i.mesh.name.equals("Cube")?0:i.mesh.name.equals("Cylinder")?1:2;
                out.append(String.format(Locale.US,"%d %.9g %.9g %.9g\n",type,i.x,i.y,i.z));
            }
            return out.toString();
        }

        public synchronized void loadAcProject(String content)throws Exception{
            Scanner s=new Scanner(content);s.useLocale(Locale.US);
            try{
                if(!s.next().equals("AC_PROJECT")||s.nextInt()!=1)throw new Exception("Unsupported project.");
                float yaw=s.nextFloat(),pitch=s.nextFloat(),distance=s.nextFloat();
                if(!Float.isFinite(yaw)||!Float.isFinite(pitch)||!Float.isFinite(distance)||
                    pitch< -1.5f||pitch>1.5f||distance<2||distance>30)throw new Exception("Invalid camera.");
                int count=s.nextInt();if(count<0||count>100000)throw new Exception("Invalid object count.");
                ArrayList<ModelTools.SceneItem> loaded=new ArrayList<>();
                for(int i=0;i<count;i++){
                    int type=s.nextInt();float x=s.nextFloat(),y=s.nextFloat(),z=s.nextFloat();
                    if(type<0||type>2||!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))
                        throw new Exception("Invalid object.");
                    ModelTools.MeshData m=type==0?ModelTools.createCube():type==1?ModelTools.createCylinder():ModelTools.createSphere();
                    loaded.add(new ModelTools.SceneItem(m,x,y,z));
                }
                scene.clear();scene.addAll(loaded);buffers.clear();clearSelection();
                cameraYaw=yaw;cameraPitch=pitch;cameraDistance=distance;
            }finally{s.close();}
        }

        private int compileShader(int type,String source){
            int sh=GLES20.glCreateShader(type);
            GLES20.glShaderSource(sh,source);GLES20.glCompileShader(sh);
            int[] status=new int[1];GLES20.glGetShaderiv(sh,GLES20.GL_COMPILE_STATUS,status,0);
            if(status[0]==0){String error=GLES20.glGetShaderInfoLog(sh);GLES20.glDeleteShader(sh);throw new RuntimeException(error);}
            return sh;
        }
        private int createProgram(String vs,String fs){
            int v=compileShader(GLES20.GL_VERTEX_SHADER,vs),f=compileShader(GLES20.GL_FRAGMENT_SHADER,fs);
            int p=GLES20.glCreateProgram();GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);
            int[] status=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,status,0);
            if(status[0]==0){String error=GLES20.glGetProgramInfoLog(p);GLES20.glDeleteProgram(p);throw new RuntimeException(error);}
            GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);return p;
        }
    }
}

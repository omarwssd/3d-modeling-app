
package com.modeling.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ModelTools {
    private ModelTools() {}

    public static class MeshData {
        public String name;
        public float[] vertices;
        public ArrayList<int[]> faces = new ArrayList<>();

        public MeshData(String name, float[] vertices, ArrayList<int[]> faces) {
            this.name = name;
            this.vertices = vertices;
            this.faces = faces;
        }

        public int getVertexCount() { return vertices.length / 3; }
        public int getFaceCount() { return faces.size(); }

        public int getEdgeCount() {
            Set<Long> edges = new HashSet<>();
            for (int[] face : faces) {
                for (int i = 0; i < face.length; i++) {
                    int a = face[i], b = face[(i + 1) % face.length];
                    edges.add((((long)Math.min(a,b)) << 32) |
                            (Math.max(a,b) & 0xffffffffL));
                }
            }
            return edges.size();
        }

        public float[] getTriangleVertices() {
            ArrayList<Float> out = new ArrayList<>();
            for (int[] face : faces) {
                for (int i = 1; i < face.length - 1; i++) {
                    addPosition(out, face[0]);
                    addPosition(out, face[i]);
                    addPosition(out, face[i + 1]);
                }
            }
            return toArray(out);
        }

        private void addPosition(ArrayList<Float> out, int index) {
            int p = index * 3;
            out.add(vertices[p]);
            out.add(vertices[p + 1]);
            out.add(vertices[p + 2]);
        }

        public void setVertex(int index, float x, float y, float z) {
            if (index < 0 || index >= getVertexCount()) return;
            int p = index * 3;
            vertices[p] = x;
            vertices[p + 1] = y;
            vertices[p + 2] = z;
        }
    }

    public static class SceneItem {
        public MeshData mesh;
        public float x, y, z;
        public SceneItem(MeshData mesh, float x, float y, float z) {
            this.mesh = mesh; this.x = x; this.y = y; this.z = z;
        }
    }

    public static MeshData createCube() {
        float s = 0.75f;
        float[] v = {
            -s,-s,-s, s,-s,-s, s,s,-s, -s,s,-s,
            -s,-s,s, s,-s,s, s,s,s, -s,s,s
        };
        ArrayList<int[]> f = new ArrayList<>();
        f.add(new int[]{0,3,2,1}); f.add(new int[]{4,5,6,7});
        f.add(new int[]{0,1,5,4}); f.add(new int[]{3,7,6,2});
        f.add(new int[]{0,4,7,3}); f.add(new int[]{1,2,6,5});
        return new MeshData("Cube", v, f);
    }

    public static MeshData createCylinder() {
        final int n = 32;
        float[] v = new float[n * 2 * 3];
        ArrayList<int[]> f = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            float x = (float)Math.cos(a) * 0.65f;
            float z = (float)Math.sin(a) * 0.65f;
            set(v, i, x, -0.75f, z);
            set(v, n + i, x, 0.75f, z);
        }

        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            f.add(new int[]{i, j, n+j, n+i});
        }

        int[] bottom = new int[n], top = new int[n];
        for (int i = 0; i < n; i++) {
            bottom[i] = n - 1 - i;
            top[i] = n + i;
        }
        f.add(bottom);
        f.add(top);
        return new MeshData("Cylinder", v, f);
    }

    public static MeshData createSphere() {
        final int rings = 20, segments = 32;
        final float radius = 0.8f;
        ArrayList<Float> v = new ArrayList<>();
        ArrayList<int[]> f = new ArrayList<>();

        add(v, 0, radius, 0);
        for (int r = 1; r < rings; r++) {
            double lat = Math.PI * (-0.5 + (double)r / rings);
            for (int s = 0; s < segments; s++) {
                double lon = Math.PI * 2 * s / segments;
                add(v,
                    (float)(Math.cos(lat)*Math.cos(lon)*radius),
                    (float)(Math.sin(lat)*radius),
                    (float)(Math.cos(lat)*Math.sin(lon)*radius));
            }
        }

        int bottom = 1 + (rings - 1) * segments;
        add(v, 0, -radius, 0);

        for (int i = 0; i < segments; i++) {
            int j = (i + 1) % segments;
            f.add(new int[]{0, 1+i, 1+j});
        }

        for (int r = 0; r < rings - 2; r++) {
            int a = 1 + r * segments, b = a + segments;
            for (int i = 0; i < segments; i++) {
                int j = (i + 1) % segments;
                f.add(new int[]{a+i, b+i, b+j, a+j});
            }
        }

        int last = 1 + (rings - 2) * segments;
        for (int i = 0; i < segments; i++) {
            int j = (i + 1) % segments;
            f.add(new int[]{bottom, last+j, last+i});
        }
        return new MeshData("Sphere", toArray(v), f);
    }

    private static void set(float[] v, int i, float x, float y, float z) {
        int p = i * 3; v[p] = x; v[p+1] = y; v[p+2] = z;
    }
    private static void add(ArrayList<Float> v, float x, float y, float z) {
        v.add(x); v.add(y); v.add(z);
    }
    private static float[] toArray(ArrayList<Float> a) {
        float[] r = new float[a.size()];
        for (int i=0;i<a.size();i++) r[i]=a.get(i);
        return r;
    }
    private static String safeName(String s) {
        return s == null ? "Object" : s.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    public static MeshData importOBJ(InputStream input) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        ArrayList<Float> vertices = new ArrayList<>();
        ArrayList<int[]> faces = new ArrayList<>();
        String line;

        while ((line = r.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] p = line.split("\\s+");

            if (p[0].equals("v") && p.length >= 4) {
                float x=Float.parseFloat(p[1]), y=Float.parseFloat(p[2]), z=Float.parseFloat(p[3]);
                if (!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))
                    throw new IOException("Invalid OBJ vertex.");
                add(vertices,x,y,z);
            } else if (p[0].equals("f") && p.length >= 4) {
                int count=vertices.size()/3;
                int[] face=new int[p.length-1];
                for(int i=1;i<p.length;i++){
                    int index=Integer.parseInt(p[i].split("/")[0]);
                    index=index<0?count+index:index-1;
                    if(index<0||index>=count) throw new IOException("Invalid OBJ face index.");
                    face[i-1]=index;
                }
                faces.add(face);
            }
        }
        if(vertices.isEmpty()||faces.isEmpty()) throw new IOException("No usable OBJ geometry.");
        return new MeshData("Imported OBJ",toArray(vertices),faces);
    }

    public static void exportOBJ(OutputStream output, ArrayList<SceneItem> scene) throws IOException {
        BufferedWriter w=new BufferedWriter(new OutputStreamWriter(output,StandardCharsets.UTF_8));
        int offset=1;
        w.write("# 3D Modeling App OBJ export\n");
        for(SceneItem item:scene){
            MeshData m=item.mesh;
            w.write("o "+safeName(m.name)+"\n");
            for(int i=0;i<m.vertices.length;i+=3)
                w.write(String.format(Locale.US,"v %.9g %.9g %.9g\n",
                    m.vertices[i]+item.x,m.vertices[i+1]+item.y,m.vertices[i+2]+item.z));
            for(int[] face:m.faces){
                w.write("f");
                for(int index:face) w.write(" "+(index+offset));
                w.write("\n");
            }
            offset+=m.getVertexCount();
        }
        w.flush();
    }

    // --------------------------------------------------
    // GLB 2.0 IMPORT - triangle primitives, POSITION and
    // optional indices. Applies node translation.
    // --------------------------------------------------

    public static ArrayList<SceneItem> importGLB(InputStream input) throws Exception {
        byte[] file = readAll(input);
        ByteBuffer b = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);
        if (b.remaining() < 20 || b.getInt(0) != 0x46546C67 || b.getInt(4) != 2)
            throw new IOException("Not a valid GLB 2.0 file.");

        int total = b.getInt(8);
        if (total > file.length || total < 20) throw new IOException("Invalid GLB length.");

        int p=12;
        String jsonText=null;
        byte[] bin=null;

        while(p+8<=total){
            int length=b.getInt(p), type=b.getInt(p+4); p+=8;
            if(length<0||p+length>total) throw new IOException("Invalid GLB chunk.");
            if(type==0x4E4F534A) jsonText=new String(file,p,length,StandardCharsets.UTF_8).trim();
            if(type==0x004E4942) bin=Arrays.copyOfRange(file,p,p+length);
            p+=length;
        }
        if(jsonText==null||bin==null) throw new IOException("GLB is missing JSON or binary data.");

        JSONObject root=new JSONObject(jsonText);
        JSONArray views=root.getJSONArray("bufferViews");
        JSONArray accessors=root.getJSONArray("accessors");
        JSONArray meshes=root.getJSONArray("meshes");
        JSONArray nodes=root.optJSONArray("nodes");
        ArrayList<SceneItem> result=new ArrayList<>();

        if(nodes!=null){
            for(int ni=0;ni<nodes.length();ni++){
                JSONObject node=nodes.getJSONObject(ni);
                if(!node.has("mesh")) continue;
                int meshIndex=node.getInt("mesh");
                float tx=0,ty=0,tz=0;
                JSONArray tr=node.optJSONArray("translation");
                if(tr!=null&&tr.length()>=3){tx=(float)tr.getDouble(0);ty=(float)tr.getDouble(1);tz=(float)tr.getDouble(2);}
                JSONObject mesh=meshes.getJSONObject(meshIndex);
                JSONArray primitives=mesh.getJSONArray("primitives");
                for(int pi=0;pi<primitives.length();pi++){
                    JSONObject primitive=primitives.getJSONObject(pi);
                    if(primitive.optInt("mode",4)!=4) continue;
                    JSONObject attrs=primitive.getJSONObject("attributes");
                    int posAccessor=attrs.getInt("POSITION");
                    float[] verts=readPositions(bin,views,accessors,posAccessor);
                    int[] indices;
                    if(primitive.has("indices"))
                        indices=readIndices(bin,views,accessors,primitive.getInt("indices"));
                    else {
                        indices=new int[verts.length/3];
                        for(int i=0;i<indices.length;i++)indices[i]=i;
                    }
                    ArrayList<int[]> faces=new ArrayList<>();
                    for(int i=0;i+2<indices.length;i+=3)
                        faces.add(new int[]{indices[i],indices[i+1],indices[i+2]});
                    if(!faces.isEmpty()){
                        String name=node.optString("name",mesh.optString("name","Imported GLB"));
                        result.add(new SceneItem(new MeshData(name,verts,faces),tx,ty,tz));
                    }
                }
            }
        }

        if(result.isEmpty()){
            for(int mi=0;mi<meshes.length();mi++){
                JSONArray primitives=meshes.getJSONObject(mi).getJSONArray("primitives");
                for(int pi=0;pi<primitives.length();pi++){
                    JSONObject primitive=primitives.getJSONObject(pi);
                    JSONObject attrs=primitive.getJSONObject("attributes");
                    float[] verts=readPositions(bin,views,accessors,attrs.getInt("POSITION"));
                    int[] indices=primitive.has("indices")?
                        readIndices(bin,views,accessors,primitive.getInt("indices")):
                        sequential(verts.length/3);
                    ArrayList<int[]> faces=new ArrayList<>();
                    for(int i=0;i+2<indices.length;i+=3)faces.add(new int[]{indices[i],indices[i+1],indices[i+2]});
                    if(!faces.isEmpty())result.add(new SceneItem(new MeshData("Imported GLB",verts,faces),0,0,0));
                }
            }
        }
        if(result.isEmpty())throw new IOException("No triangle mesh geometry found in GLB.");
        return result;
    }

    private static float[] readPositions(byte[] bin,JSONArray views,JSONArray accessors,int ai)throws Exception{
        JSONObject a=accessors.getJSONObject(ai);
        if(!"VEC3".equals(a.getString("type"))||a.getInt("componentType")!=5126)
            throw new IOException("GLB POSITION must use FLOAT VEC3.");
        int count=a.getInt("count"), vi=a.getInt("bufferView");
        JSONObject v=views.getJSONObject(vi);
        int stride=v.optInt("byteStride",12);
        int start=v.optInt("byteOffset",0)+a.optInt("byteOffset",0);
        ByteBuffer b=ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN);
        float[] out=new float[count*3];
        for(int i=0;i<count;i++)for(int k=0;k<3;k++){
            int at=start+i*stride+k*4;
            if(at<0||at+4>bin.length)throw new IOException("GLB vertex data out of range.");
            out[i*3+k]=b.getFloat(at);
        }
        return out;
    }

    private static int[] readIndices(byte[] bin,JSONArray views,JSONArray accessors,int ai)throws Exception{
        JSONObject a=accessors.getJSONObject(ai);
        int component=a.getInt("componentType"),count=a.getInt("count");
        JSONObject v=views.getJSONObject(a.getInt("bufferView"));
        int start=v.optInt("byteOffset",0)+a.optInt("byteOffset",0);
        ByteBuffer b=ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN);
        int size=component==5121?1:component==5123?2:component==5125?4:0;
        if(size==0)throw new IOException("Unsupported GLB index type.");
        int[] out=new int[count];
        for(int i=0;i<count;i++){
            int at=start+i*size;
            if(at<0||at+size>bin.length)throw new IOException("GLB index data out of range.");
            out[i]=component==5121?(b.get(at)&255):component==5123?(b.getShort(at)&65535):b.getInt(at);
        }
        return out;
    }

    private static int[] sequential(int n){int[] a=new int[n];for(int i=0;i<n;i++)a[i]=i;return a;}

    // --------------------------------------------------
    // GLB 2.0 EXPORT - geometry, indices, object names
    // and translations. Exports triangles.
    // --------------------------------------------------

    public static void exportGLB(OutputStream output,ArrayList<SceneItem> scene)throws Exception{
        ByteArrayOutputStream binOut=new ByteArrayOutputStream();
        JSONArray bufferViews=new JSONArray(),accessors=new JSONArray();
        JSONArray meshes=new JSONArray(),nodes=new JSONArray();
        JSONArray scenes=new JSONArray();
        JSONArray sceneNodes=new JSONArray();

        for(SceneItem item:scene){
            MeshData m=item.mesh;
            int vertexOffset=binOut.size();
            ByteBuffer vb=ByteBuffer.allocate(m.vertices.length*4).order(ByteOrder.LITTLE_ENDIAN);
            for(float f:m.vertices)vb.putFloat(f);
            binOut.write(vb.array());

            int indexCount=0;
            for(int[] face:m.faces)indexCount+=(face.length-2)*3;
            int[] indices=new int[indexCount];
            int ip=0;
            for(int[] face:m.faces)for(int i=1;i<face.length-1;i++){
                indices[ip++]=face[0];indices[ip++]=face[i];indices[ip++]=face[i+1];
            }
            while(binOut.size()%4!=0)binOut.write(0);
            int indexOffset=binOut.size();
            ByteBuffer ib=ByteBuffer.allocate(indices.length*4).order(ByteOrder.LITTLE_ENDIAN);
            for(int index:indices)ib.putInt(index);
            binOut.write(ib.array());

            JSONObject posView=new JSONObject();
            posView.put("buffer",0);posView.put("byteOffset",vertexOffset);
            posView.put("byteLength",m.vertices.length*4);posView.put("target",34962);
            bufferViews.put(posView);

            JSONObject idxView=new JSONObject();
            idxView.put("buffer",0);idxView.put("byteOffset",indexOffset);
            idxView.put("byteLength",indices.length*4);idxView.put("target",34963);
            bufferViews.put(idxView);

            float[] min={Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE};
            float[] max={-Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE};
            for(int i=0;i<m.vertices.length;i+=3)for(int k=0;k<3;k++){
                min[k]=Math.min(min[k],m.vertices[i+k]);max[k]=Math.max(max[k],m.vertices[i+k]);
            }

            JSONObject pa=new JSONObject();
            pa.put("bufferView",bufferViews.length()-2);pa.put("componentType",5126);
            pa.put("count",m.getVertexCount());pa.put("type","VEC3");
            JSONArray mn=new JSONArray(),mx=new JSONArray();
            for(int k=0;k<3;k++){mn.put(min[k]);mx.put(max[k]);}
            pa.put("min",mn);pa.put("max",mx);accessors.put(pa);

            JSONObject ia=new JSONObject();
            ia.put("bufferView",bufferViews.length()-1);ia.put("componentType",5125);
            ia.put("count",indices.length);ia.put("type","SCALAR");accessors.put(ia);

            JSONObject attributes=new JSONObject();attributes.put("POSITION",accessors.length()-1);
            JSONObject primitive=new JSONObject();primitive.put("attributes",attributes);
            primitive.put("indices",accessors.length());primitive.put("mode",4);
            JSONArray prims=new JSONArray();prims.put(primitive);
            JSONObject mesh=new JSONObject();mesh.put("name",safeName(m.name));mesh.put("primitives",prims);
            meshes.put(mesh);

            JSONObject node=new JSONObject();node.put("name",safeName(m.name));node.put("mesh",meshes.length()-1);
            JSONArray tr=new JSONArray();tr.put(item.x);tr.put(item.y);tr.put(item.z);node.put("translation",tr);
            nodes.put(node);sceneNodes.put(nodes.length()-1);
        }

        JSONObject sceneObject=new JSONObject();sceneObject.put("nodes",sceneNodes);scenes.put(sceneObject);
        JSONObject buffer=new JSONObject();buffer.put("byteLength",binOut.size());
        JSONArray buffers=new JSONArray();buffers.put(buffer);

        JSONObject asset=new JSONObject();asset.put("version","2.0");asset.put("generator","3D Modeling App");
        JSONObject root=new JSONObject();
        root.put("asset",asset);root.put("scene",0);root.put("scenes",scenes);
        root.put("nodes",nodes);root.put("meshes",meshes);
        root.put("buffers",buffers);root.put("bufferViews",bufferViews);root.put("accessors",accessors);

        byte[] json=root.toString().getBytes(StandardCharsets.UTF_8);
        int jsonLength=(json.length+3)&~3;
        byte[] binary=binOut.toByteArray();
        int binLength=(binary.length+3)&~3;
        int total=12+8+jsonLength+8+binLength;

        DataOutputStream out=new DataOutputStream(output);
        writeLE(out,0x46546C67);writeLE(out,2);writeLE(out,total);
        writeLE(out,jsonLength);writeLE(out,0x4E4F534A);
        out.write(json);for(int i=json.length;i<jsonLength;i++)out.write(0x20);
        writeLE(out,binLength);writeLE(out,0x004E4942);
        out.write(binary);for(int i=binary.length;i<binLength;i++)out.write(0);
        out.flush();
    }

    private static void writeLE(DataOutputStream out,int value)throws IOException{
        out.write(value&255);out.write((value>>>8)&255);out.write((value>>>16)&255);out.write((value>>>24)&255);
    }

    private static byte[] readAll(InputStream in)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] buffer=new byte[8192];int n;
        while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
        return out.toByteArray();
    }
}

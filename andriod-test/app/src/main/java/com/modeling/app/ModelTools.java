
package com.modeling.app;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

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

        public int getVertexCount() {
            return vertices.length / 3;
        }

        public int getFaceCount() {
            return faces.size();
        }

        public int getEdgeCount() {
            Set<Long> edges = new HashSet<>();

            for (int[] face : faces) {
                if (face.length < 2) continue;

                for (int i = 0; i < face.length; i++) {
                    int a = face[i];
                    int b = face[(i + 1) % face.length];

                    int low = Math.min(a, b);
                    int high = Math.max(a, b);

                    long key = (((long) low) << 32) | (high & 0xffffffffL);
                    edges.add(key);
                }
            }

            return edges.size();
        }

        public float[] getTriangleVertices() {
            ArrayList<Float> result = new ArrayList<>();

            for (int[] face : faces) {
                if (face.length < 3) continue;

                // Triangle fan for polygon faces.
                for (int i = 1; i < face.length - 1; i++) {
                    addVertex(result, face[0]);
                    addVertex(result, face[i]);
                    addVertex(result, face[i + 1]);
                }
            }

            float[] output = new float[result.size()];
            for (int i = 0; i < result.size(); i++) {
                output[i] = result.get(i);
            }

            return output;
        }

        private void addVertex(ArrayList<Float> output, int index) {
            int offset = index * 3;
            output.add(vertices[offset]);
            output.add(vertices[offset + 1]);
            output.add(vertices[offset + 2]);
        }
    }

    public static class SceneItem {
        public MeshData mesh;
        public float x, y, z;

        public SceneItem(MeshData mesh, float x, float y, float z) {
            this.mesh = mesh;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    // --------------------------------------------------
    // BUILT-IN CUBE
    // 8 vertices, 12 edges, 6 faces
    // --------------------------------------------------

    public static MeshData createCube() {
        float s = 0.75f;

        float[] v = {
            -s,-s,-s,  s,-s,-s,  s,s,-s,  -s,s,-s,
            -s,-s,s,   s,-s,s,   s,s,s,   -s,s,s
        };

        ArrayList<int[]> f = new ArrayList<>();

        f.add(new int[]{0, 3, 2, 1});
        f.add(new int[]{4, 5, 6, 7});
        f.add(new int[]{0, 1, 5, 4});
        f.add(new int[]{3, 7, 6, 2});
        f.add(new int[]{0, 4, 7, 3});
        f.add(new int[]{1, 2, 6, 5});

        return new MeshData("Cube", v, f);
    }

    // --------------------------------------------------
    // BUILT-IN CYLINDER
    // 32 segments: 34 vertices, 96 edges, 34 faces
    // --------------------------------------------------

    public static MeshData createCylinder() {
        final int segments = 32;
        final float radius = 0.65f;
        final float halfHeight = 0.75f;

        float[] v = new float[(segments * 2 + 2) * 3];

        for (int i = 0; i < segments; i++) {
            double angle = 2.0 * Math.PI * i / segments;
            float x = (float) Math.cos(angle) * radius;
            float z = (float) Math.sin(angle) * radius;

            setVertex(v, i, x, -halfHeight, z);
            setVertex(v, segments + i, x, halfHeight, z);
        }

        int bottomCenter = segments * 2;
        int topCenter = bottomCenter + 1;

        setVertex(v, bottomCenter, 0, -halfHeight, 0);
        setVertex(v, topCenter, 0, halfHeight, 0);

        ArrayList<int[]> f = new ArrayList<>();

        for (int i = 0; i < segments; i++) {
            int next = (i + 1) % segments;

            f.add(new int[]{
                i, next, segments + next, segments + i
            });

            f.add(new int[]{bottomCenter, next, i});
            f.add(new int[]{topCenter, segments + i, segments + next});
        }

        return new MeshData("Cylinder", v, f);
    }

    // --------------------------------------------------
    // BUILT-IN UV SPHERE
    // --------------------------------------------------

    public static MeshData createSphere() {
        final int rings = 20;
        final int segments = 32;
        final float radius = 0.8f;

        ArrayList<Float> vertices = new ArrayList<>();
        ArrayList<int[]> faces = new ArrayList<>();

        // Top pole.
        addVertex(vertices, 0, radius, 0);

        // Intermediate latitude rings.
        for (int ring = 1; ring < rings; ring++) {
            double latitude = Math.PI * (-0.5 + (double) ring / rings);

            for (int segment = 0; segment < segments; segment++) {
                double longitude = 2.0 * Math.PI * segment / segments;

                float x = (float) (Math.cos(latitude) * Math.cos(longitude) * radius);
                float y = (float) (Math.sin(latitude) * radius);
                float z = (float) (Math.cos(latitude) * Math.sin(longitude) * radius);

                addVertex(vertices, x, y, z);
            }
        }

        // Bottom pole.
        int bottomIndex = 1 + (rings - 1) * segments;
        addVertex(vertices, 0, -radius, 0);

        // Top triangles.
        for (int i = 0; i < segments; i++) {
            int next = (i + 1) % segments;
            faces.add(new int[]{0, 1 + i, 1 + next});
        }

        // Middle quads.
        for (int ring = 0; ring < rings - 2; ring++) {
            int first = 1 + ring * segments;
            int second = first + segments;

            for (int i = 0; i < segments; i++) {
                int next = (i + 1) % segments;

                faces.add(new int[]{
                    first + i,
                    second + i,
                    second + next,
                    first + next
                });
            }
        }

        // Bottom triangles.
        int lastRing = 1 + (rings - 2) * segments;

        for (int i = 0; i < segments; i++) {
            int next = (i + 1) % segments;
            faces.add(new int[]{
                bottomIndex,
                lastRing + next,
                lastRing + i
            });
        }

        return new MeshData("Sphere", toArray(vertices), faces);
    }

    private static void setVertex(
        float[] vertices, int index, float x, float y, float z
    ) {
        int p = index * 3;
        vertices[p] = x;
        vertices[p + 1] = y;
        vertices[p + 2] = z;
    }

    private static void addVertex(
        ArrayList<Float> vertices, float x, float y, float z
    ) {
        vertices.add(x);
        vertices.add(y);
        vertices.add(z);
    }

    private static float[] toArray(ArrayList<Float> values) {
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) result[i] = values.get(i);
        return result;
    }

    // --------------------------------------------------
    // OBJ IMPORT
    // Supports vertices and polygon faces.
    // Supports positive and negative OBJ vertex indices.
    // --------------------------------------------------

    public static MeshData importOBJ(InputStream input) throws IOException {
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(input, "UTF-8")
        );

        ArrayList<Float> vertices = new ArrayList<>();
        ArrayList<int[]> faces = new ArrayList<>();

        String line;

        while ((line = reader.readLine()) != null) {
            line = line.trim();

            if (line.isEmpty() || line.startsWith("#")) continue;

            String[] parts = line.split("\\s+");

            if (parts[0].equals("v")) {
                if (parts.length < 4) continue;

                float x = Float.parseFloat(parts[1]);
                float y = Float.parseFloat(parts[2]);
                float z = Float.parseFloat(parts[3]);

                if (!Float.isFinite(x) || !Float.isFinite(y) ||
                    !Float.isFinite(z)) {
                    throw new IOException("OBJ contains invalid vertex data.");
                }

                addVertex(vertices, x, y, z);
            }
            else if (parts[0].equals("f")) {
                if (parts.length < 4) continue;

                int vertexCount = vertices.size() / 3;
                int[] face = new int[parts.length - 1];

                for (int i = 1; i < parts.length; i++) {
                    String vertexPart = parts[i].split("/")[0];
                    int index = Integer.parseInt(vertexPart);

                    if (index < 0) {
                        index = vertexCount + index;
                    } else {
                        index = index - 1;
                    }

                    if (index < 0 || index >= vertexCount) {
                        throw new IOException("OBJ contains an invalid face index.");
                    }

                    face[i - 1] = index;
                }

                faces.add(face);
            }
        }

        reader.close();

        if (vertices.isEmpty() || faces.isEmpty()) {
            throw new IOException("No usable mesh geometry was found in this OBJ.");
        }

        return new MeshData("Imported OBJ", toArray(vertices), faces);
    }

    // --------------------------------------------------
    // OBJ EXPORT
    // Exports all scene objects into one OBJ file.
    // --------------------------------------------------

    public static void exportOBJ(
        OutputStream output,
        ArrayList<SceneItem> scene
    ) throws IOException {

        BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(output, "UTF-8")
        );

        writer.write("# Exported from 3D Modeling App\n");

        int vertexOffset = 1;

        for (SceneItem item : scene) {
            MeshData mesh = item.mesh;

            writer.write("o " + safeName(mesh.name) + "\n");

            for (int i = 0; i < mesh.vertices.length; i += 3) {
                float x = mesh.vertices[i] + item.x;
                float y = mesh.vertices[i + 1] + item.y;
                float z = mesh.vertices[i + 2] + item.z;

                writer.write(String.format(
                    Locale.US, "v %.9g %.9g %.9g\n", x, y, z
                ));
            }

            for (int[] face : mesh.faces) {
                writer.write("f");

                for (int index : face) {
                    writer.write(" " + (index + vertexOffset));
                }

                writer.write("\n");
            }

            vertexOffset += mesh.getVertexCount();
        }

        writer.flush();
    }

    private static String safeName(String name) {
        if (name == null || name.trim().isEmpty()) return "Object";
        return name.trim().replaceAll("[^A-Za-z0-9_-]", "_");
    }
}

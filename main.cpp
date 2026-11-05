
#define NOMINMAX

#include <windows.h>
#include <commdlg.h>

#include <GL/gl.h>
#include <GLFW/glfw3.h>

#include <iostream>
#include <vector>
#include <cmath>
#include <fstream>
#include <string>
#include <cstring>

using namespace std;

// ==================================================
// UI FUNCTION DECLARATIONS
// No ui.h required
// ==================================================

void UI_Init();
void UI_Draw(int width, int height);
int UI_HandleMouseClick(double mouse_x, double mouse_y);
void UI_Shutdown();

// UI action numbers must match ui.cpp
enum UIAction
{
    UI_ACTION_NONE = 0,
    UI_ACTION_ADD_CUBE = 1,
    UI_ACTION_ADD_CYLINDER = 2,
    UI_ACTION_ADD_SPHERE = 3,
    UI_ACTION_SAVE = 4,
    UI_ACTION_OPEN = 5,
    UI_ACTION_EXPORT = 6,
    UI_ACTION_IMPORT = 7
};

// ==================================================
// OBJECTS
// ==================================================

enum ObjectType
{
    CUBE,
    CYLINDER,
    SPHERE
};

struct SceneObject
{
    ObjectType type;

    float x;
    float y;
    float z;
};

vector<SceneObject> objects;

// ==================================================
// CAMERA
// ==================================================

float camera_yaw = 0.8f;
float camera_pitch = 0.4f;
float camera_distance = 8.0f;

bool rotating_camera = false;

double last_mouse_x = 0.0;
double last_mouse_y = 0.0;

// ==================================================
// ADD OBJECT
// ==================================================

void add_object(ObjectType type)
{
    SceneObject object;

    object.type = type;

    // Spawn at world origin
    object.x = 0.0f;
    object.y = 0.0f;
    object.z = 0.0f;

    objects.push_back(object);

    cout << "Object added at origin." << endl;
}

// ==================================================
// SAVE PROJECT DIALOG
// ==================================================

string save_file_dialog()
{
    char filename[MAX_PATH] = "";

    OPENFILENAMEA ofn = {};

    ofn.lStructSize = sizeof(ofn);
    ofn.hwndOwner = GetActiveWindow();

    ofn.lpstrFilter =
        "3D Editor Project (*.Ac)\0*.Ac\0"
        "All Files (*.*)\0*.*\0";

    ofn.lpstrFile = filename;
    ofn.nMaxFile = MAX_PATH;
    ofn.lpstrDefExt = "Ac";

    ofn.Flags =
        OFN_OVERWRITEPROMPT |
        OFN_PATHMUSTEXIST;

    if (GetSaveFileNameA(&ofn))
        return string(filename);

    return "";
}

// ==================================================
// OPEN PROJECT DIALOG
// ==================================================

string open_file_dialog()
{
    char filename[MAX_PATH] = "";

    OPENFILENAMEA ofn = {};

    ofn.lStructSize = sizeof(ofn);
    ofn.hwndOwner = GetActiveWindow();

    ofn.lpstrFilter =
        "3D Editor Project (*.Ac)\0*.Ac\0"
        "All Files (*.*)\0*.*\0";

    ofn.lpstrFile = filename;
    ofn.nMaxFile = MAX_PATH;
    ofn.lpstrDefExt = "Ac";

    ofn.Flags =
        OFN_FILEMUSTEXIST |
        OFN_PATHMUSTEXIST;

    if (GetOpenFileNameA(&ofn))
        return string(filename);

    return "";
}

// ==================================================
// SAVE PROJECT
// ==================================================

void save_project()
{
    string path = save_file_dialog();

    if (path.empty())
        return;

    ofstream file(path.c_str());

    if (!file.is_open())
    {
        cout << "Could not save project!" << endl;
        return;
    }

    file << "AC_PROJECT 1\n";

    // Camera
    file << camera_yaw << " "
         << camera_pitch << " "
         << camera_distance << "\n";

    // Object count
    file << objects.size() << "\n";

    // Object data
    for (size_t i = 0; i < objects.size(); i++)
    {
        file << (int)objects[i].type << " "
             << objects[i].x << " "
             << objects[i].y << " "
             << objects[i].z << "\n";
    }

    file.close();

    cout << "Project saved: " << path << endl;
}

// ==================================================
// OPEN PROJECT
// ==================================================

void open_project()
{
    string path = open_file_dialog();

    if (path.empty())
        return;

    ifstream file(path.c_str());

    if (!file.is_open())
    {
        cout << "Could not open project!" << endl;
        return;
    }

    string header;
    int version = 0;

    file >> header >> version;

    if (!file || header != "AC_PROJECT" || version != 1)
    {
        cout << "Invalid or unsupported project file!" << endl;
        return;
    }

    // Temporary camera data
    float loaded_yaw;
    float loaded_pitch;
    float loaded_distance;

    file >> loaded_yaw
         >> loaded_pitch
         >> loaded_distance;

    if (!file ||
        !isfinite(loaded_yaw) ||
        !isfinite(loaded_pitch) ||
        !isfinite(loaded_distance) ||
        loaded_pitch < -1.5f ||
        loaded_pitch > 1.5f ||
        loaded_distance < 2.0f ||
        loaded_distance > 30.0f)
    {
        cout << "Invalid camera data!" << endl;
        return;
    }

    // Object count
    size_t object_count = 0;

    file >> object_count;

    if (!file || object_count > 100000)
    {
        cout << "Invalid object count!" << endl;
        return;
    }

    vector<SceneObject> loaded_objects;

    for (size_t i = 0; i < object_count; i++)
    {
        SceneObject object;

        int type;

        file >> type
             >> object.x
             >> object.y
             >> object.z;

        if (!file ||
            type < CUBE ||
            type > SPHERE ||
            !isfinite(object.x) ||
            !isfinite(object.y) ||
            !isfinite(object.z))
        {
            cout << "Invalid object data!" << endl;
            return;
        }

        object.type = (ObjectType)type;

        loaded_objects.push_back(object);
    }

    // Replace current scene only after successful loading
    objects = loaded_objects;

    camera_yaw = loaded_yaw;
    camera_pitch = loaded_pitch;
    camera_distance = loaded_distance;

    cout << "Project loaded: " << path << endl;
}

// ==================================================
// CUBE
// ==================================================

void draw_cube()
{
    float s = 0.75f;

    glBegin(GL_QUADS);

    // Front
    glVertex3f(-s, -s, s);
    glVertex3f(s, -s, s);
    glVertex3f(s, s, s);
    glVertex3f(-s, s, s);

    // Back
    glVertex3f(-s, -s, -s);
    glVertex3f(-s, s, -s);
    glVertex3f(s, s, -s);
    glVertex3f(s, -s, -s);

    // Left
    glVertex3f(-s, -s, -s);
    glVertex3f(-s, -s, s);
    glVertex3f(-s, s, s);
    glVertex3f(-s, s, -s);

    // Right
    glVertex3f(s, -s, -s);
    glVertex3f(s, s, -s);
    glVertex3f(s, s, s);
    glVertex3f(s, -s, s);

    // Top
    glVertex3f(-s, s, -s);
    glVertex3f(-s, s, s);
    glVertex3f(s, s, s);
    glVertex3f(s, s, -s);

    // Bottom
    glVertex3f(-s, -s, -s);
    glVertex3f(s, -s, -s);
    glVertex3f(s, -s, s);
    glVertex3f(-s, -s, s);

    glEnd();
}

// ==================================================
// CYLINDER
// ==================================================

void draw_cylinder()
{
    const float PI = 3.14159265f;

    float radius = 0.65f;
    float height = 1.5f;

    int segments = 32;

    glBegin(GL_QUAD_STRIP);

    for (int i = 0; i <= segments; i++)
    {
        float angle = (2.0f * PI * i) / segments;

        float x = cos(angle) * radius;
        float z = sin(angle) * radius;

        glVertex3f(x, -height / 2.0f, z);
        glVertex3f(x, height / 2.0f, z);
    }

    glEnd();

    // Top
    glBegin(GL_TRIANGLE_FAN);

    glVertex3f(0, height / 2.0f, 0);

    for (int i = 0; i <= segments; i++)
    {
        float angle = (2.0f * PI * i) / segments;

        glVertex3f(
            cos(angle) * radius,
            height / 2.0f,
            sin(angle) * radius
        );
    }

    glEnd();

    // Bottom
    glBegin(GL_TRIANGLE_FAN);

    glVertex3f(0, -height / 2.0f, 0);

    for (int i = segments; i >= 0; i--)
    {
        float angle = (2.0f * PI * i) / segments;

        glVertex3f(
            cos(angle) * radius,
            -height / 2.0f,
            sin(angle) * radius
        );
    }

    glEnd();
}

// ==================================================
// SPHERE
// ==================================================

void draw_sphere()
{
    const float PI = 3.14159265f;

    float radius = 0.8f;

    int rings = 20;
    int segments = 32;

    for (int i = 0; i < rings; i++)
    {
        float lat1 = PI * (-0.5f + (float)i / rings);
        float lat2 = PI * (-0.5f + (float)(i + 1) / rings);

        glBegin(GL_QUAD_STRIP);

        for (int j = 0; j <= segments; j++)
        {
            float longitude = 2.0f * PI * (float)j / segments;

            float x1 = cos(lat1) * cos(longitude);
            float y1 = sin(lat1);
            float z1 = cos(lat1) * sin(longitude);

            float x2 = cos(lat2) * cos(longitude);
            float y2 = sin(lat2);
            float z2 = cos(lat2) * sin(longitude);

            glVertex3f(x1 * radius, y1 * radius, z1 * radius);
            glVertex3f(x2 * radius, y2 * radius, z2 * radius);
        }

        glEnd();
    }
}

// ==================================================
// GRID
// ==================================================

void draw_grid()
{
    glLineWidth(1.0f);

    glBegin(GL_LINES);

    for (int i = -20; i <= 20; i++)
    {
        if (i == 0)
            glColor3f(0.45f, 0.45f, 0.45f);
        else
            glColor3f(0.22f, 0.22f, 0.22f);

        glVertex3f((float)i, -1.01f, -20);
        glVertex3f((float)i, -1.01f, 20);

        glVertex3f(-20, -1.01f, (float)i);
        glVertex3f(20, -1.01f, (float)i);
    }

    glEnd();
}

// ==================================================
// DRAW OBJECTS
// ==================================================

void draw_objects()
{
    for (size_t i = 0; i < objects.size(); i++)
    {
        glPushMatrix();

        glTranslatef(
            objects[i].x,
            objects[i].y,
            objects[i].z
        );

        glColor3f(0.65f, 0.65f, 0.65f);

        if (objects[i].type == CUBE)
            draw_cube();

        else if (objects[i].type == CYLINDER)
            draw_cylinder();

        else if (objects[i].type == SPHERE)
            draw_sphere();

        glPopMatrix();
    }
}

// ==================================================
// MOUSE INPUT
// ==================================================

void mouse_button_callback(
    GLFWwindow* window,
    int button,
    int action,
    int mods)
{
    // Camera rotation
    if (button == GLFW_MOUSE_BUTTON_RIGHT)
    {
        if (action == GLFW_PRESS)
        {
            rotating_camera = true;

            glfwGetCursorPos(
                window,
                &last_mouse_x,
                &last_mouse_y
            );
        }
        else if (action == GLFW_RELEASE)
        {
            rotating_camera = false;
        }
    }

    // UI clicks
    if (button == GLFW_MOUSE_BUTTON_LEFT &&
        action == GLFW_PRESS)
    {
        double mouse_x;
        double mouse_y;

        glfwGetCursorPos(window, &mouse_x, &mouse_y);

        int result = UI_HandleMouseClick(mouse_x, mouse_y);

        switch (result)
        {
            case UI_ACTION_ADD_CUBE:
                add_object(CUBE);
                break;

            case UI_ACTION_ADD_CYLINDER:
                add_object(CYLINDER);
                break;

            case UI_ACTION_ADD_SPHERE:
                add_object(SPHERE);
                break;

            case UI_ACTION_SAVE:
                save_project();
                break;

            case UI_ACTION_OPEN:
                open_project();
                break;

            case UI_ACTION_EXPORT:
                cout << "Export is coming later." << endl;
                break;

            case UI_ACTION_IMPORT:
                cout << "Import is coming later." << endl;
                break;

            default:
                break;
        }
    }
}

// ==================================================
// CAMERA ROTATION
// ==================================================

void cursor_position_callback(
    GLFWwindow* window,
    double mouse_x,
    double mouse_y)
{
    if (rotating_camera)
    {
        double delta_x = mouse_x - last_mouse_x;
        double delta_y = mouse_y - last_mouse_y;

        camera_yaw += (float)delta_x * 0.005f;
        camera_pitch += (float)delta_y * 0.005f;

        if (camera_pitch > 1.5f)
            camera_pitch = 1.5f;

        if (camera_pitch < -1.5f)
            camera_pitch = -1.5f;
    }

    last_mouse_x = mouse_x;
    last_mouse_y = mouse_y;
}

// ==================================================
// CAMERA ZOOM
// ==================================================

void scroll_callback(
    GLFWwindow* window,
    double xoffset,
    double yoffset)
{
    camera_distance -= (float)yoffset * 0.5f;

    if (camera_distance < 2.0f)
        camera_distance = 2.0f;

    if (camera_distance > 30.0f)
        camera_distance = 30.0f;
}

// ==================================================
// MAIN
// ==================================================

int main()
{
    if (!glfwInit())
    {
        cout << "GLFW initialization failed!" << endl;
        return -1;
    }

    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 2);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 0);

    GLFWwindow* window = glfwCreateWindow(
        900,
        600,
        "3D Modeling Workspace",
        NULL,
        NULL
    );

    if (!window)
    {
        cout << "Window creation failed!" << endl;
        glfwTerminate();
        return -1;
    }

    glfwMakeContextCurrent(window);

    glfwSetMouseButtonCallback(window, mouse_button_callback);
    glfwSetCursorPosCallback(window, cursor_position_callback);
    glfwSetScrollCallback(window, scroll_callback);

    glfwSwapInterval(1);

    glDisable(GL_TEXTURE_2D);
    glEnable(GL_DEPTH_TEST);

    UI_Init();

    // ==================================================
    // MAIN LOOP
    // ==================================================

    while (!glfwWindowShouldClose(window))
    {
        int width;
        int height;

        glfwGetFramebufferSize(window, &width, &height);

        if (height == 0)
            height = 1;

        glViewport(0, 0, width, height);

        glClearColor(0.075f, 0.075f, 0.075f, 1.0f);

        glClear(
            GL_COLOR_BUFFER_BIT |
            GL_DEPTH_BUFFER_BIT
        );

        // Projection
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();

        float aspect = (float)width / (float)height;

        float near_plane = 0.1f;
        float far_plane = 100.0f;

        float top =
            near_plane * tan(45.0f * 3.14159265f / 360.0f);

        float right = top * aspect;

        glFrustum(
            -right, right,
            -top, top,
            near_plane, far_plane
        );

        // Camera
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        glTranslatef(0.0f, 0.0f, -camera_distance);

        glRotatef(
            camera_pitch * 57.2958f,
            1.0f, 0.0f, 0.0f
        );

        glRotatef(
            camera_yaw * 57.2958f,
            0.0f, 1.0f, 0.0f
        );

        // Scene
        draw_grid();
        draw_objects();

        // Draw UI in window coordinates
        int window_width;
        int window_height;

        glfwGetWindowSize(
            window,
            &window_width,
            &window_height
        );

        UI_Draw(window_width, window_height);

        glfwSwapBuffers(window);
        glfwPollEvents();
    }

    UI_Shutdown();

    glfwDestroyWindow(window);
    glfwTerminate();

    return 0;
}

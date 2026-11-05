
#include <windows.h>
#include <GL/gl.h>
#include <cstring>
// Action numbers returned to main.cpp
// 0 = Nothing
// 1 = Add Cube
// 2 = Add Cylinder
// 3 = Add Sphere
// 4 = Save
// 5 = Open
// 6 = Export
// 7 = Import

static int activeMenu = 0;
// 0 = no menu
// 1 = File
// 2 = Add

static GLuint fontList = 0;

static void DrawText(float x, float y, const char* text)
{
    if (fontList == 0)
        return;

    glRasterPos2f(x, y);
    glListBase(fontList);
    glCallLists((GLsizei)strlen(text), GL_UNSIGNED_BYTE, text);
}

static void DrawMenuButton(
    float x,
    float y,
    float width,
    const char* text,
    bool selected
)
{
    if (selected)
        glColor3f(0.25f, 0.25f, 0.25f);
    else
        glColor3f(0.12f, 0.12f, 0.12f);

    glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + 30);
        glVertex2f(x, y + 30);
    glEnd();

    glColor3f(0.85f, 0.85f, 0.85f);
    DrawText(x + 8, y + 10, text);
}

static void DrawDropdownItem(
    float x,
    float y,
    float width,
    const char* text
)
{
    glColor3f(0.16f, 0.16f, 0.16f);

    glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + 30);
        glVertex2f(x, y + 30);
    glEnd();

    glColor3f(0.9f, 0.9f, 0.9f);
    DrawText(x + 10, y + 10, text);
}

void UI_Init()
{
    HDC dc = wglGetCurrentDC();

    if (!dc)
        return;

    fontList = glGenLists(256);

    HFONT font = CreateFontA(
        16,
        0,
        0,
        0,
        FW_NORMAL,
        FALSE,
        FALSE,
        FALSE,
        ANSI_CHARSET,
        OUT_TT_PRECIS,
        CLIP_DEFAULT_PRECIS,
        ANTIALIASED_QUALITY,
        FF_DONTCARE,
        "Arial"
    );

    if (font)
    {
        HGDIOBJ oldFont = SelectObject(dc, font);

        wglUseFontBitmapsA(dc, 0, 256, fontList);

        SelectObject(dc, oldFont);
        DeleteObject(font);
    }
}

void UI_Draw(int width, int height)
{
    glMatrixMode(GL_PROJECTION);
    glPushMatrix();
    glLoadIdentity();
    glOrtho(0, width, height, 0, -1, 1);

    glMatrixMode(GL_MODELVIEW);
    glPushMatrix();
    glLoadIdentity();

    glDisable(GL_DEPTH_TEST);
    glDisable(GL_LIGHTING);

    // Menu bar background
    glColor3f(0.08f, 0.08f, 0.08f);

    glBegin(GL_QUADS);
        glVertex2f(0, 0);
        glVertex2f(width, 0);
        glVertex2f(width, 30);
        glVertex2f(0, 30);
    glEnd();

    // Main menu buttons
    DrawMenuButton(10, 0, 48, "File", activeMenu == 1);
    DrawMenuButton(62, 0, 48, "Edit", false);
    DrawMenuButton(114, 0, 65, "Select", false);
    DrawMenuButton(183, 0, 48, "Add", activeMenu == 2);

    // Thin separators
    glColor3f(0.35f, 0.35f, 0.35f);

    glBegin(GL_LINES);
        glVertex2f(58, 6);
        glVertex2f(58, 24);

        glVertex2f(110, 6);
        glVertex2f(110, 24);

        glVertex2f(179, 6);
        glVertex2f(179, 24);
    glEnd();

    // File dropdown
    if (activeMenu == 1)
    {
        DrawDropdownItem(10, 30, 165, "Save");
        DrawDropdownItem(10, 60, 165, "Open");
        DrawDropdownItem(10, 90, 165, "Export");
        DrawDropdownItem(10, 120, 165, "Import");

        glColor3f(0.35f, 0.35f, 0.35f);

        glBegin(GL_LINES);
            glVertex2f(10, 30);
            glVertex2f(175, 30);
            glVertex2f(10, 150);
            glVertex2f(175, 150);
        glEnd();
    }

    // Add dropdown
    if (activeMenu == 2)
    {
        DrawDropdownItem(183, 30, 125, "Cube");
        DrawDropdownItem(183, 60, 125, "Cylinder");
        DrawDropdownItem(183, 90, 125, "Sphere");

        glColor3f(0.35f, 0.35f, 0.35f);

        glBegin(GL_LINES);
            glVertex2f(183, 30);
            glVertex2f(308, 30);
            glVertex2f(183, 120);
            glVertex2f(308, 120);
        glEnd();
    }

    glEnable(GL_DEPTH_TEST);

    glMatrixMode(GL_MODELVIEW);
    glPopMatrix();

    glMatrixMode(GL_PROJECTION);
    glPopMatrix();

    glMatrixMode(GL_MODELVIEW);
}

int UI_HandleMouseClick(double mouse_x, double mouse_y)
{
    // Main menu bar
    if (mouse_y >= 0 && mouse_y <= 30)
    {
        if (mouse_x >= 10 && mouse_x <= 58)
        {
            activeMenu = (activeMenu == 1) ? 0 : 1;
            return 0;
        }

        if (mouse_x >= 183 && mouse_x <= 231)
        {
            activeMenu = (activeMenu == 2) ? 0 : 2;
            return 0;
        }

        // Edit and Select currently do nothing
        if ((mouse_x >= 62 && mouse_x <= 110) ||
            (mouse_x >= 114 && mouse_x <= 179))
        {
            activeMenu = 0;
            return 0;
        }

        activeMenu = 0;
        return 0;
    }

    // File dropdown
    if (activeMenu == 1)
    {
        if (mouse_x >= 10 && mouse_x <= 175)
        {
            if (mouse_y >= 30 && mouse_y < 60)
            {
                activeMenu = 0;
                return 4; // Save
            }

            if (mouse_y >= 60 && mouse_y < 90)
            {
                activeMenu = 0;
                return 5; // Open
            }

            if (mouse_y >= 90 && mouse_y < 120)
            {
                activeMenu = 0;
                return 6; // Export
            }

            if (mouse_y >= 120 && mouse_y < 150)
            {
                activeMenu = 0;
                return 7; // Import
            }
        }
    }

    // Add dropdown
    if (activeMenu == 2)
    {
        if (mouse_x >= 183 && mouse_x <= 308)
        {
            if (mouse_y >= 30 && mouse_y < 60)
            {
                activeMenu = 0;
                return 1; // Cube
            }

            if (mouse_y >= 60 && mouse_y < 90)
            {
                activeMenu = 0;
                return 2; // Cylinder
            }

            if (mouse_y >= 90 && mouse_y < 120)
            {
                activeMenu = 0;
                return 3; // Sphere
            }
        }
    }

    // Click outside menus closes dropdown
    activeMenu = 0;
    return 0;
}

void UI_Shutdown()
{
    if (fontList != 0)
    {
        glDeleteLists(fontList, 256);
        fontList = 0;
    }

    activeMenu = 0;
}



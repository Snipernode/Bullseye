#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;

out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexColor = Color;

    //Isolating Scoreboard Display
    if (gl_Position.y > -0.5 && gl_Position.y < 0.4 && gl_Position.x > 0.0 && gl_Position.x <= 1.0 && Position.z > 5.0 && Position.z < 2750.0) {
        //SCOREBOARD vertexColor.a = 0.0;
    }

    // Uncomment this if you want to make LIST invisible
    if (gl_Position.y > 0.4 && gl_Position.y < 2.0 && gl_Position.x > -1.0 && gl_Position.x <= 1.0 && Position.z > 5.0 && Position.z < 2750.0) {
        //TABLIST vertexColor.a = 0.0;
    }
}
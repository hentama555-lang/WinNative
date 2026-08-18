package com.winlator.cmod.runtime.display.renderer;

public class ViewTransformation {
  /** Letterbox: preserve the game's aspect ratio, centering it with black bars (default). */
  public static final int FILL_MODE_FIT = 0;
  /** Stretch the game to fill the whole surface, ignoring aspect ratio. */
  public static final int FILL_MODE_STRETCH = 1;
  /** Zoom/crop: fill the surface preserving aspect, cropping the overflowing edges. */
  public static final int FILL_MODE_ZOOM = 2;

  public int mode = FILL_MODE_FIT;

  public int viewOffsetX;
  public int viewOffsetY;
  public int viewWidth;
  public int viewHeight;
  public float aspect;
  public float sceneScaleX;
  public float sceneScaleY;
  public float sceneOffsetX;
  public float sceneOffsetY;

  public void update(int outerWidth, int outerHeight, int innerWidth, int innerHeight) {
    if (outerWidth <= 0 || outerHeight <= 0 || innerWidth <= 0 || innerHeight <= 0) return;

    if (mode == FILL_MODE_STRETCH) {
      // Game scene fills the entire surface; no centering, no aspect preservation.
      aspect = (float) outerWidth / innerWidth;
      viewOffsetX = 0;
      viewOffsetY = 0;
      viewWidth = outerWidth;
      viewHeight = outerHeight;
      sceneScaleX = (float) innerWidth / outerWidth;
      sceneScaleY = (float) innerHeight / outerHeight;
      sceneOffsetX = 0f;
      sceneOffsetY = 0f;
      return;
    }

    // FIT uses the smaller scale (letterbox); ZOOM uses the larger scale (fill + crop).
    aspect = (mode == FILL_MODE_ZOOM)
        ? Math.max((float) outerWidth / innerWidth, (float) outerHeight / innerHeight)
        : Math.min((float) outerWidth / innerWidth, (float) outerHeight / innerHeight);
    viewWidth = (int) Math.ceil(innerWidth * aspect);
    viewHeight = (int) Math.ceil(innerHeight * aspect);
    viewOffsetX = (int) ((outerWidth - innerWidth * aspect) * 0.5f);
    viewOffsetY = (int) ((outerHeight - innerHeight * aspect) * 0.5f);

    sceneScaleX = (innerWidth * aspect) / outerWidth;
    sceneScaleY = (innerHeight * aspect) / outerHeight;
    sceneOffsetX = (innerWidth - innerWidth * sceneScaleX) * 0.5f;
    sceneOffsetY = (innerHeight - innerHeight * sceneScaleY) * 0.5f;
  }

  /** Convert a point in the physical output surface into guest/X-server coordinates.
   *  This is the single inverse transform used by touch/pointer input so input follows exactly
   *  the same FIT/STRETCH/ZOOM geometry as the Vulkan scene.
   */
  public float[] mapSurfaceToScene(float surfaceX, float surfaceY, float[] out) {
    if (out == null || out.length < 2) out = new float[2];
    if (aspect <= 0f) {
      out[0] = surfaceX;
      out[1] = surfaceY;
      return out;
    }

    if (mode == FILL_MODE_STRETCH) {
      out[0] = surfaceX * sceneScaleX;
      out[1] = surfaceY * sceneScaleY;
    } else {
      // FIT and ZOOM both use one uniform scale. Negative offsets are intentional in ZOOM
      // because the overflowing edges are cropped by the renderer's scissor.
      out[0] = (surfaceX - viewOffsetX) / aspect;
      out[1] = (surfaceY - viewOffsetY) / aspect;
    }
    return out;
  }

  public float mapSurfaceXToScene(float x) {
    if (aspect <= 0f) return x;
    return mode == FILL_MODE_STRETCH ? x * sceneScaleX : (x - viewOffsetX) / aspect;
  }

  public float mapSurfaceYToScene(float y) {
    if (aspect <= 0f) return y;
    return mode == FILL_MODE_STRETCH ? y * sceneScaleY : (y - viewOffsetY) / aspect;
  }

}

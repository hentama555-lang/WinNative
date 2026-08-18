package com.winlator.cmod.runtime.display.renderer;

public class ViewTransformation {
  /**
   * Resize/fit: preserve the game's aspect ratio and scale it uniformly as large as possible
   * inside the physical surface. The rendered frame is anchored at (0,0) on purpose:
   * there is no synthetic padding/letterbox offset to feed into input mapping.
   */
  public static final int FILL_MODE_FIT = 0;

  /** Stretch the game to fill the whole surface, ignoring aspect ratio. */
  public static final int FILL_MODE_STRETCH = 1;

  /** Zoom/crop: fill the surface preserving aspect, cropping the overflowing edges. */
  public static final int FILL_MODE_ZOOM = 2;

  public int mode = FILL_MODE_FIT;

  /** Pixel rectangle occupied by the rendered game inside the physical surface. */
  public int viewOffsetX;
  public int viewOffsetY;
  public int viewWidth;
  public int viewHeight;

  /** Uniform scale from guest pixels to physical pixels. */
  public float aspect;

  /** Inverse scales used by legacy callers for stretch mode. */
  public float sceneScaleX;
  public float sceneScaleY;

  /** Kept for API compatibility; resize/fit intentionally keeps these at zero. */
  public float sceneOffsetX;
  public float sceneOffsetY;

  public void update(int outerWidth, int outerHeight, int innerWidth, int innerHeight) {
    if (outerWidth <= 0 || outerHeight <= 0 || innerWidth <= 0 || innerHeight <= 0) return;

    if (mode == FILL_MODE_STRETCH) {
      // Full-surface stretch. This is the only mode that intentionally distorts aspect ratio.
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

    /*
     * FIT = uniform resize without padding.
     *
     * Use the smaller ratio so the complete game frame stays visible. Unlike the old
     * implementation, do NOT center the result by generating offsets.
     */
    aspect = (mode == FILL_MODE_ZOOM)
        ? Math.max((float) outerWidth / innerWidth, (float) outerHeight / innerHeight)
        : Math.min((float) outerWidth / innerWidth, (float) outerHeight / innerHeight);

    viewWidth = Math.max(1, Math.round(innerWidth * aspect));
    viewHeight = Math.max(1, Math.round(innerHeight * aspect));

    if (mode == FILL_MODE_ZOOM) {
      // Crop is still centered; this is not padding because the overflow lies outside the
      // physical surface. FIT, on the other hand, is always anchored at the origin.
      viewOffsetX = Math.round((outerWidth - innerWidth * aspect) * 0.5f);
      viewOffsetY = Math.round((outerHeight - innerHeight * aspect) * 0.5f);
    } else {
      // Resize/fit: no synthetic letterbox/padding region.
      viewOffsetX = 0;
      viewOffsetY = 0;
    }

    // Legacy inverse-scale values. Rendering/input use 'aspect' for the uniform modes.
    sceneScaleX = (float) innerWidth / viewWidth;
    sceneScaleY = (float) innerHeight / viewHeight;
    sceneOffsetX = 0f;
    sceneOffsetY = 0f;
  }

  /**
   * Returns true when the physical point lies on the actual rendered game rectangle.
   * For FIT this keeps blank phone-screen area out of the guest coordinate space.
   */
  public boolean isInsideRenderedFrame(float surfaceX, float surfaceY) {
    if (viewWidth <= 0 || viewHeight <= 0) return false;
    return surfaceX >= viewOffsetX
        && surfaceY >= viewOffsetY
        && surfaceX < viewOffsetX + viewWidth
        && surfaceY < viewOffsetY + viewHeight;
  }

  /**
   * Convert a physical output-surface point into guest/X-server coordinates.
   * FIT/ZOOM use exactly the same uniform scale as rendering. FIT has no padding offset.
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
      out[0] = (surfaceX - viewOffsetX) / aspect;
      out[1] = (surfaceY - viewOffsetY) / aspect;
    }

    // Clamp the inverse transform to the actual guest frame. This prevents taps outside
    // the resized frame from becoming out-of-range X-server coordinates.
    final float sceneWidth = viewWidth / aspect;
    final float sceneHeight = viewHeight / aspect;
    out[0] = Math.max(0f, Math.min(out[0], sceneWidth - 1f));
    out[1] = Math.max(0f, Math.min(out[1], sceneHeight - 1f));
    return out;
  }

  public float mapSurfaceXToScene(float x) {
    if (aspect <= 0f) return x;
    float result = mode == FILL_MODE_STRETCH ? x * sceneScaleX : (x - viewOffsetX) / aspect;
    return Math.max(0f, Math.min(result, viewWidth / aspect - 1f));
  }

  public float mapSurfaceYToScene(float y) {
    if (aspect <= 0f) return y;
    float result = mode == FILL_MODE_STRETCH ? y * sceneScaleY : (y - viewOffsetY) / aspect;
    return Math.max(0f, Math.min(result, viewHeight / aspect - 1f));
  }
}

package com.tom_e_white.set_game;

import boofcv.gui.ListDisplayPanel;
import boofcv.gui.image.ShowImages;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.UtilImageIO;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.Planar;
import com.tom_e_white.set_game.image.GeometryUtils;
import com.tom_e_white.set_game.image.ImageProcessingPipeline;
import georegression.struct.shapes.Quadrilateral_F64;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Extract a standardised card (or cards) from an image.
 */
public class CardDetector {

  public List<BufferedImage> scan(String filename) throws IOException {
    return scan(filename, false);
  }

  public List<BufferedImage> scan(String filename, boolean debug) throws IOException {
    // Based on code from http://boofcv.org/index.php?title=Example_Binary_Image

    BufferedImage image = UtilImageIO.loadImage(filename);
    if (image.getWidth() > image.getHeight()) {
      throw new IllegalArgumentException("Image height must be greater than width: " + filename);
    }

    ListDisplayPanel panel = debug ? new ListDisplayPanel() : null;

    List<Quadrilateral_F64> quads = ImageProcessingPipeline.fromBufferedImage(image, panel)
            .gray()
            .medianBlur(16)
            .binarize(0, 255)
            .erode()
            .dilate()
            .contours()
            .polygons(0.05, 0.1)
            .getExternalQuadrilaterals();

    // Only include shapes that are within given percentage of the mean area. This filters out image artifacts that
    // happen to be quadrilaterals that are not cards (since they are usually a different size).
    List<Quadrilateral_F64> cards = GeometryUtils.filterByArea(quads, 20);
    cards = GeometryUtils.sortRowWise(cards); // sort into a stable order
    List<BufferedImage> cardImages = cards.stream().map(q -> {
              // Remove perspective distortion
              Planar<GrayF32> output = GeometryUtils.removePerspectiveDistortion(image, q, 57 * 3, 89 * 3); // actual cards measure 57 mm x 89 mm
              return ConvertBufferedImage.convertTo_F32(output, null, true);
            }
    ).collect(Collectors.toList());

    if (debug) {
      int i = 1;
      for (BufferedImage card : cardImages) {
        panel.addImage(card, "Card " + i++);
      }
      ShowImages.showWindow(panel, getClass().getSimpleName(), true);
    }

    return cardImages;

  }

  public static void main(String[] args) throws IOException {
    new CardDetector().scan(args[0], true);
  }
}

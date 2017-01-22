package com.tom_e_white.set_game;

import boofcv.gui.ListDisplayPanel;
import boofcv.gui.image.ShowImages;
import boofcv.io.image.UtilImageIO;
import com.tom_e_white.set_game.image.ImageProcessingPipeline;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Optional;

/**
 * Find features for shapes on a card.
 */
public class FindCardShapeFeatures implements FeatureFinder<FindCardShapeFeatures.CardShapeFeatures> {

  public static class CardShapeFeatures implements Features {
    private final int shapeNumberLabel;
    private final int numSides;
    private final boolean isConvex;

    public CardShapeFeatures(int shapeNumberLabel, int numSides, boolean isConvex) {
      this.shapeNumberLabel = shapeNumberLabel;
      this.numSides = numSides;
      this.isConvex = isConvex;
    }

    public int getNumSides() {
      return numSides;
    }

    public boolean isConvex() {
      return isConvex;
    }

    @Override
    public String getSummaryLine() {
      return shapeNumberLabel + "," +
              numSides + "," +
              (isConvex ? "1" : "0");
    }

    @Override
    public String toString() {
      return "CardShapeFeatures{" +
              "shapeNumberLabel=" + shapeNumberLabel +
              ", numSides=" + numSides +
              ", isConvex=" + isConvex +
              '}';
    }
  }

  @Override
  public CardShapeFeatures find(String filename, boolean debug) throws IOException {
    // Based on code from http://boofcv.org/index.php?title=Example_Binary_Image

    BufferedImage image = UtilImageIO.loadImage(filename);

    ListDisplayPanel panel = debug ? new ListDisplayPanel() : null;

    // TODO: Use shapes found by CardFeatureCounter, since these are "good" contours. Maybe join?
    Optional<CardShapeFeatures> cardShapeFeatures = ImageProcessingPipeline.fromBufferedImage(image, panel)
            .gray()
            .medianBlur(3) // this is fairly critical
            .edges()
            .dilate()
            .contours()
            .polygons(0.05, 0.05)
            .getExternalPolygons()
            .stream()
            .map(p -> new CardShapeFeatures(CardLabel.getShapeNumber(new File(filename)), p.size(), p.isConvex()))
            .findFirst();

    if (debug) {
      ShowImages.showWindow(panel, getClass().getSimpleName(), true);
    }

    return cardShapeFeatures.orElse(null); // improve
  }

  public static void main(String[] args) throws IOException {
    CardShapeFeatures cardShapeFeatures = new FindCardShapeFeatures().find(args[0], true);
    System.out.println(cardShapeFeatures);
  }
}

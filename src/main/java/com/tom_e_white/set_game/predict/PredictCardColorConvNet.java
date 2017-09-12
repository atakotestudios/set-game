package com.tom_e_white.set_game.predict;

import com.tom_e_white.set_game.preprocess.CardDetector;
import com.tom_e_white.set_game.preprocess.CardImage;
import com.tom_e_white.set_game.train.FindCardColourFeatures;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.tensorflow.DataType;
import org.tensorflow.Graph;
import org.tensorflow.Output;
import org.tensorflow.Session;
import org.tensorflow.Tensor;

public class PredictCardColorConvNet {
  public static void main(String[] args) throws IOException {
    File testFile = new File(args[0]);
    CardDetector cardDetector = new CardDetector();
    List<CardImage> images = cardDetector.detect(testFile.getAbsolutePath(), false);
    List<String> testDescriptions = Files.lines(Paths.get(testFile.getAbsolutePath().replace(".jpg", ".txt"))).collect(Collectors.toList());
    FindCardColourFeatures colourFinder = new FindCardColourFeatures(2); // to parse description
    int correct = 0;
    int total = 0;
    for (int i = 0; i < testDescriptions.size(); i++) {
      float[] labelProbabilities = predict(images.get(i).getImage(), "tf/set_color.pb");
      //System.out.println(Arrays.toString(labelProbabilities));
      int predictedLabel = maxIndex(labelProbabilities);
      int actualLabel = colourFinder.getLabelFromDescription(testDescriptions.get(i));
      if (predictedLabel == actualLabel) {
        correct++;
      } else {
        System.out.println("Incorrect, predicted " + predictedLabel + " but was " + actualLabel + " for card " + (i + 1));
      }
      total++;
    }
    System.out.println("Correct: " + correct);
    System.out.println("Total: " + total);
    double accuracy = ((double) correct)/total * 100;
    System.out.println("Accuracy: " + ((int) accuracy) + " percent");
    System.out.println("------------------------------------------");
  }

  static float[] predict(BufferedImage image, String file) throws IOException {
    byte[] graphDef = Files.readAllBytes(Paths.get(file));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "jpg", out);
    byte[] imageBytes = out.toByteArray();

    try (Tensor imageTensor = constructAndExecuteGraphToNormalizeImage(imageBytes)) {
      return executeCnnGraph(graphDef, imageTensor);
    }
  }

  static List<String> readLabels(String file) throws IOException {
    return Files.readAllLines(Paths.get(file), Charset.forName("UTF-8"));
  }

  static Tensor constructAndExecuteGraphToNormalizeImage(byte[] imageBytes) {
    try (Graph g = new Graph()) {
      GraphBuilder b = new GraphBuilder(g);
      // Some constants specific to the pre-trained model at:
      // https://storage.googleapis.com/download.tensorflow.org/models/inception5h.zip
      //
      // - The model was trained with images scaled to 224x224 pixels.
      // - The colors, represented as R, G, B in 1-byte each were converted to
      //   float using (value - Mean)/Scale.
      final int H = 150;
      final int W = 150;
      final float mean = 0f;
      final float scale = 255f;

      // Since the graph is being constructed once per execution here, we can use a constant for the
      // input image. If the graph were to be re-used for multiple input images, a placeholder would
      // have been more appropriate.
      final Output input = b.constant("input", imageBytes);
      final Output output =
          b.div(
              b.sub(
                  b.resizeBilinear(
                      b.expandDims(
                          b.cast(b.decodeJpeg(input, 3), DataType.FLOAT),
                          b.constant("make_batch", 0)),
                      b.constant("size", new int[] {H, W})),
                  b.constant("mean", mean)),
              b.constant("scale", scale));
      try (Session s = new Session(g)) {
        return s.runner().fetch(output.op().name()).run().get(0);
      }
    }
  }

  static float[] executeCnnGraph(byte[] graphDef, Tensor image) {
    try (Graph g = new Graph()) {
      g.importGraphDef(graphDef);
      try (Session s = new Session(g);
           Tensor result = s.runner()
               .feed("conv2d_1_input", image)
               .feed("keras_learning_phase", Tensor.create(false))
               .fetch("dense_2/Softmax").run().get(0)) {
        final long[] rshape = result.shape();
        if (result.numDimensions() != 2 || rshape[0] != 1) {
          throw new RuntimeException(
              String.format(
                  "Expected model to produce a [1 N] shaped tensor where N is the number of labels, instead it produced one with shape %s",
                  Arrays.toString(rshape)));
        }
        int nlabels = (int) rshape[1];
        return result.copyTo(new float[1][nlabels])[0];
      }
    }
  }

  static int maxIndex(float[] probabilities) {
    int best = 0;
    for (int i = 1; i < probabilities.length; ++i) {
      if (probabilities[i] > probabilities[best]) {
        best = i;
      }
    }
    return best;
  }

  static class GraphBuilder {
    GraphBuilder(Graph g) {
      this.g = g;
    }

    Output div(Output x, Output y) {
      return binaryOp("Div", x, y);
    }

    Output sub(Output x, Output y) {
      return binaryOp("Sub", x, y);
    }

    Output resizeBilinear(Output images, Output size) {
      return binaryOp("ResizeBilinear", images, size);
    }

    Output expandDims(Output input, Output dim) {
      return binaryOp("ExpandDims", input, dim);
    }

    Output cast(Output value, DataType dtype) {
      return g.opBuilder("Cast", "Cast").addInput(value).setAttr("DstT", dtype).build().output(0);
    }

    Output decodeJpeg(Output contents, long channels) {
      return g.opBuilder("DecodeJpeg", "DecodeJpeg")
          .addInput(contents)
          .setAttr("channels", channels)
          .build()
          .output(0);
    }

    Output constant(String name, Object value) {
      try (Tensor t = Tensor.create(value)) {
        return g.opBuilder("Const", name)
            .setAttr("dtype", t.dataType())
            .setAttr("value", t)
            .build()
            .output(0);
      }
    }

    private Output binaryOp(String type, Output in1, Output in2) {
      return g.opBuilder(type, type).addInput(in1).addInput(in2).build().output(0);
    }

    private Graph g;
  }
}
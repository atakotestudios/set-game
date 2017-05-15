# Set

Play Set using image recognition.

## Idea

Use image recognition algorithms that

1. Pick out individual cards from an image.
2. For each card, count the number of shapes, and detect the colour, shape and shading.

Finally finding Sets within the image is the easy bit.

## Technique

Deep Learning is an obvious candidate, but I started by using more basic image processing
and machine learning techniques. These serve as a baseline, and will help quantify
how much better Deep Learning can do.

## Data

I took photos of lots of Set cards under various lighting conditions. The cards were
on black backgrounds to make things easier. An obvious extension would be to try
different backgrounds at some point.

There are two training datasets.

### Dataset 1

The first dataset is found in _data/train_, and consists of 36 images of 9 cards.
Each image has cards of the same colour, and laid out in the same way.

The program `CreateTrainingSetV1` processes the raw training images and creates
a training set of images with one card in each image. See _data/train_out_.

The small size of the first dataset meant that colour detection in particular wa
lacking, so I created a new, larger second dataset with more varied lighting conditions.

### Dataset 2

The _train-v2_ dataset is a collection of Set card images.

The _raw-archive_ directory contains original camera images. Each image is a 
photo of a board of 27 images, arranged in a 3 by 9 grid. There are three
boards, one contains all the Set cards with one shape, one with two shapes,
and one with three shapes.

The _raw-archive_ images are photos of the same boards taken at different times under
varying lighting conditions, from different angles, and with different cameras.

#### Process

1. Copy photos from the camera to a new _raw-archive/batch-nnnnn_ directory.
2. Visually inspect the images in Preview and make sure they all oriented correctly.
(Open the Inspector, and check the Orientation - it should be 1.) Rotate any that
are not the right way up.
3. Run `mkdir -p data/train-v2/raw-new; cp data/train-v2/raw-archive/batch-nnnnn/* data/train-v2/raw-new`
4. Run the following if files have an uppercase `.JPG` extension:
```
for file in data/train-v2/raw-new/*.JPG; do mv $file data/train-v2/raw-new/$(basename $file .JPG).jpg; done
```
5. Run `CheckRawTrainingImagesV2`. This will check that the images all have the correct orientation and the
individual cards can be detected.
6. If there are problematic images, then copy them to _raw-problem_. These will not be used, but
keep them as future versions of the code may be able to handle them.
7. Run `SortRawTrainingImagesV2`. This will programmatically detect the number of features on
each card so that it can sort the training boards with 1, 2, or 3 number cards. (Note 3 is called 0.)
8. Open each directory in _raw-sorted_ and visually check that each board is in the correct
directory. Move any that are not.
9. Run `CreateTrainingSetV2`. This will take each board in _raw-sorted_ and extract labelled
individual cards and store them in _raw-labelled_, then open a window showing each set of
cards. Visually inspect these to check they are correct. Move any images that are not.
10. Run 
```
mkdir -p data/train-v2/labelled/
rsync -a data/train-v2/raw-labelled/ data/train-v2/labelled/
rm -rf data/train-v2/raw-{new,sorted,labelled}
```
11. You can view all of the labelled images by running `ViewLabelledImagesV2`.

### Test Data

The test data is in _data/20170106_205743.jpg_, as well as _data/ad-hoc_.

## Processing

The raw data is _preprocessed_ to get it into shape for training. The preprocessing
was discussed above, and the output is one card per image in labelled directories.

_Training_ is comprised of two parts: feature extraction from the images, and creating a
model from the features. Both steps are carried out by the `FeatureFinder` classes, which
use hand-crafted feature extractors, followed by k-nearest neighbors to do prediction.
(Note that model creation is not needed for kNN, since all the training data is used as the
model.) Furthermore, `FindCardNumberFeatures` does not even need a model since the
image processing can accurately count the number of shapes on a card.

Training is carried out by running `CreateTrainingDataV1`.

_Prediction_ (or inference) is the last step of the process, and uses the `FeatureFinder`
classes to recognize the cards in new or test images.

Prediction is carried out by the classes in `com.tom_e_white.set_game.predict`, including
`PlaySet` which takes an image and highlights the Sets in it.

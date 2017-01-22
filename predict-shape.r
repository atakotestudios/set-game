require(caret)
a <- read.csv("/Users/tom/projects-workspace/set-game/data/train-out-shape.csv")
a$X1 <- as.factor(a$X1)
set.seed(1)
aFit <- train(X1 ~ ., data = a, method = "svmRadial", preProc = c("center", "scale"), tuneLength = 10)
aFit

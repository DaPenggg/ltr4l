/*
 * Copyright 2018 org.LTR4L
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ltr4l.trainers;

import org.ltr4l.nn.Ranker;
import org.ltr4l.query.Document;
import org.ltr4l.query.Query;
import org.ltr4l.query.QuerySet;
import org.ltr4l.tools.Error;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.IntStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.ltr4l.tools.Config;

/**
 * The implementation of LTRTrainer which uses the
 * PRank(Perceptron Ranking) algorithm.
 *
 */
public class PRankTrainer extends LTRTrainer {
  final private PRank pRanker;
  private final  List<Document> trainingDocList;

  PRankTrainer(QuerySet training, QuerySet validation, Config config) {
    super(training, validation, config);
    maxScore = 0.0;
    pRanker = new PRank(training.getFeatureLength(), QuerySet.findMaxLabel(trainingSet));
    super.ranker = pRanker;
    trainingDocList = new ArrayList<>();
    for (Query query : trainingSet)
      trainingDocList.addAll(query.getDocList());
  }

  @Override
  public void train() {
    Collections.shuffle(trainingDocList);
    for (Document doc : trainingDocList)
      pRanker.updateWeights(doc);
  }

  protected double calculateLoss(List<Query> queries) {
    double loss = 0d;
    for (Query query : queries) {
      List<Document> docList = query.getDocList();
      loss += docList.stream().mapToDouble(doc -> new Error.Square().error(pRanker.predict(doc.getFeatures()), doc.getLabel())).sum() / docList.size();
    }
    return loss / queries.size();
  }

  @Override
  Ranker getRanker() {
    return pRanker;
  }
}

class PRank extends Ranker{
  protected double[] weights;
  protected double[] thresholds;

  PRank(int featureLength, int maxLabel) {
    if (featureLength > 0 && maxLabel > 0) {
      weights = new double[featureLength];
      thresholds = new double[maxLabel];
    } else {
      weights = null;
      thresholds = null;
    }

  }

  public double[] getWeights() {
    return weights;
  }

  public double[] getThresholds() {
    return thresholds;
  }

  public void writeModel(Properties props, String file) {
    try (PrintWriter pw = new PrintWriter(new FileOutputStream(file))) {
      props.store(pw, "Saved model");
      pw.println("model=" + Arrays.toString(weights)); //To ensure model gets written at the end.
      pw.println("thresholds=" + Arrays.toString(thresholds));
      //props.setProperty("model", obtainWeights().toString());
      //props.store(pw, "Saved model");

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void updateWeights(Document doc) {
    double wx = predictRelScore(doc.getFeatures());
    int output = (int) predict(doc.getFeatures());
    int label = doc.getLabel();
    if (output == label)//if output == label, do not update weights.
      return;
    int[] tau = new int[thresholds.length];
    for (int r = 0; r <= thresholds.length - 1; r++) { /////thresholds.length ??
      int ytr;
      if (label <= r)
        ytr = -1;
      else
        ytr = 1;
      if ((wx - thresholds[r]) * ytr <= 0)
        tau[r] = ytr;
      else
        tau[r] = 0;
    }
    int T = IntStream.of(tau).sum();
    for (int i = 0; i <= weights.length - 1; i++) {
      weights[i] += T * doc.getFeatures().get(i);
    }
    for (int r = 0; r <= thresholds.length - 1; r++) {
      thresholds[r] -= tau[r];
    }
  }


  @Override
  public double predict(List<Double> features) {
    double wx = predictRelScore(features);
    for (int i = 0; i < thresholds.length; i++) {
      double b = thresholds[i];
      if (wx < b)
        return i;
    }
    return thresholds.length;
  }

  private double predictRelScore(List<Double> features){
    double wx = 0;
    for (int i = 0; i < features.size(); i++){
      wx += features.get(i) * weights[i];
    }
    return wx;
  }

}
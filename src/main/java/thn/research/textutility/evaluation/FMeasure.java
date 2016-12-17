/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package thn.research.textutility.evaluation;

import java.util.List;

/**
 * Ref: https://en.wikipedia.org/wiki/F1_score
 * 
 * @author THNghiep
 */
public class FMeasure {

    // Prevent instantiation.
    private FMeasure() {
    }

    /**
     * This method computes MeanFMeasure with beta parameter with shared ground truth. When beta == 1, we have F1 score.
     * 
     * @param input
     * @param groundTruth
     * @param beta
     * @param k
     * @return
     * @throws Exception 
     */
    public static double computeMeanFMeasureAtKSharedGroundTruth(List<List> input, List groundTruth, double beta, int k) throws Exception {
        if ((input == null) || (groundTruth == null) || (input.isEmpty()) || (groundTruth.isEmpty())) {
            return 0.0;
        }

        double sumF = 0;
        int numInput = 0;
        for (int i = 0; i < input.size(); i++) {
            if ((input.get(i) != null) && (!input.get(i).isEmpty())) {
                numInput++;
                sumF += computeFMeasureAtK(input.get(i), groundTruth, beta, k);
            }
        }
        if (numInput == 0) {
            return 0;
        }
        return sumF / numInput;
    }

    /**
     * This method computes MeanFMeasure with beta parameter. When beta == 1, we have F1 score.
     * 
     * @param input
     * @param groundTruth
     * @param beta
     * @param k
     * @return
     * @throws Exception 
     */
    public static double computeMeanFMeasureAtK(List<List> input, List<List> groundTruth, double beta, int k) throws Exception {
        if ((input == null) || (groundTruth == null) || (input.isEmpty()) || (groundTruth.isEmpty())) {
            return 0.0;
        }

        double sumF = 0;
        int numInput = 0;
        for (int i = 0; i < input.size(); i++) {
            if ((input.get(i) != null) && (!input.get(i).isEmpty())) {
                numInput++;
                sumF += computeFMeasureAtK(input.get(i), groundTruth.get(i), beta, k);
            }
        }
        if (numInput == 0) {
            return 0;
        }
        return sumF / numInput;
    }

    /**
     * This method computes F1 score.
     *
     * @param input
     * @param groundTruth
     * @return F1
     */
    public static double computeF1Score(List input, List groundTruth) throws Exception {
        return computeFMeasure(input, groundTruth, 1);
    }

    /**
     * This method computes F-measure.
     *
     * @param input
     * @param groundTruth
     * @param beta
     * @return F measure
     */
    public static double computeFMeasure(List input, List groundTruth, double beta) throws Exception {
        if ((input == null) || (groundTruth == null) || (input.isEmpty()) || (groundTruth.isEmpty()) || (beta < 0)) {
            return 0.0;
        }
        
        double precision = Precision.computePrecision(input, groundTruth);
        double recall = Recall.computeRecall(input, groundTruth);
        double f = ((1 + beta) * precision * recall) / ((beta * beta * precision) + recall);
        if (Double.isNaN(f) || (Double.isInfinite(f))) {
            f = 0.0;
        }

        return f;
    }
    
    /**
     * This method computes F1 score at K.
     *
     * @param input
     * @param groundTruth
     * @param k
     * @return F1
     */
    public static double computeF1ScoreAtK(List input, List groundTruth, int k) throws Exception {
        return computeFMeasureAtK(input, groundTruth, 1, k);
    }

    /**
     * This method computes F-measure at K.
     *
     * @param input
     * @param groundTruth
     * @param beta
     * @param k
     * @return F measure
     */
    public static double computeFMeasureAtK(List input, List groundTruth, double beta, int k) throws Exception {
        if ((input == null) || (groundTruth == null) || (input.isEmpty()) || (groundTruth.isEmpty()) || (beta < 0)) {
            return 0.0;
        }
        
        double precision = Precision.computePrecisionAtK(input, groundTruth, k);
        double recall = Recall.computeRecallAtK(input, groundTruth, k);
        double f = ((1 + beta) * precision * recall) / ((beta * beta * precision) + recall);
        if (Double.isNaN(f) || (Double.isInfinite(f))) {
            f = 0.0;
        }

        return f;
    }
}

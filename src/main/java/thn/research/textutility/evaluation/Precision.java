/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package thn.research.textutility.evaluation;

import java.util.List;

/**
 * This class content methods for computing metric related to Precision. 
 * Ref: 
 * 1. http://en.wikipedia.org/wiki/Precision_and_recall 
 * 2. http://www.stanford.edu/class/cs276/handouts/EvaluationNew-handout-6-per.pdf
 *
 * @author THNghiep
 */
public class Precision {

    // Prevent instantiation.
    private Precision() {
    }

    /**
     * Compute mean precision at k with shared ground truth.
     * 
     * @param input
     * @param groundTruth
     * @param k
     * @return 
     */
    public static double computeMeanPrecisionAtKSharedGroundTruth(List<List> input, List groundTruth, int k) throws Exception {
        if ((input == null) || (groundTruth == null) || (input.isEmpty()) || (groundTruth.isEmpty())) {
            return 0.0;
        }

        double sumPrecision = 0;
        int numInput = 0;
        for (int i = 0; i < input.size(); i++) {
            if ((input.get(i) != null) && (!input.get(i).isEmpty())) {
                numInput++;
                sumPrecision += computePrecisionAtK(input.get(i), groundTruth, k);
            }
        }
        if (numInput == 0) {
            return 0;
        }
        return sumPrecision / numInput;
    }

    /**
     * Compute mean precision at k.
     * 
     * @param input
     * @param groundTruth
     * @param k
     * @return 
     */
    public static double computeMeanPrecisionAtK(List<List> input, List<List> groundTruth, int k) throws Exception {
        if ((input == null) || (groundTruth == null) || (input.isEmpty()) || (groundTruth.isEmpty())) {
            return 0.0;
        }

        double sumPrecision = 0;
        int numInput = 0;
        for (int i = 0; i < input.size(); i++) {
            if ((input.get(i) != null) && (!input.get(i).isEmpty())) {
                numInput++;
                sumPrecision += computePrecisionAtK(input.get(i), groundTruth.get(i), k);
            }
        }
        if (numInput == 0) {
            return 0;
        }
        return sumPrecision / numInput;
    }

    /**
     * This method computes precision based on relevant documents retrieved and
     * total retrieved documents
     *
     * @param input
     * @param groundTruth
     * @return
     */
    public static double computePrecision(List input, List groundTruth) throws Exception {
        if ((input == null) || (groundTruth == null) || (input.isEmpty()) || (groundTruth.isEmpty())) {
            return 0.0;
        }
        
        // true positive
        double tp = 0.0;

        for (int i = 0; i < input.size(); i++) {
            if (groundTruth.contains(input.get(i))) {
                tp++;
            }
        }

        // ranklist size = true positive + false positive.
        return (double) tp / input.size();
    }

    /**
     * This method computes precision with threshold k based on relevant
     * documents retrieved and k retrieved documents
     *
     * @param input
     * @param groundTruth
     * @param k
     * @return
     */
    public static double computePrecisionAtK(List input, List groundTruth, int k) throws Exception {
        if ((input == null) || (groundTruth == null) || (input.isEmpty()) || (groundTruth.isEmpty()) || (k <= 0)) {
            return 0.0;
        }
        
        // true positive
        double tp = 0.0;

        // count to rank list size but divide by original top k.
        int nN = k;
        if (nN > input.size()) {
            nN = input.size();
        }

        for (int i = 0; i < nN; i++) {
            if (groundTruth.contains(input.get(i))) {
                tp++;
            }
        }

        // k = true positive + false positive.
        return (double) tp / k;
    }
}

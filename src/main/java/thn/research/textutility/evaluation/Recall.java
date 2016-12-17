/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package thn.research.textutility.evaluation;

import java.util.List;

/**
 * This class content methods for computing metric related to Recall. 
 * Ref:
 * 1. http://en.wikipedia.org/wiki/Precision_and_recall
 * 2. http://www.stanford.edu/class/cs276/handouts/EvaluationNew-handout-6-per.pdf
 * 
 * @author THNghiep
 */
public class Recall {

    // Prevent instantiation.
    private Recall() {
    }

    /**
     * Compute mean recall at k with shared ground truth.
     * 
     * @param input
     * @param groundTruth
     * @param k
     * @return 
     */
    public static double computeMeanRecallAtKSharedGroundTruth(List<List> input, List groundTruth, int k) throws Exception {
        if ((input == null) || (groundTruth == null) || (input.isEmpty()) || (groundTruth.isEmpty())) {
            return 0.0;
        }

        double sumRecall = 0;
        int numInput = 0;
        for (int i = 0; i < input.size(); i++) {
            if ((input.get(i) != null) && (!input.get(i).isEmpty())) {
                numInput++;
                sumRecall += computeRecallAtK(input.get(i), groundTruth, k);
            }
        }
        if (numInput == 0) {
            return 0;
        }
        return sumRecall / numInput;
    }
    
    /**
     * Compute mean recall at k.
     * 
     * @param input
     * @param groundTruth
     * @param k
     * @return 
     */
    public static double computeMeanRecallAtK(List<List> input, List<List> groundTruth, int k) throws Exception {
        if ((input == null) || (groundTruth == null) || (input.isEmpty()) || (groundTruth.isEmpty())) {
            return 0.0;
        }

        double sumRecall = 0;
        int numInput = 0;
        for (int i = 0; i < input.size(); i++) {
            if ((input.get(i) != null) && (!input.get(i).isEmpty())) {
                numInput++;
                sumRecall += computeRecallAtK(input.get(i), groundTruth.get(i), k);
            }
        }
        if (numInput == 0) {
            return 0;
        }
        return sumRecall / numInput;
    }

    /**
     * This method computes recall based on relevant documents retrieved and
     * total relevant documents
     *
     * @param input
     * @param groundTruth
     * @return rec
     */
    public static double computeRecall(List input, List groundTruth) throws Exception {
        if ((input == null) || (groundTruth == null) || (input.isEmpty()) || (groundTruth.isEmpty())) {
            return 0.0;
        }
        
        // true positive
        double tp = 0.0;

        for (int i = 0; i < groundTruth.size(); i++) {
            if (input.contains(groundTruth.get(i))) {
                tp++;
            }
        }
        
        // ground truth size = true positive + false negative.
        return (double) tp / groundTruth.size();
    }

    /**
     * 
     * @param input
     * @param groundTruth
     * @param k
     * @return 
     */
    public static double computeRecallAtK(List input, List groundTruth, int k) throws Exception {
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
        
        List topNRankList = input.subList(0, nN);
        for (int i = 0; i < groundTruth.size(); i++) {
            if (topNRankList.contains(groundTruth.get(i))) {
                tp++;
            }
        }
        
        // ground truth size = true positive + false negative.
        return (double) tp / groundTruth.size();
    }
}

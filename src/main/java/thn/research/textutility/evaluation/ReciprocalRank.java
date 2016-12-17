/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package thn.research.textutility.evaluation;

import java.util.List;

/**
 * This class content methods for computing metric related to ReciprocalRank.
 * Caller needs to get mean of reciprocal rank later.
 * Ref:
 * 1. https://en.wikipedia.org/wiki/Mean_reciprocal_rank
 * 2. http://www.stanford.edu/class/cs276/handouts/EvaluationNew-handout-6-per.pdf
 * 
 * @author THNghiep
 */
public class ReciprocalRank {

    // Prevent instantiation. Use static method instead.
    private ReciprocalRank() {
    }

    /**
     * Compute Mean Reciprocal Rank with shared ground truth.
     * 
     * @param input
     * @param sharedGroundTruth
     * @return
     * @throws Exception 
     */
    public static double computeMRRSharedGroundTruth(List<List> input, List sharedGroundTruth) throws Exception {
        if ((input == null) || (sharedGroundTruth == null) || (input.isEmpty()) || (sharedGroundTruth.isEmpty())) {
            return 0.0;
        }

        double srr = 0;
        int numInput = 0;
        for (int i = 0; i < input.size(); i++) {
            if ((input.get(i) != null) && (!input.get(i).isEmpty())) {
                numInput++;
                srr += computeRR(input.get(i), sharedGroundTruth);
            }
        }
        if (numInput == 0) {
            return 0;
        }
        return srr / numInput;
    }
    
    /**
     * Compute Mean Reciprocal Rank.
     * 
     * @param input
     * @param groundTruth
     * @return
     * @throws Exception 
     */
    public static double computeMRR(List<List> input, List<List> groundTruth) throws Exception {
        if ((input == null) || (groundTruth == null) || (input.isEmpty()) || (groundTruth.isEmpty())) {
            return 0.0;
        }

        double srr = 0;
        int numInput = 0;
        for (int i = 0; i < input.size(); i++) {
            if ((input.get(i) != null) && (!input.get(i).isEmpty())) {
                numInput++;
                srr += computeRR(input.get(i), groundTruth.get(i));
            }
        }
        if (numInput == 0) {
            return 0;
        }
        return srr / numInput;
    }

    /**
     * This method computes the reciprocal rank of 1 list.
     * @param input
     * @param groundTruth
     * @return reciprocal rank.
     */
    public static double computeRR(List input, List groundTruth) throws Exception {
        if ((input == null) || (groundTruth == null) || (input.isEmpty()) || (groundTruth.isEmpty())) {
            return 0.0;
        }
        
        for (int i = 0; i < input.size(); i++) {
            if (groundTruth.contains(input.get(i))) {
                // Reciprocal of first relevant item position.
                return (double) 1 / (i + 1);
            }
        }

        return 0.0;
    }
}

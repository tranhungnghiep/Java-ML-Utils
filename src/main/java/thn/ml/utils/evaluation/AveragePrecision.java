/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package thn.research.textutility.evaluation;

import java.util.List;

/**
 * This class content methods for computing metric related to AveragePrecision.
 * Ref:
 * 1. http://en.wikipedia.org/wiki/Information_retrieval#Average_precision
 * 2. http://fastml.com/what-you-wanted-to-know-about-mean-average-precision/
 *
 * @author THNghiep
 */
public class AveragePrecision {

    // Prevent instantiation.
    private AveragePrecision() {
    }

    /**
     * Compute MAP at K with shared ground truth.
     * 
     * @param input
     * @param groundTruth
     * @param k
     * @return
     * @throws Exception 
     */
    public static double computeMAPAtKSharedGroundTruth(List<List> input, List groundTruth, int k) throws Exception {
        if ((input == null) || (groundTruth == null) || (input.isEmpty()) || (groundTruth.isEmpty())) {
            return 0.0;
        }

        double sumMAP = 0;
        int numInput = 0;
        for (int i = 0; i < input.size(); i++) {
            if ((input.get(i) != null) && (!input.get(i).isEmpty())) {
                numInput++;
                sumMAP += computeAPAtK(input.get(i), groundTruth, k);
            }
        }
        if (numInput == 0) {
            return 0;
        }
        return sumMAP / numInput;
    }

    /**
     * Compute MAP at K.
     * 
     * @param input
     * @param groundTruth
     * @param k
     * @return
     * @throws Exception 
     */
    public static double computeMAPAtK(List<List> input, List<List> groundTruth, int k) throws Exception {
        if ((input == null) || (groundTruth == null) || (input.isEmpty()) || (groundTruth.isEmpty())) {
            return 0.0;
        }

        double sumMAP = 0;
        int numInput = 0;
        for (int i = 0; i < input.size(); i++) {
            if ((input.get(i) != null) && (!input.get(i).isEmpty())) {
                numInput++;
                sumMAP += computeAPAtK(input.get(i), groundTruth.get(i), k);
            }
        }
        if (numInput == 0) {
            return 0;
        }
        return sumMAP / numInput;
    }

    /**
     * This method computes average precision with threshold k.
     *
     * @param input
     * @param groundTruth
     * @param k
     * @return
     */
    public static double computeAPAtK(List input, List groundTruth, int k) {
        if ((input == null) || (groundTruth == null) || (input.isEmpty()) || (groundTruth.isEmpty()) || (k <= 0)) {
            return 0.0;
        }
        
        // return value.
        double apk = 0.0;

        // only consider the real ranklist size.
        int nK = k;
        if (nK > input.size()) {
            nK = input.size();
        }
        
        // sum of precision at k.
        int num_hits = 0;
        for (int i = 0; i < nK; i++) {
            // Only interate to nK = real ranklist size
            // => if list is too small, the result is not changed when k changes.
            // E.g., if nK == 3 and all 3 item is in ground truth, MAP@5, 10, 15... = 1.
            // This is correct, according to the definition of MAP (https://www.kaggle.com/wiki/MeanAveragePrecision)
            if (groundTruth.contains(input.get(i))) {
                num_hits += 1;
                apk += (double) num_hits / (i + 1);
            }
        }
        
        // multiply by change in recall at each step, or average by number of relevant document.
        int numRelevantDocument = 0;
        if (nK < groundTruth.size()) {
            numRelevantDocument = nK;
        } else {
            numRelevantDocument = groundTruth.size();
        }
        apk = (double) apk / numRelevantDocument;
        
        return apk;
    }
}

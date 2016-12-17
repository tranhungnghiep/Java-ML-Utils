/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package thn.research.textutility.evaluation;

import java.util.List;

/**
 * This class content methods for computing metric related to NDCG.
 * Ref:
 * 1. https://en.wikipedia.org/wiki/Discounted_cumulative_gain
 * 2. http://www.stanford.edu/class/cs276/handouts/EvaluationNew-handout-6-per.pdf
 * 3. https://www.kaggle.com/wiki/NormalizedDiscountedCumulativeGain
 * 
 * @author THNghiep
 */
public class NDCG {
    
    // Prevent instantiation.
    private NDCG() {
    }
    
    /**
     * Compute mean NDCG at K with shared ground truth.
     * 
     * @param input
     * @param groundTruth
     * @param k
     * @return
     * @throws Exception 
     */
    public static double computeMeanNDCGAtKSharedGroundTruth(List<List> input, List groundTruth, int k) throws Exception {
        if ((input == null) || (groundTruth == null) || (input.isEmpty()) || (groundTruth.isEmpty())) {
            return 0.0;
        }

        double sumNDCG = 0;
        int numInput = 0;
        for (int i = 0; i < input.size(); i++) {
            if ((input.get(i) != null) && (!input.get(i).isEmpty())) {
                numInput++;
                sumNDCG += computeNDCG(input.get(i), groundTruth, k);
            }
        }
        if (numInput == 0) {
            return 0;
        }
        return sumNDCG / numInput;
    }

    /**
     * Compute mean NDCG at K.
     * 
     * @param input
     * @param groundTruth
     * @param k
     * @return
     * @throws Exception 
     */
    public static double computeMeanNDCGAtK(List<List> input, List<List> groundTruth, int k) throws Exception {
        if ((input == null) || (groundTruth == null) || (input.isEmpty()) || (groundTruth.isEmpty())) {
            return 0.0;
        }

        double sumNDCG = 0;
        int numInput = 0;
        for (int i = 0; i < input.size(); i++) {
            if ((input.get(i) != null) && (!input.get(i).isEmpty())) {
                numInput++;
                sumNDCG += computeNDCG(input.get(i), groundTruth.get(i), k);
            }
        }
        if (numInput == 0) {
            return 0;
        }
        return sumNDCG / numInput;
    }

    /**
     * This method compute the basic NDCG metric.
     * Relevance score: binary, means relevant item: 1, irrelevant item: 0.
     * 
     * Note: 
     * This implementation uses the second formula on wiki page. 
     * This formula is more popular and is consistent regard of log base.
     * But this formula produce lower score than the first formula.
     * 
     * @param input
     * @param groundTruth
     * @param k
     * @return ndcg
     */
    public static double computeNDCG(List input, List groundTruth, int k) throws Exception {
        if ((input == null) || (groundTruth == null) || (input.isEmpty()) || (groundTruth.isEmpty()) || (k <= 0)) {
            return 0.0;
        }
        
        double ndcg = 0.0;
        
        // Note: for IDCG, we need to keep the value of k unchange, not reduce to ranklist's length.
        ndcg = computeDCG(input, groundTruth, k) / computeIDCG(k);
        
        return ndcg;
    }
    
    /**
     * This method compute the standard basic binary DCG metric.
     * 
     * @param input
     * @param groundTruth
     * @param k
     * @return dcg
     */
    public static double computeDCG(List input, List groundTruth, int k) throws Exception {
        if ((input == null) || (groundTruth == null) || (input.isEmpty()) || (groundTruth.isEmpty()) || (k <= 0)) {
            return 0.0;
        }
        
        double dcg = 0.0;
        
        // Items out of ranklist are irrelevant items and gain 0.
        // Reduce k to ranklist size to avoid out of range error and to imply 0 gain.
        int nK = k;
        if (nK > input.size()) {
            nK = input.size();
        }
        
        for (int i = 0; i < nK; i++) {
            if (groundTruth.contains(input.get(i))) {
                dcg += Math.log(2) / Math.log(i + 2);
            }
        }
        
        return dcg;
    }

    /**
     * This method computes DCG metric of rankList with ideal order.
     * The assumption here is: the result list could content all items, so it could content k relevant items.
     * 
     * @param k
     * @return idcg
     */
    public static double computeIDCG(int k) throws Exception {
        if (k <= 0) {
            return 0.0;
        }
        
        double idcg = 0.0;

        // With our assumption, the ideal list contents k relevant items.
        // So we sum up all the item.
        for (int i = 0; i < k; i++) {
            idcg += Math.log(2) / Math.log(i + 2);
        }
        
        return idcg;
    }
    
    /**
     * This method computes the cumulated gain, i.e., it does not consider position of ranked items.
     * 
     * @param input
     * @param groundTruth
     * @param k
     * @return cg
     */
    public static double computeCG(List input, List groundTruth, int k) throws Exception {
        if ((input == null) || (groundTruth == null) || (input.isEmpty()) || (groundTruth.isEmpty()) || (k <= 0)) {
            return 0.0;
        }
        
        double cg = 0.0;
        
        // Items out of ranklist are irrelevant items and gain 0.
        // Reduce k to ranklist size to avoid out of range error and to imply 0 gain.
        int nK = k;
        if (nK > input.size()) {
            nK = input.size();
        }
        
        for (int i = 0; i < nK; i++) {
            if (groundTruth.contains(input.get(i))) {
                cg += 1;
            }
        }
        
        return cg;
    }
}

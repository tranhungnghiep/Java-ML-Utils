/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package thn.research.textutility.general;

/**
 *
 * @author THNghiep
 */
public class NumericUtility {

    /**
     * Normalize value (cosine similarity) to the range between 0 and 1.
     *
     * @param d
     * @return standardized value of d.
     */
    public static double normalizeValue(double d) {

        if (d < 0) {
            d = 0.0;
        } else if (d > 1) {
            d = 1.0;
        } else if (Double.isNaN(d)) {
            d = 0.0;
        }

        return d;
    }

    /**
     * Check String is Numeric or not.
     *
     * @param strNum
     * @return
     */
    public static boolean isNum(String strNum) {
        boolean result = true;
        try {
            Double.parseDouble(strNum);
        } catch (NumberFormatException e) {
            result = false;
        }
        return result;
    }
}

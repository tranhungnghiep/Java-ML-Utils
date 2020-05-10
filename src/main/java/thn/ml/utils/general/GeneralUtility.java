/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package thn.research.textutility.general;

/**
 *
 * @author THNghiep
 */
public class GeneralUtility {

    public static int getNumOfCore() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.availableProcessors();
    }
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package thn.research.textutility.general;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author THNghiep
 */
public class ArrayUtility {

    public static void shufflePrimitiveArray(int[] array) throws Exception {
        List<Integer> list = new ArrayList<>();
        for (int i : array) {
            list.add(i);
        }

        Collections.shuffle(list);

        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
    }
}

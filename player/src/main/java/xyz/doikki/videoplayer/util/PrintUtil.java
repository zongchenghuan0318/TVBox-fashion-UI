package xyz.doikki.videoplayer.util;

import com.lcstudio.commonsurport.L;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PrintUtil {
    private static final String TAG = PrintUtil.class.getSimpleName();

    public static Map<String, Long> successMap = new HashMap<>();
    public static Map<String, String> playErrorMap = new HashMap<>();

    public static void saveError(String key, String err) {
        playErrorMap.put(key, err);
    }

    public static void printPlayErrorMap() {
        L.i(TAG+"printPlayErrorMap 2");
        List<Map.Entry<String, String>> entrys = new ArrayList(playErrorMap.entrySet());
        Collections.sort(entrys, new MyComparator2());
        for (Map.Entry<String, String> entry : entrys) {
            L.i(TAG + entry.getKey() + ":" + entry.getValue());
        }
    }

    public static void saveSuccess(String key, Long value) {
        successMap.put(key, value);
    }

    public static void printSuccessMap() {
        List<Map.Entry<String, Long>> entrys = new ArrayList(successMap.entrySet());
        Collections.sort(entrys, new MyComparator());
        L.i(TAG + "printSuccessMap ");
        for (Map.Entry<String, Long> entry : entrys) {
            L.i(TAG + entry.getKey() + ":" + entry.getValue());
        }
    }

    static class MyComparator implements Comparator<Map.Entry> {
        @Override
        public int compare(Map.Entry o1, Map.Entry o2) {
            if ((Long) o1.getValue() > (Long) o2.getValue()) {
                return 1;
            }
            return -1;
        }
    }

    static class MyComparator2 implements Comparator<Map.Entry> {
        @Override
        public int compare(Map.Entry o1, Map.Entry o2) {
            return ((String) o1.getKey()).compareTo((String) o2.getKey());
        }
    }


}

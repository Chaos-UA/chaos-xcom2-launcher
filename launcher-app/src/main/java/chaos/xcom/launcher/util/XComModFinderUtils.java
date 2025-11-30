package chaos.xcom.launcher.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class XComModFinderUtils {

    public static final String MOD_FILE_EXTENSION = ".XComMod";

    public static List<File> findModFiles(File modDir, int maxSubDirsCount) {
        List<File> result = new ArrayList<>();
        search(modDir, 0, maxSubDirsCount, result);
        return result;
    }

    private static void search(File dir, int depth, int maxDepth, List<File> result) {
        if (depth > maxDepth) {
            return;
        }
        if (!dir.isDirectory()) {
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        // 1. Переглядаємо файли в поточній директорії
        for (File f : files) {
            if (f.isFile() && f.getName().toLowerCase().endsWith(MOD_FILE_EXTENSION.toLowerCase())) {
                result.add(f);
                return;
            }
        }

        for (File f : files) {
            if (f.isDirectory()) {
                search(f, depth + 1, maxDepth, result);
            }
        }
    }
}

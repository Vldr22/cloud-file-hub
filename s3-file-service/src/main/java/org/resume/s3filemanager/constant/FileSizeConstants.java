package org.resume.s3filemanager.constant;

import lombok.experimental.UtilityClass;

/**
 * Константы форматирования размера файла.
 */
@UtilityClass
public class FileSizeConstants {

    public static final String TO_MEGABYTES = "toMegabytes";
    public static final double BYTES_IN_MB = 1_048_576.0;
    public static final String MB_FORMAT = "%.2f MB";
}

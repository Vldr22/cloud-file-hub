package org.resume.s3filemanager.constant;

import lombok.experimental.UtilityClass;

/**
 * Базовые пути REST API.
 */
@UtilityClass
public class ApiPaths {

    public static final String API_V1 = "/api/v1";

    public static final String AUTH = API_V1 + "/auth";
    public static final String FILES = API_V1 + "/files";
    public static final String HOME = API_V1 + "/home";
    public static final String ADMIN = API_V1 + "/admin";
}

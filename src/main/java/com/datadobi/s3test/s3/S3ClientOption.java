package com.datadobi.s3test.s3;

import com.datadobi.s3test.util.SystemPropertyOption;

class S3ClientOption {
    public static final String OPTION_PREFIX = "com.datadobi.s3.sdk";

    public static final SystemPropertyOption<Integer> MAX_CONNECTIONS = SystemPropertyOption.createIntOption(OPTION_PREFIX, "max_connections");
    public static final SystemPropertyOption<Integer> CONNECT_TIMEOUT_SECONDS = SystemPropertyOption.createIntOption(OPTION_PREFIX, "connect_timout_seconds");
    public static final SystemPropertyOption<Integer> SOCKET_TIMEOUT_SECONDS = SystemPropertyOption.createIntOption(OPTION_PREFIX, "socket_timeout_seconds");

    public static final SystemPropertyOption<Integer> NUM_RETRIES = SystemPropertyOption.createIntOption(OPTION_PREFIX, "num_retries");
    public static final SystemPropertyOption<Integer> API_CALL_TIMEOUT_SECONDS = SystemPropertyOption.createIntOption(OPTION_PREFIX,
            "api_call_timeout_seconds");
    public static final SystemPropertyOption<Integer> API_CALL_ATTEMPT_TIMEOUT_SECONDS = SystemPropertyOption.createIntOption(OPTION_PREFIX,
            "api_call_attempt_timeout_seconds");
}
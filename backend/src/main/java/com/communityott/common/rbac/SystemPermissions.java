package com.communityott.common.rbac;

public final class SystemPermissions {

    private SystemPermissions() {}

    // User Module
    public static final String USER_VIEW = "USER_VIEW";
    public static final String USER_CREATE = "USER_CREATE";
    public static final String USER_UPDATE = "USER_UPDATE";
    public static final String USER_SUSPEND = "USER_SUSPEND";
    public static final String USER_DELETE = "USER_DELETE";

    // Role Module
    public static final String ROLE_VIEW = "ROLE_VIEW";
    public static final String ROLE_CREATE = "ROLE_CREATE";
    public static final String ROLE_UPDATE = "ROLE_UPDATE";
    public static final String ROLE_DELETE = "ROLE_DELETE";
    public static final String ROLE_ASSIGN = "ROLE_ASSIGN";

    // Permission Module
    public static final String PERMISSION_VIEW = "PERMISSION_VIEW";
    public static final String PERMISSION_ASSIGN = "PERMISSION_ASSIGN";

    // Content Module
    public static final String CONTENT_VIEW = "CONTENT_VIEW";
    public static final String CONTENT_CREATE = "CONTENT_CREATE";
    public static final String CONTENT_UPDATE = "CONTENT_UPDATE";
    public static final String CONTENT_DELETE = "CONTENT_DELETE";
    public static final String CONTENT_SUBMIT = "CONTENT_SUBMIT";
    public static final String CONTENT_PUBLISH = "CONTENT_PUBLISH";
    public static final String CONTENT_ARCHIVE = "CONTENT_ARCHIVE";
    public static final String CONTENT_METADATA_UPDATE = "CONTENT_METADATA_UPDATE";

    // Category Module
    public static final String CATEGORY_VIEW = "CATEGORY_VIEW";
    public static final String CATEGORY_CREATE = "CATEGORY_CREATE";
    public static final String CATEGORY_UPDATE = "CATEGORY_UPDATE";
    public static final String CATEGORY_DELETE = "CATEGORY_DELETE";

    // Language Module
    public static final String LANGUAGE_VIEW = "LANGUAGE_VIEW";
    public static final String LANGUAGE_CREATE = "LANGUAGE_CREATE";
    public static final String LANGUAGE_UPDATE = "LANGUAGE_UPDATE";
    public static final String LANGUAGE_DELETE = "LANGUAGE_DELETE";

    // Video Module
    public static final String VIDEO_UPLOAD = "VIDEO_UPLOAD";
    public static final String VIDEO_VIEW = "VIDEO_VIEW";
    public static final String VIDEO_EDIT = "VIDEO_EDIT";
    public static final String VIDEO_DELETE = "VIDEO_DELETE";
    public static final String VIDEO_PROCESS = "VIDEO_PROCESS";
    public static final String VIDEO_RETRY = "VIDEO_RETRY";
    public static final String VIDEO_PUBLISH = "VIDEO_PUBLISH";

    // Analytics Module
    public static final String ANALYTICS_VIEW = "ANALYTICS_VIEW";
    public static final String ANALYTICS_EXPORT = "ANALYTICS_EXPORT";

    // Notification Module
    public static final String NOTIFICATION_VIEW = "NOTIFICATION_VIEW";
    public static final String NOTIFICATION_SEND = "NOTIFICATION_SEND";

    // Audit Module
    public static final String AUDIT_VIEW = "AUDIT_VIEW";
    public static final String AUDIT_EXPORT = "AUDIT_EXPORT";

    // System Settings Module
    public static final String SYSTEM_SETTINGS_VIEW = "SYSTEM_SETTINGS_VIEW";
    public static final String SYSTEM_SETTINGS_UPDATE = "SYSTEM_SETTINGS_UPDATE";
    public static final String SYSTEM_HEALTH_VIEW = "SYSTEM_HEALTH_VIEW";
}

# Preserve line numbers for crash diagnostics (R8 retrace) while hiding original source filenames
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep PrivilegedHelperMain entry point launched via app_process
-keep class com.stormpanda.betterdualscreenassistant.privileged.PrivilegedHelperMain {
    public static void main(java.lang.String[]);
    *;
}
-keep class com.stormpanda.betterdualscreenassistant.privileged.PrivilegedHelperMain$* { *; }

# Keep AssistantForegroundService class name (targeted explicitly via shell `am start-foreground-service`
# and `Intent.setClassName` from PrivilegedHelperMain's persistent supervisor)
-keepnames class com.stormpanda.betterdualscreenassistant.service.AssistantForegroundService

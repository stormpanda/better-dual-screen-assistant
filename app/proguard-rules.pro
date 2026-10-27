# Keep PrivilegedHelperMain entry point launched via app_process
-keep class com.stormpanda.betterdualscreenassistant.privileged.PrivilegedHelperMain {
    public static void main(java.lang.String[]);
}
-keep class com.stormpanda.betterdualscreenassistant.privileged.PrivilegedHelperMain$* { *; }

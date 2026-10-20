package com.victorious.launch.shizuku;

interface IPrivilegedService {
    String freeze(String packageName, boolean frozen, int userId);
    String hide(String packageName, boolean hidden, int userId);
    String forceStop(String packageName, int userId);
    String grantUsageAccess(String packageName);
    String createManagedProfile(String name);
    String quietMode(int userId, boolean quiet);
    String installExisting(String packageName, int userId);
    void destroy();
}

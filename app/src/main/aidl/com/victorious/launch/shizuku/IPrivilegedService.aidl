package com.victorious.launch.shizuku;

interface IPrivilegedService {
    String freeze(String packageName, boolean frozen);
    String hide(String packageName, boolean hidden);
    String forceStop(String packageName);
    String grantUsageAccess(String packageName);
    String createManagedProfile(String name);
    String quietMode(int userId, boolean quiet);
    String installExisting(String packageName, int userId);
    void destroy();
}

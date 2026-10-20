package com.victorious.launch.shizuku;

interface IPrivilegedService {
    String freeze(String packageName, boolean frozen, int userId);
    String hide(String packageName, boolean hidden, int userId);
    String forceStop(String packageName, int userId);
    String grantUsageAccess(String packageName);
    String createManagedProfile(String name);
    String quietMode(int userId, boolean quiet);
    String installExisting(String packageName, int userId);
    String grantWriteSecureSettings(String packageName);
    String listAdbModules();
    String packagePath(String packageName);
    String installRootModule(String kind, String zipPath);
    String setAllowedNetworkTypes(long bitmask);
    String setAllowed2g(boolean allowed);
    String setPreferredNetworkMode(int mode);
    String radioRecover();
    void destroy();
}

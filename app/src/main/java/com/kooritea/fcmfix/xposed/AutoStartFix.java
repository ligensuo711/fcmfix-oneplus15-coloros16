package com.kooritea.fcmfix.xposed;

import android.content.Intent;
import android.content.pm.ApplicationInfo;

import java.lang.reflect.Method;

import com.kooritea.fcmfix.libxposed.XC_MethodHook;
import com.kooritea.fcmfix.libxposed.XposedBridge;
import com.kooritea.fcmfix.libxposed.XposedHelpers;

import static com.kooritea.fcmfix.xposed.OplusHooks.findIntentArgument;
import static com.kooritea.fcmfix.xposed.OplusHooks.runHook;

public class AutoStartFix extends XposedModule {
    // PHK110 (OnePlus Ace 2 / ColorOS 16 V16.0.0): stopped-app veto points that the
    // upstream hook set (built against OnePlus 15 / ColorOS 17) does not cover on this
    // firmware. Each group is guarded: a missing method only records a failed status.
    private static final String CLASS_OPLUS_STARTUP_MANAGER = "com.android.server.am.OplusAppStartupManager";
    private static final String CLASS_OPLUS_SCENE_MANAGER = "com.android.server.am.OplusSceneManager";
    private static final String CLASS_OPLUS_HANS_MANAGER = "com.android.server.am.OplusHansManager";
    private static final String CLASS_BROADCAST_QUEUE_EXT_IMPL = "com.android.server.am.BroadcastQueueModernImplExtImpl";

    public AutoStartFix(ClassLoader classLoader){
        super(classLoader);
        runHook("OplusAppStartupManager receiver restriction", this::startHook);
        runHook("PHK110 process-start restriction", this::startHookPhk110ProcessStart);
        runHook("PHK110 receiver-restriction check", this::startHookPhk110ReceiverRestriction);
        runHook("PHK110 hans service-start check", this::startHookPhk110HansService);
        runHook("PHK110 broadcast-queue skip check", this::startHookPhk110BroadcastQueueSkip);
    }

    protected void startHook(){
        // OOS/COS 15/16: ColorOS updates have changed the parameter count.
        // Hook every boolean overload and discover the Intent at runtime.
        Class<?> startupManager = XposedHelpers.findClass(
                "com.android.server.am.OplusAppStartupManager", classLoader);
        int hookCount = 0;
        String[] methodNames = new String[]{
                "shouldPreventSendReceiverReal",
                "shouldPreventSendReceiver"
        };
        for (Method method : startupManager.getDeclaredMethods()) {
            if (!contains(methodNames, method.getName())) {
                continue;
            }
            if (method.getReturnType() != boolean.class
                    && method.getReturnType() != Boolean.class) {
                continue;
            }
            XposedBridge.hookMethod(method,new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam methodHookParam) {
                    Intent intent = findIntentArgument(methodHookParam.args);
                    if (intent == null || !isFCMIntent(intent)) {
                        return;
                    }
                    String target = intent.getComponent() == null
                            ? intent.getPackage()
                            : intent.getComponent().getPackageName();
                    if (target == null) {
                        target = findAllowedPackageArgument(methodHookParam.args);
                    }
                    if (trustedDelivery(intent, target, methodHookParam)) {
                        printLog("Oplus auto-start bypass: pkg=" + target
                                + ", method=" + method.getName(), true);
                        methodHookParam.setResult(false);
                    }
                }
            });
            hookCount++;
            printLog("Oplus auto-start hook active: " + method.getName()
                    + "/" + method.getParameterCount());
        }
        if (hookCount == 0) {
            throw new NoSuchMethodError("OplusAppStartupManager#shouldPreventSendReceiver(Real)");
        }
    }

    /**
     * PHK110 / ColorOS 16 (V16.0.0): the stopped-app auto-start veto lives in
     * OplusAppStartupManager.shouldPreventStartProcess(AMS, ProcessRecord) -- the
     * sibling of shouldPreventSendReceiver, covering generic process starts.
     * OnePlus 15 firmware has BroadcastQueueModernStubImpl.checkApplicationAutoStart
     * instead, which does not exist on PHK110. The veto carries no Intent, so the
     * decision uses the FCM delivery window opened by BroadcastFix/OplusProxyFix
     * for the target uid.
     */
    private void startHookPhk110ProcessStart() {
        Class<?> startupManager = XposedHelpers.findClass(CLASS_OPLUS_STARTUP_MANAGER, classLoader);
        int hookCount = 0;
        for (Method method : startupManager.getDeclaredMethods()) {
            if (!"shouldPreventStartProcess".equals(method.getName())
                    || !OplusHooks.isBooleanType(method.getReturnType())
                    || method.getParameterCount() != 2) {
                continue;
            }
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam methodHookParam) {
                    Object processRecord = methodHookParam.args.length > 1
                            ? methodHookParam.args[1] : null;
                    Integer uid = findProcessRecordUid(processRecord);
                    if (uid == null || !OplusProxyFix.isInFcmDeliveryWindow(uid)) {
                        return;
                    }
                    printLog("PHK110 process-start bypass (delivery window): uid=" + uid, true);
                    methodHookParam.setResult(false);
                }
            });
            hookCount++;
            printLog("PHK110 shouldPreventStartProcess hook active: " + OplusHooks.describeMethod(method));
        }
        if (hookCount == 0) {
            throw new NoSuchMethodError("OplusAppStartupManager#shouldPreventStartProcess");
        }
    }

    /**
     * PHK110: OplusSceneManager.checkReceiverIfRestricted(BroadcastRecord, Object)
     * rejects FCM receivers through the scene manager before process start.
     */
    private void startHookPhk110ReceiverRestriction() {
        Class<?> sceneManager = XposedHelpers.findClass(CLASS_OPLUS_SCENE_MANAGER, classLoader);
        int hookCount = 0;
        for (Method method : sceneManager.getDeclaredMethods()) {
            if (!"checkReceiverIfRestricted".equals(method.getName())
                    || !OplusHooks.isBooleanType(method.getReturnType())) {
                continue;
            }
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam methodHookParam) {
                    Intent intent = findIntentArgument(methodHookParam.args);
                    if (intent == null || !isFCMIntent(intent)) {
                        return;
                    }
                    String target = findPhk110Target(intent, methodHookParam.args);
                    if (target != null && phk110Trusted(intent, target, methodHookParam)) {
                        printLog("PHK110 receiver-restriction bypass: pkg=" + target, true);
                        methodHookParam.setResult(false);
                    }
                }
            });
            hookCount++;
            printLog("PHK110 checkReceiverIfRestricted hook active: " + OplusHooks.describeMethod(method));
        }
        if (hookCount == 0) {
            throw new NoSuchMethodError("OplusSceneManager#checkReceiverIfRestricted");
        }
    }

    /**
     * PHK110: OplusHansManager.isAllowStartService(String pkg, String process, int userId,
     * String action, String type, String callerPackage, int flags) governs service
     * starts from frozen/background context. Allow FCM targets through.
     */
    private void startHookPhk110HansService() {
        Class<?> hansManager = XposedHelpers.findClass(CLASS_OPLUS_HANS_MANAGER, classLoader);
        int hookCount = 0;
        for (Method method : hansManager.getDeclaredMethods()) {
            if (!"isAllowStartService".equals(method.getName())
                    || !OplusHooks.isBooleanType(method.getReturnType())) {
                continue;
            }
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam methodHookParam) {
                    String target = findPhk110PackageArgument(methodHookParam.args);
                    if (target == null) {
                        return;
                    }
                    if (targetIsAllow(target)) {
                        printLog("PHK110 hans service-start bypass: pkg=" + target, true);
                        methodHookParam.setResult(true);
                    }
                }
            });
            hookCount++;
            printLog("PHK110 isAllowStartService hook active: " + OplusHooks.describeMethod(method));
        }
        if (hookCount == 0) {
            throw new NoSuchMethodError("OplusHansManager#isAllowStartService");
        }
    }

    /**
     * PHK110: IBroadcastQueueExt.shouldPreventStartProcessForBroadcast has no
     * implementation on this firmware (interface-only), but the BroadcastQueue ext
     * wrapper BroadcastQueueModernImplExtImpl applies startup policy through
     * skipScheduleReceiverColdLocked/skipScheduleReceiverWarmLocked, which return a
     * SKIP_REASON_* string (null = do not skip). Force null for allowed FCM
     * receivers so a stopped app's process can be started for delivery.
     */
    private void startHookPhk110BroadcastQueueSkip() {
        Class<?> extImpl = XposedHelpers.findClassIfExists(CLASS_BROADCAST_QUEUE_EXT_IMPL, classLoader);
        int hookCount = 0;
        if (extImpl != null) {
            for (Method method : extImpl.getDeclaredMethods()) {
                if (!method.getName().startsWith("skipScheduleReceiver")) {
                    continue;
                }
                if (method.getReturnType() == void.class) {
                    continue;
                }
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam methodHookParam) {
                        Intent intent = findIntentArgument(methodHookParam.args);
                        if (intent == null || !isFCMIntent(intent)) {
                            return;
                        }
                        String target = findPhk110Target(intent, methodHookParam.args);
                        if (target == null || !phk110Trusted(intent, target, methodHookParam)) {
                            return;
                        }
                        if (method.getReturnType() == String.class) {
                            printLog("PHK110 bq skip bypass: pkg=" + target, true);
                            methodHookParam.setResult(null);
                        } else if (OplusHooks.isBooleanType(method.getReturnType())) {
                            printLog("PHK110 bq skip bypass(bool): pkg=" + target, true);
                            methodHookParam.setResult(false);
                        }
                    }
                });
                hookCount++;
                printLog("PHK110 skipScheduleReceiver hook active: " + OplusHooks.describeMethod(method));
            }
        }
        if (hookCount == 0) {
            throw new NoSuchMethodError("BroadcastQueueModernImplExtImpl#skipScheduleReceiver*");
        }
    }

    /**
     * PHK110 bypass gate: accept the upstream trusted-delivery verdict (GMS-origin
     * BroadcastRecord / caller attribution) or, on call chains upstream's attribution
     * table does not know, an allowlisted FCM target inside an active delivery window
     * (the window is only opened by a verified FCM entry).
     */
    private boolean phk110Trusted(Intent intent, String target, XC_MethodHook.MethodHookParam param) {
        if (intent == null || target == null || !targetIsAllow(target)) {
            return false;
        }
        if (trustedDelivery(intent, target, param)) {
            return true;
        }
        int uid = OplusHooks.getTargetUidFromPackageName(target);
        return uid >= 0 && OplusProxyFix.isInFcmDeliveryWindow(uid);
    }

    private String findPhk110Target(Intent intent, Object[] args) {
        String target = OplusHooks.getIntentTarget(intent);
        if (target != null) {
            return target;
        }
        return findAllowedPackageArgument(args);
    }

    private String findPhk110PackageArgument(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof String && targetIsAllow((String) arg)) {
                return (String) arg;
            }
        }
        for (Object arg : args) {
            if (arg instanceof ApplicationInfo) {
                return ((ApplicationInfo) arg).packageName;
            }
        }
        return null;
    }

    /** Extract uid from an android.app.LoadedApk-style ProcessRecord (uid / mUid / appId fields). */
    private static Integer findProcessRecordUid(Object processRecord) {
        if (processRecord == null) {
            return null;
        }
        for (String field : new String[]{"uid", "mUid", "appId"}) {
            try {
                Object value = XposedHelpers.getObjectField(processRecord, field);
                if (value instanceof Integer && (Integer) value >= 10000) {
                    // >=10000 covers both full uid (user 0) and appId; FCM targets are apps.
                    return (Integer) value;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private boolean contains(String[] values, String wanted) {
        for (String value : values) {
            if (value.equals(wanted)) return true;
        }
        return false;
    }
}

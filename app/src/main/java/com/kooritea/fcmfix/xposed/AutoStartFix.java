package com.kooritea.fcmfix.xposed;

import android.content.Intent;

import com.kooritea.fcmfix.util.XposedUtils;

import java.lang.reflect.Method;

import com.kooritea.fcmfix.libxposed.XC_MethodHook;
import com.kooritea.fcmfix.libxposed.XposedBridge;
import com.kooritea.fcmfix.libxposed.XposedHelpers;

public class AutoStartFix extends XposedModule {
    private final String FCM_RECEIVE = ".android.c2dm.intent.RECEIVE";
    private static final String CLASS_OPLUS_STARTUP_MANAGER = "com.android.server.am.OplusAppStartupManager";
    private static final String CLASS_OPLUS_SCENE_MANAGER = "com.android.server.am.OplusSceneManager";
    private static final String CLASS_OPLUS_HANS_MANAGER = "com.android.server.am.OplusHansManager";

    public AutoStartFix(ClassLoader classLoader){
        super(classLoader);
        try{
            this.startHook();
            this.startHookRemovePowerPolicy();
        }catch (Throwable e) {
            printLog("hook error AutoStartFix:" + e.getMessage());
        }
    }

    protected void startHook(){
        try{
            // miui12
            Class<?> BroadcastQueueInjector = XposedHelpers.findClass("com.android.server.am.BroadcastQueueInjector",classLoader);
            XposedUtils.findAndHookMethodAnyParam(BroadcastQueueInjector,"checkApplicationAutoStart",new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam methodHookParam) {
                    Intent intent = (Intent) XposedHelpers.getObjectField(methodHookParam.args[2], "intent");
                    if(isFCMIntent(intent)){
                        String target = intent.getComponent() == null ? intent.getPackage() : intent.getComponent().getPackageName();
                        if(targetIsAllow(target)){
                            XposedHelpers.callStaticMethod(BroadcastQueueInjector,"checkAbnormalBroadcastInQueueLocked", methodHookParam.args[1], methodHookParam.args[0]);
                            printLog("Allow Auto Start: " + target, true);
                            methodHookParam.setResult(true);
                        }
                    }
                }
            });
        }catch (XposedHelpers.ClassNotFoundError | NoSuchMethodError  e){
            printLog("No Such Method com.android.server.am.BroadcastQueueInjector.checkApplicationAutoStart");
        }
        try{
            // miui13
            Class<?> BroadcastQueueImpl = XposedHelpers.findClass("com.android.server.am.BroadcastQueueImpl",classLoader);
            XposedUtils.findAndHookMethodAnyParam(BroadcastQueueImpl,"checkApplicationAutoStart",new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam methodHookParam) {
                    Intent intent = (Intent) XposedHelpers.getObjectField(methodHookParam.args[1], "intent");
                    if(isFCMIntent(intent)){
                        String target = intent.getComponent() == null ? intent.getPackage() : intent.getComponent().getPackageName();
                        if(targetIsAllow(target)){
                            XposedHelpers.callMethod(methodHookParam.thisObject, "checkAbnormalBroadcastInQueueLocked", methodHookParam.args[0]);
                            printLog("Allow Auto Start: " + target, true);
                            methodHookParam.setResult(true);
                        }
                    }
                }
            });
        }catch (XposedHelpers.ClassNotFoundError | NoSuchMethodError  e){
            printLog("No Such Method com.android.server.am.BroadcastQueueImpl.checkApplicationAutoStart");
        }

        try{
            // hyperos
            Class<?> BroadcastQueueImpl = XposedHelpers.findClass("com.android.server.am.BroadcastQueueModernStubImpl",classLoader);
            printLog("[fcmfix] start hook com.android.server.am.BroadcastQueueModernStubImpl.checkApplicationAutoStart");
            XposedUtils.findAndHookMethodAnyParam(BroadcastQueueImpl,"checkApplicationAutoStart", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam methodHookParam) {
                    Intent intent = (Intent) XposedHelpers.getObjectField(methodHookParam.args[1], "intent");
                    String target = intent.getComponent() == null ? intent.getPackage() : intent.getComponent().getPackageName();
                    if (targetIsAllow(target)) {
                        // 无日志，先放了
                        printLog("[" + intent.getAction() + "]checkApplicationAutoStart package_name: " + target, true);
                        methodHookParam.setResult(true);
//                        if(isFCMIntent(intent)){
//                            printLog("checkApplicationAutoStart package_name: " + target, true);
//                            methodHookParam.setResult(true);
//                        }else{
//                            printLog("[skip][" + intent.getAction() + "]checkApplicationAutoStart package_name: " + target, true);
//                        }

                    }
                }
            });

            printLog("[fcmfix] start hook com.android.server.am.BroadcastQueueModernStubImpl.checkReceiverIfRestricted");
            XposedUtils.findAndHookMethodAnyParam(BroadcastQueueImpl,"checkReceiverIfRestricted", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam methodHookParam) {
                    Intent intent = (Intent) XposedHelpers.getObjectField(methodHookParam.args[1], "intent");
                    String target = intent.getComponent() == null ? intent.getPackage() : intent.getComponent().getPackageName();
                    if(targetIsAllow(target)){
                        if(isFCMIntent(intent)){
                            printLog("BroadcastQueueModernStubImpl.checkReceiverIfRestricted package_name: " + target, true);
                            methodHookParam.setResult(false);
                        }
                    }
                }
            });
        }catch (XposedHelpers.ClassNotFoundError | NoSuchMethodError  e){
            printLog("No Such class com.android.server.am.BroadcastQueueModernStubImpl");
        }

        try {
            Class<?> AutoStartManagerServiceStubImpl = XposedHelpers.findClass("com.android.server.am.AutoStartManagerServiceStubImpl", classLoader);
            XC_MethodHook methodHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam methodHookParam) {
                    Intent intent = (Intent) methodHookParam.args[1];
                    String target = intent.getComponent().getPackageName();
                    if(targetIsAllow(target)) {
                        // 拿不到action，先放了
                        printLog("[" + intent.getAction() + "]AutoStartManagerServiceStubImpl.isAllowStartService package_name: " + target, true);
                        methodHookParam.setResult(true);
//                        if(isFCMIntent(intent)){
//                            printLog("AutoStartManagerServiceStubImpl.isAllowStartService package_name: " + target, true);
//                            methodHookParam.setResult(true);
//                        }else{
//                            printLog("[skip][" + intent.getAction() + "]AutoStartManagerServiceStubImpl.isAllowStartService package_name: " + target, true);
//                        }
                    }
                }
            };

            printLog("[fcmfix] start hook com.android.server.am.AutoStartManagerServiceStubImpl.isAllowStartService");
            XC_MethodHook.Unhook unhook1 = XposedUtils.tryFindAndHookMethod(AutoStartManagerServiceStubImpl, "isAllowStartService", 3, methodHook);
            XC_MethodHook.Unhook unhook2 = XposedUtils.tryFindAndHookMethod(AutoStartManagerServiceStubImpl, "isAllowStartService", 4, methodHook);
            if(unhook1 == null && unhook2 == null){
                throw new NoSuchMethodError();
            }
        } catch (XposedHelpers.ClassNotFoundError | NoSuchMethodError  e){
            printLog("No Such Class com.android.server.am.AutoStartManagerServiceStubImpl.isAllowStartService");
        }

        try {
            Class<?> SmartPowerService = XposedHelpers.findClass("com.android.server.am.SmartPowerService", classLoader);

            printLog("[fcmfix] start hook com.android.server.am.SmartPowerService.shouldInterceptBroadcast");
            XposedUtils.findAndHookMethodAnyParam(SmartPowerService, "shouldInterceptBroadcast", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam methodHookParam) {
                    Intent intent = (Intent) XposedHelpers.getObjectField(methodHookParam.args[1], "intent");
                    String target = intent.getComponent() == null ? intent.getPackage() : intent.getComponent().getPackageName();
                    if(targetIsAllow(target)) {
                        if(isFCMIntent(intent)){
                            printLog("SmartPowerService.shouldInterceptBroadcast package_name: " + target, true);
                            methodHookParam.setResult(false);
                        }
                    }
                }
            });
        } catch (XposedHelpers.ClassNotFoundError | NoSuchMethodError  e){
            printLog("No Such Class com.android.server.am.SmartPowerService");
        }

        try{
            // OOS/COS 15/16: ColorOS updates have changed the parameter count.
            // Hook every boolean overload and discover the Intent at runtime.
            Class<?> startupManager = XposedHelpers.findClass(
                    CLASS_OPLUS_STARTUP_MANAGER, classLoader);
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
                        if (target != null && targetIsAllow(target)) {
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
                throw new NoSuchMethodError();
            }
        } catch (XposedHelpers.ClassNotFoundError | NoSuchMethodError  e) {
            printLog("No compatible OplusAppStartupManager receiver restriction method");
        }

        try{
            // PHK110 / ColorOS 16 (V16.0.0): the stopped-app auto-start veto lives in
            // OplusAppStartupManager.shouldPreventStartProcess(AMS, ProcessRecord) -- the
            // sibling of shouldPreventSendReceiver, covering generic process starts.
            // PLK110 firmware has BroadcastQueueModernStubImpl.checkApplicationAutoStart
            // instead, which does not exist on PHK110. The veto carries no Intent, so the
            // decision uses the FCM delivery window opened by BroadcastFix/OplusProxyFix
            // for the target uid.
            Class<?> startupManager = XposedHelpers.findClass(CLASS_OPLUS_STARTUP_MANAGER, classLoader);
            int hookCount = 0;
            for (Method method : startupManager.getDeclaredMethods()) {
                if (!"shouldPreventStartProcess".equals(method.getName())
                        || !isBooleanReturnType(method)
                        || method.getParameterCount() != 2) {
                    continue;
                }
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam methodHookParam) {
                        Object processRecord = methodHookParam.args.length > 1
                                ? methodHookParam.args[1] : null;
                        Integer uid = findProcessRecordUid(processRecord);
                        if (uid == null || !OplusProxyFix.isInFcmWindow(uid)) {
                            return;
                        }
                        printLog("PHK110 process-start bypass (delivery window): uid=" + uid, true);
                        methodHookParam.setResult(false);
                    }
                });
                hookCount++;
                printLog("PHK110 shouldPreventStartProcess hook active: " + method.toString());
            }
            if (hookCount == 0) {
                throw new NoSuchMethodError("OplusAppStartupManager.shouldPreventStartProcess");
            }
        } catch (XposedHelpers.ClassNotFoundError | NoSuchMethodError  e) {
            printLog("PHK110 hook missing OplusAppStartupManager.shouldPreventStartProcess: " + e.getMessage());
        }

        try{
            // PHK110: OplusSceneManager.checkReceiverIfRestricted(BroadcastRecord, Object)
            // rejects FCM receivers through the scene manager before process start.
            Class<?> sceneManager = XposedHelpers.findClass(CLASS_OPLUS_SCENE_MANAGER, classLoader);
            int hookCount = 0;
            for (Method method : sceneManager.getDeclaredMethods()) {
                if (!"checkReceiverIfRestricted".equals(method.getName())
                        || !isBooleanReturnType(method)) {
                    continue;
                }
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam methodHookParam) {
                        Intent intent = findIntentArgumentDeep(methodHookParam.args);
                        if (intent == null || !isFCMIntent(intent)) {
                            return;
                        }
                        String target = findFcmTarget(intent, methodHookParam.args);
                        if (target != null && targetIsAllow(target)) {
                            printLog("PHK110 receiver-restriction bypass: pkg=" + target, true);
                            methodHookParam.setResult(false);
                        }
                    }
                });
                hookCount++;
                printLog("PHK110 checkReceiverIfRestricted hook active: " + method.toString());
            }
            if (hookCount == 0) {
                throw new NoSuchMethodError("OplusSceneManager.checkReceiverIfRestricted");
            }
        } catch (XposedHelpers.ClassNotFoundError | NoSuchMethodError  e) {
            printLog("PHK110 hook missing OplusSceneManager.checkReceiverIfRestricted: " + e.getMessage());
        }

        try{
            // PHK110: OplusHansManager.isAllowStartService(String pkg, String process, int userId,
            // String action, String type, String callerPackage, int flags) governs service
            // starts from frozen/background context. Allow FCM targets through.
            Class<?> hansManager = XposedHelpers.findClass(CLASS_OPLUS_HANS_MANAGER, classLoader);
            int hookCount = 0;
            for (Method method : hansManager.getDeclaredMethods()) {
                if (!"isAllowStartService".equals(method.getName())
                        || !isBooleanReturnType(method)) {
                    continue;
                }
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam methodHookParam) {
                        String target = findPackageNameArgument(methodHookParam.args);
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
                printLog("PHK110 isAllowStartService hook active: " + method.toString());
            }
            if (hookCount == 0) {
                throw new NoSuchMethodError("OplusHansManager.isAllowStartService");
            }
        } catch (XposedHelpers.ClassNotFoundError | NoSuchMethodError  e) {
            printLog("PHK110 hook missing OplusHansManager.isAllowStartService: " + e.getMessage());
        }

        try{
            // PHK110: IBroadcastQueueExt.shouldPreventStartProcessForBroadcast has no
            // implementation on this firmware (interface-only), but the BroadcastQueue ext
            // wrapper BroadcastQueueModernImplExtImpl applies startup policy through
            // skipScheduleReceiverColdLocked/skipScheduleReceiverWarmLocked, which return a
            // SKIP_REASON_* string (null = do not skip). Force null for allowed FCM
            // receivers so a stopped app's process can be started for delivery.
            Class<?> extImpl = XposedHelpers.findClassIfExists(
                    "com.android.server.am.BroadcastQueueModernImplExtImpl", classLoader);
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
                            // Fast path: no skip decision may apply while any FCM delivery
                            // window is active; scope the override to allowed FCM targets.
                            Intent intent = findIntentArgumentDeep(methodHookParam.args);
                            if (intent == null || !isFCMIntent(intent)) {
                                return;
                            }
                            String target = findFcmTarget(intent, methodHookParam.args);
                            if (target == null || !targetIsAllow(target)) {
                                return;
                            }
                            if (method.getReturnType() == String.class) {
                                printLog("PHK110 bq skip bypass: pkg=" + target, true);
                                methodHookParam.setResult(null);
                            } else if (isBooleanReturnType(method)) {
                                printLog("PHK110 bq skip bypass(bool): pkg=" + target, true);
                                methodHookParam.setResult(false);
                            }
                        }
                    });
                    hookCount++;
                    printLog("PHK110 skipScheduleReceiver hook active: " + method.toString());
                }
            }
            if (hookCount == 0) {
                throw new NoSuchMethodError("BroadcastQueueModernImplExtImpl.skipScheduleReceiver*");
            }
        } catch (Throwable e) {
            printLog("PHK110 hook missing BroadcastQueueModernImplExtImpl.skipScheduleReceiver*: " + e.getMessage());
        }
    }

    private static boolean isBooleanReturnType(Method method) {
        return method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class;
    }

    /**
     * Recursively search arguments (including fields of complex records) for the first Intent.
     */
    private Intent findIntentArgumentDeep(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof Intent) {
                return (Intent) arg;
            }
        }
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            try {
                Object nestedIntent = XposedHelpers.getObjectField(arg, "intent");
                if (nestedIntent instanceof Intent) {
                    return (Intent) nestedIntent;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private String findFcmTarget(Intent intent, Object[] args) {
        String target = intent.getComponent() == null
                ? intent.getPackage()
                : intent.getComponent().getPackageName();
        if (target != null) {
            return target;
        }
        return findAllowedPackageArgument(args);
    }

    private String findPackageNameArgument(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof String && targetIsAllow((String) arg)) {
                return (String) arg;
            }
        }
        for (Object arg : args) {
            if (arg instanceof android.content.pm.ApplicationInfo) {
                return ((android.content.pm.ApplicationInfo) arg).packageName;
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

    private Intent findIntentArgument(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof Intent) {
                return (Intent) arg;
            }
        }
        for (Object arg : args) {
            if (arg == null) continue;
            try {
                Object nestedIntent = XposedHelpers.getObjectField(arg, "intent");
                if (nestedIntent instanceof Intent) {
                    return (Intent) nestedIntent;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private String findAllowedPackageArgument(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof String && targetIsAllow((String) arg)) {
                return (String) arg;
            }
        }
        return null;
    }

    protected void startHookRemovePowerPolicy(){
        try {
            // MIUI13
            Class<?> AutoStartManagerService = XposedHelpers.findClass("com.miui.server.smartpower.SmartPowerPolicyManager",classLoader);
            XposedUtils.findAndHookMethodAnyParam(AutoStartManagerService,"shouldInterceptService",new XC_MethodHook() {

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Intent intent = (Intent) param.args[0];
                    if("com.google.firebase.MESSAGING_EVENT".equals(intent.getAction())){
                        String target = intent.getComponent() == null ? intent.getPackage() : intent.getComponent().getPackageName();
                        if(targetIsAllow(target)){
                            printLog("Disable MIUI Intercept: " + target, true);
                            param.setResult(false);
                        }
                    }
                }
            });
        } catch (XposedHelpers.ClassNotFoundError | NoSuchMethodError  e) {
            printLog("No Such Method com.miui.server.smartpower.SmartPowerPolicyManager.shouldInterceptService");
        }
    }
}

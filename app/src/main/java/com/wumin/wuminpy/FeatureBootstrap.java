package com.wumin.wuminpy;

import android.content.Context;

import com.wumin.ai.AIAgentTaskPusher;
import com.wumin.ai.AINavigationImpl;
import com.wumin.codeeditor.CoderNavigationImpl;
import com.wumin.core.AIAgentRuntime;
import com.wumin.ai.api.AINavigation;
import com.wumin.core.CoderNavigation;
import com.wumin.core.PythonNavigation;
import com.wumin.core.ScriptRuntime;
import com.wumin.core.TerminalNavigation;
import com.wumin.ai.api.AITaskPusher;
import com.wumin.core.ServiceRegistry;
import com.wumin.merminal.TerminalNavigationImpl;
import com.wumin.python.PythonNavigationImpl;
import com.wumin.python.ScriptManager;

public final class FeatureBootstrap {
    private FeatureBootstrap() {
    }

    public static void init(Context context) {
        ServiceRegistry.INSTANCE.register(AINavigation.class, new AINavigationImpl());
        ServiceRegistry.INSTANCE.register(AITaskPusher.class, new AIAgentTaskPusher());
        ServiceRegistry.INSTANCE.register(TerminalNavigation.class, new TerminalNavigationImpl());
        ServiceRegistry.INSTANCE.register(PythonNavigation.class, new PythonNavigationImpl());

        ScriptManager scriptManager = ScriptManager.getInstance();
        ServiceRegistry.INSTANCE.register(ScriptRuntime.class, scriptManager);
        ServiceRegistry.INSTANCE.register(AIAgentRuntime.class, scriptManager);

        CoderNavigationImpl coderNavigation = new CoderNavigationImpl();
        coderNavigation.init(context);
        ServiceRegistry.INSTANCE.register(CoderNavigation.class, coderNavigation);

        scriptManager.init(context);
    }
}

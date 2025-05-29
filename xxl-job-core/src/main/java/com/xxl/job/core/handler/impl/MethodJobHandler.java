package com.xxl.job.core.handler.impl;

import com.xxl.job.common.utils.JacksonUtil;
import com.xxl.job.core.handler.IJobHandler;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

/**
 * @author xuxueli 2019-12-11 21:12:18
 */
public class MethodJobHandler extends IJobHandler {

    private final Object target;
    private final Method method;
    private Method initMethod;
    private Method destroyMethod;

    public MethodJobHandler(Object target, Method method, Method initMethod, Method destroyMethod) {
        this.target = target;
        this.method = method;
        this.initMethod = initMethod;
        this.destroyMethod = destroyMethod;
    }

    @Override
    public void execute(String parameters) throws Exception {
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length > 0 && StringUtils.hasText(parameters)) {
            if (paramTypes.length > 1) {
                // method-param can not be primitive-types
                method.invoke(target, JacksonUtil.deserialize(parameters, paramTypes));
            } else {
                // 添加对单个参数的支持
                method.invoke(target, JacksonUtil.readValue(parameters, paramTypes[0]));
            }
        } else {
            method.invoke(target);
        }
    }

    @Override
    public void init() throws Exception {
        if (initMethod != null) {
            initMethod.invoke(target);
        }
    }

    @Override
    public void destroy() throws Exception {
        if (destroyMethod != null) {
            destroyMethod.invoke(target);
        }
    }

    @Override
    public String toString() {
        return super.toString() + "[" + target.getClass() + "#" + method.getName() + "]";
    }
}

package com.xxl.job.core.thread;

import com.xxl.job.common.enums.RegistryConstants;
import com.xxl.job.common.model.RegistryParam;
import com.xxl.job.common.model.ReturnT;
import com.xxl.job.core.executor.AdminManagerClientWrapper;
import com.xxl.job.core.executor.config.XxlJobConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by xuxueli on 17/3/2.
 */
public class ExecutorRegistryThread implements DisposableBean {
    private static Logger logger = LoggerFactory.getLogger(ExecutorRegistryThread.class);

    private final AdminManagerClientWrapper adminManagerClientWrapper;

    private final XxlJobConfiguration configuration;

    private final ScheduledThreadPoolExecutor executorRegistryThreadPool;

    public ExecutorRegistryThread(AdminManagerClientWrapper adminManagerClientWrapper, XxlJobConfiguration configuration) {
        this.adminManagerClientWrapper = adminManagerClientWrapper;
        this.configuration = configuration;
        this.executorRegistryThreadPool = new ScheduledThreadPoolExecutor(1, r -> new Thread(r, "xxl-job, executor ExecutorRegistryThread"));
    }

    /**
     * start registry job info.
     * <p>
     * 这里删除原有的定时注册的原因，个人认为：
     * 1.xxl-job虽然没有注册中心的概念，但是admin其实在理论上来说，也属于一个中心化的结构；
     * 2.所有的executor都会向admin注册自己的服务，所以admin上存储了所有的executor执行器节点；
     * 3.那么admin是最方便的对所有的executor进行探活的，客户端只需要在第一次启动时注册，后续的探活都应该由admin来做；
     * 4.另外一个客户端定时向admin注册的缺点就是，客户端请求的是admin的端口，只能保证当前客户端和admin是互通的；
     * 5.但是Netty启动的是9999端口，这个时候不能保证admin和当前的9999端口互通，或者这个时候客户端的http服务器已经假死，这种情况下admin无法感知；
     * 6.再比如如果有一些executor是在控制台上手动填写；
     * 7.所以个人认为最好的方式，是在admin端做探活，删除僵尸节点，保证负载均衡时的健康节点；
     *
     * </p>
     */
    public void startRegistry() {
        this.executorRegistryThreadPool.scheduleAtFixedRate(() -> {

        }, 0, RegistryConstants.BEAT_TIMEOUT, TimeUnit.SECONDS);
        RegistryParam registryParam = new RegistryParam(RegistryConstants.RegistryType.EXECUTOR.name(), configuration.getAppName(), configuration.getExecutorAddress());
        try {
            ReturnT<String> registryResult = adminManagerClientWrapper.getAdminManager().registry(registryParam);
            if (registryResult != null && ReturnT.SUCCESS_CODE == registryResult.getCode()) {
                logger.debug(">>>>>>>>>>> xxl-job registry success, registryParam:{}, registryResult:{}", new Object[]{registryParam, ReturnT.SUCCESS});
            } else {
                logger.info(">>>>>>>>>>> xxl-job registry fail, registryParam:{}, registryResult:{}", new Object[]{registryParam, registryResult});
            }
        } catch (Exception e) {
            logger.error(">>>>>>>>>>> xxl-job registry error, registryParam:{}", registryParam, e);
        }
    }

    @Override
    public void destroy() throws Exception {
        // stop thread pool
//        executorRegistryThreadPool.shutdownNow();
//        try {
//            if (executorRegistryThreadPool.awaitTermination(5, TimeUnit.SECONDS)) {
//                logger.info(">>>>>>>>>>> xxl-job executorRegistryThreadPool shutdown success.");
//            }
//        } catch (InterruptedException exception) {
//            logger.error(">>>>>>>>>>> xxl-job executorRegistryThreadPool shutdown error.", exception);
//        }
        RegistryParam registryParam = new RegistryParam(RegistryConstants.RegistryType.EXECUTOR.name(), configuration.getAppName(), configuration.getExecutorAddress());
        try {
            ReturnT<String> registryResult = adminManagerClientWrapper.getAdminManager().registryRemove(registryParam);
            if (registryResult != null && ReturnT.SUCCESS_CODE == registryResult.getCode()) {
                logger.info(">>>>>>>>>>> xxl-job registry-remove success, registryParam:{}, registryResult:{}", new Object[]{registryParam, ReturnT.SUCCESS});
            } else {
                logger.info(">>>>>>>>>>> xxl-job registry-remove fail, registryParam:{}, registryResult:{}", new Object[]{registryParam, registryResult});
            }
        } catch (Exception e) {
            logger.error(">>>>>>>>>>> xxl-job registry-remove error, registryParam:{}", registryParam, e);
        }
    }
}

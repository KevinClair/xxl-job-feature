package com.xxl.job.core.executor;

import com.xxl.job.common.model.RegistryParam;
import com.xxl.job.common.service.AdminManager;
import com.xxl.job.core.biz.client.AdminManagerClient;
import com.xxl.job.core.executor.config.XxlJobConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin客户端管理
 * <p>
 * 客户端(应用端)如果遇到Admin集群部署时，{@link XxlJobConfiguration}中的属性address会写多个，但经过分析认为只需要填写集群中的一个地址即可，或者填写https地址
 * <p>
 * 1.{@link AdminManager}中的操作对于Admin来说，底层逻辑都是基于数据库操作；
 * 2.初步判断作者可能认为如果其中一个地址处理失败，还可以通过其他地址完成操作，但是Admin端对于这样的操作都是异步操作，例如{@link AdminManager#registry(RegistryParam)};
 * 3.在第二个条件存在下，对于某一个操作，在配置多个地址的前提下，请求{@link AdminManager#registry(RegistryParam)}
 * <pre>
 *     {@code for (AdminManager adminManager : adminManagerClientWrapper.getAdminBizList()) {
 *             ReturnT<String> registryResult = adminManager.registry(registryParam);
 *             // 因为Admin是异步操作，这里会一直为true，也就失去了填写多个地址的意义
 *             if (registryResult != null && ReturnT.SUCCESS_CODE == registryResult.getCode()) {
 *                 logger.debug(">>>>>>>>>>> xxl-job registry success, registryParam:{}, registryResult:{}", new Object[]{registryParam, ReturnT.SUCCESS});
 *                 break;
 *             } else {
 *                 logger.info(">>>>>>>>>>> xxl-job registry fail, registryParam:{}, registryResult:{}", new Object[]{registryParam, registryResult});
 *             }
 *         }}
 * </pre>
 * 4.这里改为如果Admin集群部署时，地址填写多个，只使用其中一个。
 * 5.为了方便后续修改，考虑在此类中包装一次操作，如果后续还需要修改为填写多个地址时，可以回退
 */
public class AdminManagerClientWrapper {

    private final List<AdminManager> adminManagerList = new ArrayList<AdminManager>();

    private final AdminManager adminManager;

    public AdminManagerClientWrapper(XxlJobConfiguration configuration) {
        String address = configuration.getAddress().trim().split(",")[0];
        this.adminManager = new AdminManagerClient(address.trim(), configuration.getAccessToken());
    }

    public List<AdminManager> getAdminManagerList() {
        return adminManagerList;
    }

    public AdminManager getAdminManager() {
        return adminManager;
    }
}

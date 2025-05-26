package com.xxl.job.starter;

import com.xxl.job.common.service.ExecutorManager;
import com.xxl.job.common.utils.IpUtil;
import com.xxl.job.core.biz.impl.ExecutorManagerImpl;
import com.xxl.job.core.executor.AdminManagerClientWrapper;
import com.xxl.job.core.executor.config.XxlJobConfiguration;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import com.xxl.job.core.handler.JobHandlerRepository;
import com.xxl.job.core.server.EmbedServer;
import com.xxl.job.core.thread.ExecutorRegistryThread;
import com.xxl.job.core.thread.TriggerCallbackThread;
import com.xxl.job.core.util.NetUtil;
import com.xxl.job.starter.config.XxlJobAdminConfiguration;
import com.xxl.job.starter.config.XxlJobExecutorConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;

import java.util.Properties;

/**
 * xxl-job属性自动装配
 */
@Configuration
@EnableConfigurationProperties({XxlJobAdminConfiguration.class, XxlJobExecutorConfiguration.class})
@ConditionalOnProperty(
        prefix = "xxl-job.admin",
        name = {"address", "access-token"})
public class XxlJobAutoConfiguration implements EnvironmentAware, InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(XxlJobAutoConfiguration.class);

    public static final String LINE_SEPARATOR = System.getProperty("line.separator");

    private static final String PATH = "/META-INF/maven/com.xuxueli/xxl-job-spring-boot-starter/pom.properties";

    private static final String BANNAR = "\n" +
            "██╗  ██╗██╗  ██╗██╗               ██╗ ██████╗ ██████╗ \n" +
            "╚██╗██╔╝╚██╗██╔╝██║               ██║██╔═══██╗██╔══██╗\n" +
            " ╚███╔╝  ╚███╔╝ ██║    █████╗     ██║██║   ██║██████╔╝\n" +
            " ██╔██╗  ██╔██╗ ██║    ╚════╝██   ██║██║   ██║██╔══██╗\n" +
            "██╔╝ ██╗██╔╝ ██╗███████╗     ╚█████╔╝╚██████╔╝██████╔╝\n" +
            "╚═╝  ╚═╝╚═╝  ╚═╝╚══════╝      ╚════╝  ╚═════╝ ╚═════╝ \n";

    private ConfigurableEnvironment environment;

    @Override
    public void afterPropertiesSet() throws Exception {
        String version = null;
        try {
            Properties properties = new Properties();
            properties.load(XxlJobAutoConfiguration.class.getResourceAsStream(PATH));
            version = properties.getProperty("version");
        } catch (Exception e) {
        }
        StringBuilder bannerTextBuilder = new StringBuilder();
        bannerTextBuilder.append(LINE_SEPARATOR).append(BANNAR).append(" :: xxl-job ::         (v").append(version).append(")").append(LINE_SEPARATOR);
        logger.info(bannerTextBuilder.toString());
    }

    /**
     * 合并生成{@link XxlJobConfiguration}
     *
     * @param adminConfiguration    {@link XxlJobAdminConfiguration}
     * @param executorConfiguration {@link XxlJobExecutorConfiguration}
     * @return {@link XxlJobConfiguration}
     */
    @Bean
    public XxlJobConfiguration xxlJobConfiguration(@NonNull XxlJobAdminConfiguration adminConfiguration, @NonNull XxlJobExecutorConfiguration executorConfiguration) {
        XxlJobConfiguration configuration = new XxlJobConfiguration();
        configuration.setAddress(adminConfiguration.getAddress());
        configuration.setAccessToken(adminConfiguration.getAccessToken());
        if (!StringUtils.hasLength(executorConfiguration.getAppName())) {
            String applicationName = environment.getProperty("spring.application.name");
            if (!StringUtils.hasLength(applicationName)) {
                throw new IllegalArgumentException("The name of executor is empty.");
            }
            configuration.setAppName(applicationName);
        } else {
            configuration.setAppName(executorConfiguration.getAppName());
        }

        if (!StringUtils.hasLength(executorConfiguration.getIp())) {
            configuration.setIp(IpUtil.getIp());
        } else {
            configuration.setIp(executorConfiguration.getIp());
        }
        configuration.setPort(executorConfiguration.getPort() > 0 ? executorConfiguration.getPort() : NetUtil.findAvailablePort(9999));
        configuration.setExecutorAddress("http://" + configuration.getIp() + ":" + configuration.getPort());
        configuration.setLogPath(executorConfiguration.getLogPath());
        configuration.setLogRetentionDays(executorConfiguration.getLogRetentionDays());
        return configuration;
    }

    /**
     * 启动注册JobHandler，扫描{@link com.xxl.job.core.handler.annotation.XxlJob}注解，请求Admin接口注册job任务
     *
     * @param adminManagerClientWrapper {@link AdminManagerClientWrapper}
     * @param configuration             {@link XxlJobConfiguration}
     * @return {@link JobHandlerRepository}
     */
    @Bean
    public JobHandlerRepository jobHandlerRepository(AdminManagerClientWrapper adminManagerClientWrapper, XxlJobConfiguration configuration) {
        return new JobHandlerRepository(adminManagerClientWrapper, configuration);
    }

    /**
     * 应用端的执行器管理，会根据admin请求core的不同路径调用不同的实现方法
     *
     * @param jobHandlerRepository {@link JobHandlerRepository}
     * @return {@link ExecutorManager}
     */
    @Bean
    public ExecutorManager executorBiz(JobHandlerRepository jobHandlerRepository) {
        return new ExecutorManagerImpl(jobHandlerRepository);
    }

    /**
     * admin客户端管理
     *
     * @param configuration {@link XxlJobConfiguration}
     * @return {@link AdminManagerClientWrapper}
     */
    @Bean
    public AdminManagerClientWrapper adminManagerClientWrapper(XxlJobConfiguration configuration) {
        return new AdminManagerClientWrapper(configuration);
    }

    /**
     * 调度任务执行结束后的回调，调度结果回调Admin
     *
     * @param adminManagerClientWrapper {@link AdminManagerClientWrapper}
     * @return {@link TriggerCallbackThread}
     */
    @Bean
    public TriggerCallbackThread triggerCallbackThread(AdminManagerClientWrapper adminManagerClientWrapper) {
        return new TriggerCallbackThread(adminManagerClientWrapper);
    }

    /**
     * 初始化一些东西，例如日志路径
     *
     * @param configuration {@link XxlJobConfiguration}
     * @return {@link XxlJobSpringExecutor}
     */
    @Bean
    public XxlJobSpringExecutor xxlJobSpringExecutor(XxlJobConfiguration configuration) {
        return new XxlJobSpringExecutor(configuration);
    }

    /**
     * 执行器注册，只负责注册执行器，不负责注册job任务
     *
     * @param adminManagerClientWrapper 客户端管理{@link AdminManagerClientWrapper}
     * @param configuration             {@link XxlJobConfiguration}
     * @return {@link ExecutorRegistryThread}
     */
    @Bean
    public ExecutorRegistryThread executorRegistryThread(AdminManagerClientWrapper adminManagerClientWrapper, XxlJobConfiguration configuration) {
        return new ExecutorRegistryThread(adminManagerClientWrapper, configuration);
    }

    /**
     * Netty实现的本地http服务器，供admin请求
     *
     * @param executorManager        {@link ExecutorManager}
     * @param configuration          {@link XxlJobConfiguration}
     * @param executorRegistryThread {@link ExecutorRegistryThread}
     * @return {@link EmbedServer}
     */
    @Bean
    public EmbedServer embedServer(ExecutorManager executorManager, XxlJobConfiguration configuration, ExecutorRegistryThread executorRegistryThread) {
        return new EmbedServer(executorManager, configuration, executorRegistryThread);
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = (ConfigurableEnvironment) environment;
    }
}
